package com.code42.user;

import java.util.List;
import java.util.Map;

import com.backup42.CpcConstants;
import com.backup42.EmailPaths;
import com.backup42.account.AccountServices;
import com.backup42.history.CpcHistoryLogger;
import com.backup42.server.CpcEmailContext;
import com.backup42.server.MasterServices;
import com.code42.auth.IPermission;
import com.code42.auth.PasswordResetTokenCreateCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.content.IResource;
import com.code42.core.content.impl.ContentProviderServices;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.code42.email.Emailer;
import com.code42.utils.SystemProperties;
import com.code42.utils.SystemProperty;
import com.code42.validation.rules.EmailRule;

/**
 * Sends an email to the address provided allowing that user to reset their password.
 */
public class UserResetPasswordSendEmailCmd extends DBCmd<Boolean> {

	// NEW
	public static final String PASSWORD_RESET = "PasswordReset";

	public enum Error {
		USER_NOT_FOUND, NON_UNIQUE_EMAIL_OR_USERNAME, EMAIL_OR_USERNAME_INVALID, EXTERNAL_AUTH_USER, MISSING_EMAIL
	}

	private final String emailOrUsername;
	private final Integer userId;
	private String viewUrl;

	// transient
	private User user;

	/**
	 * @param emailOrUsername - an email address or a username.
	 */
	public UserResetPasswordSendEmailCmd(String emailOrUsername) {
		this(null, emailOrUsername, "/console/password-reset.html");
	}

	/**
	 * @param emailOrUsername - and email address or a username.
	 */
	public UserResetPasswordSendEmailCmd(String emailOrUsername, String viewUrl) {
		this(null, emailOrUsername, viewUrl);
	}

	public UserResetPasswordSendEmailCmd(int userId) {
		this(userId, null, "/console/password-reset.html");
	}

	public UserResetPasswordSendEmailCmd(int userId, String viewUrl) {
		this(userId, null, viewUrl);
	}

	public UserResetPasswordSendEmailCmd(Integer userId, String emailOrUsername, String viewUrl) {
		this.userId = userId;
		this.viewUrl = viewUrl;
		this.emailOrUsername = emailOrUsername;
	}

	@Override
	public Boolean exec(CoreSession session) throws CommandException {
		if (this.emailOrUsername == null && this.userId == null) {
			throw new CommandException(Error.EMAIL_OR_USERNAME_INVALID, "Email/username or userId is required.");
		}

		if (this.userId != null) {
			this.user = this.db.find(new UserFindByIdQuery(this.userId));
		} else {
			List<User> users = this.db.find(new UserFindByUsernameQuery(this.emailOrUsername));
			if (users.isEmpty() && EmailRule.isValidEmail(this.emailOrUsername)) {
				users = this.db.find(new UserFindByEmailQuery(this.emailOrUsername).doNotCheckResultSize());
			}
			if (users.size() == 0) {
				throw new CommandException(Error.USER_NOT_FOUND, "User not found; email=" + this.emailOrUsername);
			}
			if (users.size() > 1) {
				throw new CommandException(Error.NON_UNIQUE_EMAIL_OR_USERNAME, "Multiple users found; email="
						+ this.emailOrUsername);
			}
			this.user = users.get(0);
		}

		if (this.user == null || this.user.isPlaceholder()) {
			throw new CommandException(Error.USER_NOT_FOUND, "User not found; email=" + this.emailOrUsername);
		}

		if (!MasterServices.getInstance().isMasterUser(this.user)) {
			throw new UnauthorizedException("Cannot edit users from hosted orgs; userId=" + this.user.getUserId());
		}

		if (AccountServices.getInstance().isOrgSsoAuth(this.user.getOrgId())) {
			throw new CommandException(Error.EXTERNAL_AUTH_USER,
					"Cannot reset passwords for SSO authenticated users; userId=" + this.user.getUserId());
		}

		if (AccountServices.getInstance().isOrgRadius(this.user.getOrgId())) {
			throw new CommandException(Error.EXTERNAL_AUTH_USER,
					"Cannot reset passwords for RADIUS authenticated users; userId=" + this.user.getUserId());
		}

		if (AccountServices.getInstance().isOrgLdap(this.user.getOrgId())) {
			throw new CommandException(Error.EXTERNAL_AUTH_USER,
					"Cannot reset passwords for LDAP authenticated users; userId=" + this.user.getUserId());
		}

		// Determine recipient email address.
		String recipient = this.user.getEmail();
		if (!EmailRule.isValidEmail(recipient)) {
			if (this.user.getUserId() == CpcConstants.Users.ADMIN_ID) {
				// it is the admin so send recovery password to the error email recipient
				recipient = SystemProperties.getRequired(SystemProperty.ERROR_EMAIL_RECIPIENT);
			} else {
				throw new CommandException(Error.MISSING_EMAIL, "Unable to reset password, user doesn't have an email. userId="
						+ this.user.getUserId());
			}
		}

		boolean result = false;

		try {
			// Send Email
			this.db.openSession();

			if (this.env.isCrashPlanOrg(this.user.getOrgId())) {
				// This is only temporary until we can get the consumer site using the same reset mechanism
				if (this.viewUrl == null) {
					this.viewUrl = "/account/view_password.vtl";
				}
				AccountServices.getInstance().requestPasswordRecoveryEmail(this.user, this.viewUrl);

			} else {
				if (this.viewUrl == null) {
					this.viewUrl = "/console/password-reset.html";
				}
				IPermission appLoginPermission = this.serverService.getRequiredLoginPermission(this.user.getUserId());
				PasswordResetTokenCreateCmd cmd = new PasswordResetTokenCreateCmd(this.user.getUserId(), appLoginPermission);

				// Needs to be run as Admin because the provided session probably has no user
				// (this is OK because all we're doing is sending an email)
				String encryptedToken = this.runtime.run(cmd, this.auth.getAdminSession());

				Map<String, Object> context = new CpcEmailContext(this.user.getOrgId());
				context.put("encryptedParam", encryptedToken);
				context.put("email", recipient);
				context.put("viewUrl", this.viewUrl);

				ContentProviderServices cProvider = CoreBridge.getContentService().getServiceInstance();
				IResource resource = cProvider.getResourceByName(EmailPaths.EMAIL_RESET_PASSWORD);
				Emailer.enqueueEmail(resource, this.user.getEmail(), context);
			}

			CpcHistoryLogger.info(session, "Password reset email sent {}/{}", this.user.getUserId(), recipient);
			result = true;
		} catch (CommandException e) {
			throw e;
		} catch (Throwable t) {
			throw new CommandException("Unable to send email invitations", t);
		} finally {
			this.db.closeSession();
		}

		return result;
	}
}
