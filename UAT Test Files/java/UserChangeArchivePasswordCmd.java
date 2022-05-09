package com.code42.user;

import java.util.List;

import com.backup42.account.AccountServices;
import com.backup42.common.perm.C42PermissionPro;
import com.backup42.common.util.CPRule;
import com.backup42.computer.EncryptionKeyServices;
import com.backup42.history.CpcHistoryLogger;
import com.code42.auth.AuthenticatorFindByOrgCmd;
import com.code42.backup.DataKey;
import com.code42.backup.SecureDataKey;
import com.code42.computer.DataEncryptionKey;
import com.code42.core.CommandException;
import com.code42.core.auth.Authenticator;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.core.security.ICryptoService;
import com.code42.core.security.ILoginMonitoringService;
import com.code42.utils.LangUtils;
import com.google.inject.Inject;

/**
 * There are two modes.
 * <p>
 * 1. User updating their own private data password. Requires current password.
 * <p>
 * 2. System admin updating any users archive password. Requires LDAP or RADIUS and account password security.
 */
public class UserChangeArchivePasswordCmd extends DBCmd<Void> {

	public enum Error {
		USER_NOT_FOUND, KEY_NOT_FOUND, INCORRECT_TYPE, INCORRECT_CURRENT_PASSWORD, TOO_MANY_ATTEMPTS, INVALID_AUTHENTICATOR
	}

	@Inject
	private ILoginMonitoringService loginMonitor;

	@Inject
	private ICryptoService crypto;

	private final int userId;
	private final String currentPassword;
	private final String newPassword;

	public UserChangeArchivePasswordCmd(int userId, String currentPassword, String newPassword) {
		super();
		this.userId = userId;
		this.currentPassword = currentPassword;
		this.newPassword = newPassword;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		this.db.beginTransaction();
		try {
			this.ensureMaster();

			// If userId does not exist, or permission check fails, this check will throw an exception
			final IUser user = this.runtime.run(new IsUserManageableCmd(this.userId, C42PermissionApp.User.UPDATE), session);
			if (user == null) {
				throw new CommandException(Error.USER_NOT_FOUND, "Unable to change archive password, user {} not found.",
						this.userId);
			}

			this.ensureNotHostedOrg(user.getOrgId(), session);

			if (!LangUtils.hasValue(this.newPassword) || this.newPassword.length() < CPRule.MIN_ARCHIVE_PASSWORD_LENGTH) {
				CpcHistoryLogger.info(session,
						"Unable to change archive password, new password must be atleast {} characters long.",
						CPRule.MIN_ARCHIVE_PASSWORD_LENGTH);
				throw new CommandException(Error.INCORRECT_TYPE,
						"Unable to change archive password, new password must be atleast {} characters long.",
						CPRule.MIN_ARCHIVE_PASSWORD_LENGTH);
			}

			// Get the encryption key from DB
			final DataEncryptionKey dbKey = EncryptionKeyServices.getInstance().findKeyForUser(this.userId);
			if (dbKey == null) {
				CpcHistoryLogger.info(session, "Unable to change archive password, no encryption key found for user {}. "
						+ "They likely don't have any devices yet.", this.userId);
				throw new CommandException(Error.KEY_NOT_FOUND,
						"Unable to change archive password, no encryption key found for user {}.", this.userId);
			}

			final boolean adminMode = !LangUtils.hasValue(this.currentPassword);
			if (adminMode) {
				this.updateArchiveAccountPassword(user, dbKey, session);
			} else {
				this.updateArchivePrivatePassword(user, dbKey, session);
			}

			this.db.commit();
			return null;
		} catch (CommandException ce) {
			this.db.rollback();
			throw ce;
		} catch (Throwable t) {
			this.db.rollback();
			throw new CommandException("Unexpected Exception: " + t.getMessage(), t);
		} finally {
			this.db.endTransaction();
		}
	}

	private void updateArchivePrivatePassword(IUser user, DataEncryptionKey dbKey, CoreSession session)
			throws CommandException {
		// Check the type, must be using "archive password" method.
		if (!dbKey.isPrivatePassword()) {
			CpcHistoryLogger.info(session,
					"Unable to change archive password, user {} isn't using archive private password method. method={}",
					this.userId, dbKey.getSecurityKeyType().name());
			throw new CommandException(Error.INCORRECT_TYPE,
					"Unable to change archive password, user {} isn't using archive private password method. method={}",
					this.userId, dbKey.getSecurityKeyType().name());
		}
		if (!LangUtils.hasValue(this.currentPassword)) {
			CpcHistoryLogger.info(session, "Unable to change archive password, no current password specified.");
			throw new CommandException(Error.INCORRECT_CURRENT_PASSWORD,
					"Unable to change archive password, no current password specified.");
		}

		final SecureDataKey secureDataKey = new SecureDataKey(dbKey.getSecureDataKey().getBytes());

		// Get private key from public key using passed current password
		final String loginMonitorKey = user.getUsername() + "_AK"; // So it doesn't conflict with account login.
		final DataKey dataKey = secureDataKey.getDataKey(this.currentPassword);
		if (dataKey == null) {
			// Don't let them try too many times in a row.
			if (this.loginMonitor.isDisabled(loginMonitorKey)) {
				CpcHistoryLogger.info(session, "Unable to change archive password, too many attempts.");
				throw new CommandException(Error.TOO_MANY_ATTEMPTS,
						"Unable to change archive password, too many attempts for user {}.", this.userId);
			} else {
				this.loginMonitor.loginFailed(loginMonitorKey);
				CpcHistoryLogger.info(session, "Unable to change archive password, incorrect current password.");
				throw new CommandException(Error.INCORRECT_CURRENT_PASSWORD,
						"Unable to change archive password, incorrect current password.");
			}
		}

		this.loginMonitor.loginSucceeded(loginMonitorKey);

		// Build new public key
		final SecureDataKey newSecureDataKey = SecureDataKey.create(dataKey, this.newPassword);
		EncryptionKeyServices.getInstance().storeKey(this.userId, null, newSecureDataKey);

		// Change data password
		CpcHistoryLogger.info(session, "Change archive private password for {}", user);
	}

	private void updateArchiveAccountPassword(IUser user, DataEncryptionKey dbKey, CoreSession session)
			throws CommandException {
		// Requires system admin privs
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		// Check the type, must be using "archive password" method.
		if (!dbKey.isAccountPassword()) {
			CpcHistoryLogger.info(session,
					"Unable to change archive password, user {} isn't using archive account password method. method={}",
					this.userId, dbKey.getSecurityKeyType().name());
			throw new CommandException(Error.INCORRECT_TYPE,
					"Unable to change archive password, user {} isn't using archive account password method. method={}",
					this.userId, dbKey.getSecurityKeyType().name());
		}

		// Must be remote (LDAP) authenticator.
		final List<Authenticator> directories = this.runtime.run(new AuthenticatorFindByOrgCmd(user.getOrgId()), session);
		final boolean allLocal = AccountServices.getInstance().isAuthLocal(directories);
		if (allLocal) {
			CpcHistoryLogger
					.info(session, "Unable to change archive password, must be using remote authenticator like LDAP.");
			throw new CommandException(Error.INVALID_AUTHENTICATOR,
					"Unable to change archive password, must be using remote authenticator like LDAP.");
		}

		final DataKey dataKey = this.crypto.decryptDataKey(dbKey.getDataKey());

		// Build new public key
		final SecureDataKey newSecureDataKey = SecureDataKey.create(dataKey, this.newPassword);
		EncryptionKeyServices.getInstance().storeKey(this.userId, dataKey, newSecureDataKey);

		// Change data password
		CpcHistoryLogger.info(session, "Change archive account password for {}", user);
	}
}
