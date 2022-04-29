package com.code42.user;

import com.backup42.CpcConstants;
import com.backup42.common.AuthorizeRules;
import com.backup42.common.util.CPValidation;
import com.backup42.computer.EncryptionKeyServices;
import com.backup42.history.CpcHistoryLogger;
import com.code42.account.AuthorizeRulesFindByOrgIdCmd;
import com.code42.backup.DataKey;
import com.code42.backup.SecureDataKey;
import com.code42.computer.DataEncryptionKey;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.alert.ISystemAlertService;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.core.security.ICryptoService;
import com.code42.crypto.StringHasher;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.utils.LangUtils;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;
import com.google.inject.Inject;

/**
 * Provides a command for updating the users password using either hashed or plain-text passwords. No password can be
 * updated without validating that the user knows the old password. TODO: Turn this into an abstract superclass to
 * handle all the different ways of changing the password
 */
public class UserPasswordUpdateCmd extends DBCmd<User> {

	private static Logger log = LoggerFactory.getLogger(UserPasswordUpdateCmd.class);

	@Inject
	private ISystemAlertService systemAlertService;

	@Inject
	private ICryptoService crypto;

	private final Builder data;

	private User user;

	public enum Error {
		OLD_PASSWORD_INVALID, // Old password does not match the current one
		NEW_PASSWORD_INVALID, // Doesn't meet the auth rule requirements
	}

	UserPasswordUpdateCmd(Builder data) {
		this.data = data;
	}

	@Override
	public User exec(CoreSession session) throws CommandException {

		this.runtime.run(new IsUserManageableCmd(this.data.userId, C42PermissionApp.User.UPDATE), session);

		this.db.beginTransaction();

		try {

			this.user = this.db.find(new UserFindByIdQuery(this.data.userId));

			final AuthorizeRules rules = this.run(new AuthorizeRulesFindByOrgIdCmd(this.user.getOrgId()), session);
			if (!LangUtils.hasValue(this.data.newPassword) || !CPValidation.isValidPassword(this.data.newPassword, rules)) {
				throw new CommandException(Error.NEW_PASSWORD_INVALID, "Invalid new password provided.");
			}

			int userId = this.user.getUserId();
			int sUserId = session.getUser().getUserId();
			// If the password update is on yourself or oldPassword is passed then ensure that it's correct
			if ((userId == sUserId) || (this.data.oldPassword instanceof Some<?>)) {
				if (this.data.oldPassword instanceof None) {
					throw new CommandException(Error.OLD_PASSWORD_INVALID, "Old password required to change your own password");
				}
				if (!StringHasher.checkPassword(this.data.oldPassword.get(), this.user.getPassword())) {
					throw new CommandException(Error.OLD_PASSWORD_INVALID, "The old password is not valid. Cannot change.");
				}
			}

			// Only attempt to set the password if it is different than the old one
			if (StringHasher.checkPassword(this.data.newPassword, this.user.getPassword())) {
				return this.user;
			}

			// Set the password whether it is already hashed or is in plain text
			if (StringHasher.C42.isValidHash(this.data.newPassword)) {
				this.user.setPassword(this.data.newPassword);
			} else {
				String hashedPassword = StringHasher.C42.hash(this.data.newPassword);
				this.user.setPassword(hashedPassword);
			}

			this.db.update(new UserUpdateQuery(this.user));

			// Generate backup data keys if changing password
			final DataEncryptionKey dbKey = EncryptionKeyServices.getInstance().findKeyForUser(this.data.userId);
			if ((dbKey != null) && (dbKey.getDataKey() != null)) {
				final DataKey dataKey = this.crypto.decryptDataKey(dbKey.getDataKey());
				final SecureDataKey secureDataKey = SecureDataKey.create(dataKey, this.data.newPassword);
				EncryptionKeyServices.getInstance().storeKey(this.data.userId, dataKey, secureDataKey);
			}

			this.db.afterTransaction(new UserPublishUpdateCmd(this.user), session);

			this.db.commit();

		} catch (CommandException e) {
			this.db.rollback();
			throw e;
		} catch (Throwable e) {
			this.db.rollback();
			log.error("Unexpected: ", e);
		} finally {
			this.db.endTransaction();
		}

		CpcHistoryLogger.info(session, "Changed account password: {}/{}", this.user.getUserId(), this.user.getUsername());

		// Last second check for the ADMIN user
		if (this.user.getUserId() == CpcConstants.Users.ADMIN_ID) {
			// Check to see if the password is still the default
			boolean defaultAdminPw = StringHasher.checkPassword(CpcConstants.Users.INITIAL_ADMIN_PASS, this.user
					.getPassword());
			if (defaultAdminPw) {
				this.systemAlertService.triggerAdminPasswordUnchangedAlert();
			} else {
				this.systemAlertService.clearAdminPasswordUnchangedAlert();
			}
		}

		return this.user;
	}

	public static class Builder {

		int userId;
		String newPassword;
		Option<String> oldPassword = None.getInstance();

		public Builder(int userId, String newPassword) {
			this.userId = userId;
			this.newPassword = newPassword;
		}

		public Builder oldPassword(String oldPassword) {
			this.oldPassword = new Some<String>(oldPassword);
			return this;
		}

		public UserPasswordUpdateCmd build() throws BuilderException {
			this.validate();
			return new UserPasswordUpdateCmd(this);
		}

		public void validate() throws BuilderException {
			if (this.newPassword == null) {
				throw new BuilderException(Error.NEW_PASSWORD_INVALID, "New password cannot be null");
			}
		}
	}

}