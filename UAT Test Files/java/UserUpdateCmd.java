/*
 * <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.user;

import java.util.Date;
import java.util.List;

import com.backup42.CpcConstants;
import com.backup42.common.perm.C42PermissionPro;
import com.backup42.history.CpcHistoryLogger;
import com.code42.auth.IPermission;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.alert.ISystemAlertService;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.db.NotFoundException;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.OrgSettingsInfo;
import com.code42.org.OrgSettingsInfoFindByOrgCmd;
import com.code42.perm.PermissionUtils;
import com.code42.utils.LangUtils;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;
import com.code42.validation.rules.EmailRule;
import com.google.inject.Inject;

/**
 * Validate and update all the fields on the given user, ensuring that the usernameId field gets populated, if it isn't
 * already.<br>
 * <br>
 * As of Bugzilla 1587 this command cannot block, unblock, activate or deactivate a user.
 */
public class UserUpdateCmd extends DBCmd<User> {

	private static final Logger log = LoggerFactory.getLogger(UserUpdateCmd.class);

	@Inject
	private ISystemAlertService sysAlerts;

	private Builder data = null;

	private BackupUser user;

	enum Error {
		USERNAME_NOT_AN_EMAIL, EMAIL_INVALID, USER_NOT_FOUND, INVALID_USERNAME
	}

	/**
	 * Use the Builder static inner class to construct one of these.
	 */
	private UserUpdateCmd(Builder b) {
		this.data = b;
	}

	@Override
	public User exec(CoreSession session) throws CommandException {

		this.runtime.run(new IsUserManageableCmd(this.data.userId, C42PermissionApp.User.UPDATE), session);

		this.db.beginTransaction();

		try {

			// Cannot use the above user because it may be a cached copy.
			this.user = (BackupUser) this.runtime.run(new UserFindByIdCmd(this.data.userId), session);
			this.ensureNotHostedOrg(this.user.getOrgId(), this.auth.getAdminSession());

			if (this.user == null || this.user.getUserId() == null) {
				throw new NotFoundException(Error.USER_NOT_FOUND, "Unable to update user; key value is null or invalid: "
						+ this.data.userId);
			}

			if (this.data.username instanceof Some<?>) {
				if (!LangUtils.equals(this.data.username.get(), this.user.getUsername())) {
					// If necessary, make sure it is an email address

					// Elevate permissions here so we can check the org setting
					OrgSettingsInfo osi = this.run(new OrgSettingsInfoFindByOrgCmd.Builder().orgId(this.user.getOrgId()).build(),
							this.auth.getAdminSession());
					if (osi.getUsernameIsAnEmail()) {
						if (!EmailRule.isValidEmail(this.data.username.get())) {
							throw new CommandException("Username must be an email address: {} for users in orgId: {}",
									this.data.username.get(), this.user.getOrgId());
						}
					}

					CpcHistoryLogger.info(session, "Changing account username: {}/{}  new: {}", this.user.getUserId(), this.user
							.getUsername(), this.data.username.get());
				}
				this.user.setUsername(this.data.username.get());
			}

			if (this.data.email instanceof Some<?>) {
				if (!LangUtils.equals(this.data.email.get(), this.user.getEmail())) {
					CpcHistoryLogger.info(session, "Changing account email: {}/{}  old/new: {}/{}", this.user.getUserId(),
							this.user.getUsername(), this.user.getEmail(), this.data.email.get());
				}
				this.user.setEmail(this.data.email.get());
			}

			if (this.data.firstName instanceof Some<?>) {
				this.user.setFirstName(this.data.firstName.get());
			}

			if (this.data.lastName instanceof Some<?>) {
				this.user.setLastName(this.data.lastName.get());
			}

			if (this.data.maxBytes instanceof Some<?>) {
				this.user.setMaxBytes(this.data.maxBytes.get());
			}

			if (this.data.pwResetRequired instanceof Some<?>) {
				this.user.setPasswordResetRequired(this.data.pwResetRequired.get());
			}

			if ((this.data.emailPromo instanceof Some<?>) && (this.data.emailPromoChangeReason instanceof Some<?>)) {
				this.user.setEmailPromo(this.data.emailPromo.get(), this.data.emailPromoChangeReason.get());
			}

			if (this.data.personalPlanId instanceof Some<?>) {
				this.user.setPersonalPlanId(this.data.personalPlanId.get());
				this.user.addUserPlan(this.data.personalPlanId.get());
			}

			if (this.data.pwViewExpire instanceof Some<?>) {
				this.user.setPwViewExpire(this.data.pwViewExpire.get());
			}

			if (this.data.twitterScreenName instanceof Some<?>) {
				this.user.setTwitterScreenName(this.data.twitterScreenName.get());
			}

			if (this.data.twitterAccessToken instanceof Some<?>) {
				this.user.setTwitterAccessToken(this.data.twitterAccessToken.get());
			}

			if (this.data.twitterAccessTokenSecret instanceof Some<?>) {
				this.user.setTwitterAccessTokenSecret(this.data.twitterAccessTokenSecret.get());
			}

			if (this.data.question instanceof Some<?>) {
				this.user.setQuestion(this.data.question.get());
			}

			if (this.data.answer instanceof Some<?>) {
				this.user.setAnswer(this.data.answer.get());
			}
			/*
			 * A user should NEVER be here without a usernameId
			 */
			assert (LangUtils.hasValue(this.user.getUserUid()));

			this.user = (BackupUser) this.runtime.run(new UserValidateCmd(this.user), this.auth.getAdminSession());

			this.user = (BackupUser) this.db.update(new UserUpdateQuery(this.user));

			if (this.data.newPassword instanceof Some<?>) {
				UserPasswordUpdateCmd.Builder passwordUpdateBuilder = new UserPasswordUpdateCmd.Builder(this.data.userId,
						this.data.newPassword.get());
				if (!(this.data.oldPassword instanceof None)) {
					passwordUpdateBuilder.oldPassword(this.data.oldPassword.get());
				}
				this.run(passwordUpdateBuilder.build(), session);
			}

			// Make sure a user login history exists, should already happen but just to make sure or the 'admin' user can
			// get locked out.
			// http://bugz.c42/bugzilla/show_bug.cgi?id=7068
			this.runtime.run(new UserHistoryLoginCmd(), session);

			// If user is a system admin and they have an email address, remove the admin email alert
			{

				UserSso sso = this.run(new UserSsoFindByUserIdCmd(this.user.getUserId()), session);
				List<IPermission> permissions = this.run(new UserFindPermissionsCmd(sso), session);
				boolean isSysAdmin = PermissionUtils.isAuthorized(C42PermissionPro.System.ALL, permissions);
				if (isSysAdmin && EmailRule.isValidEmail(this.user.getEmail())) {
					this.sysAlerts.clearSystemAlertRecipientsMissingAlert();
				}
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

		CpcHistoryLogger.info(session, "modified user: {}/{}", this.user.getUserId(), this.user.getUsername());

		return this.user;
	}

	/**
	 * Builds the input data and the UserUpdate command. This takes the place of a big long constructor.
	 * 
	 * Note that within validate() below we're only doing null checks on builder methods which take a reference type and
	 * not those that take a value type (i.e. a primitive). Even though an integer will get autoboxed into an Integer
	 * within the Option type and that Integer could (in theory) be null there's no way for a null integer primitive to be
	 * passed into the actual method call... and as such there's no way the Integer in the corresponding Option type could
	 * be null in practice. Keep this in mind if you change the method signature on any of the builder methods below.
	 */
	public static class Builder {

		/* This val must always be present; it's the only way to get a builder */
		public int userId = 0;

		public Option<String> email = None.getInstance();
		public Option<String> username = None.getInstance();
		public Option<String> oldPassword = None.getInstance();
		public Option<String> newPassword = None.getInstance();
		public Option<String> firstName = None.getInstance();
		public Option<String> lastName = None.getInstance();
		public Option<Long> maxBytes = None.getInstance();
		public Option<Boolean> pwResetRequired = None.getInstance();
		public Option<Boolean> emailPromo = None.getInstance();
		public Option<String> emailPromoChangeReason = None.getInstance();
		public Option<Boolean> login = None.getInstance(); // Record a login
		public Option<Long> personalPlanId = None.getInstance();
		public Option<Date> pwViewExpire = None.getInstance();
		public Option<String> twitterScreenName = None.getInstance();
		public Option<String> twitterAccessToken = None.getInstance();
		public Option<String> twitterAccessTokenSecret = None.getInstance();
		public Option<String> question = None.getInstance();
		public Option<String> answer = None.getInstance();

		public Builder(int userId) {
			this.userId = userId;
		}

		public Builder email(String email) {
			this.email = new Some<String>(email == null ? null : email.toLowerCase());
			return this;
		}

		public Builder username(String username) {
			this.username = new Some<String>(username);
			return this;
		}

		public Builder firstName(String firstName) {
			this.firstName = new Some<String>(firstName);
			return this;
		}

		public Builder lastName(String lastName) {
			this.lastName = new Some<String>(lastName);
			return this;
		}

		public Builder maxBytes(Long maxBytes) {
			this.maxBytes = new Some<Long>(maxBytes);
			return this;
		}

		public Builder oldPassword(String oldPassword) {
			this.oldPassword = new Some<String>(oldPassword);
			return this;
		}

		public Builder login() {
			this.login = new Some<Boolean>(true);
			return this;
		}

		public Builder newPassword(String newPassword) {
			this.newPassword = new Some<String>(newPassword);
			return this;
		}

		public Builder emailPromo(boolean emailPromo, String reason) {
			this.emailPromo = new Some<Boolean>(emailPromo);
			this.emailPromoChangeReason = new Some<String>(reason);
			return this;
		}

		public Builder passwordResetRequired(boolean pwResetRequired) {
			this.pwResetRequired = new Some<Boolean>(pwResetRequired);
			return this;
		}

		public Builder personalPlanId(Long planId) {
			this.personalPlanId = new Some<Long>(planId);
			return this;
		}

		public Builder pwViewExpire(Date expireDate) {
			this.pwViewExpire = new Some<Date>(expireDate);
			return this;
		}

		public Builder twitterScreenName(String screenName) {
			this.twitterScreenName = new Some<String>(screenName);
			return this;
		}

		public Builder twitterAccessToken(String token) {
			this.twitterAccessToken = new Some<String>(token);
			return this;
		}

		public Builder twitterAcessTokenSecret(String secret) {
			this.twitterAccessTokenSecret = new Some<String>(secret);
			return this;
		}

		public Builder question(String question) {
			this.question = new Some<String>(question);
			return this;
		}

		public Builder answer(String answer) {
			this.answer = new Some<String>(answer);
			return this;
		}

		public void validate() throws BuilderException {

			if (this.username instanceof Some<?> && !LangUtils.hasValue(this.username)) {
				throw new BuilderException(Error.INVALID_USERNAME, "Username cannot be null or empty");
			}

			// Allow users to clear the email field - only validate it if it contains information.
			// Further validation occurs above in UserValidateCmd
			if (this.email instanceof Some<?> && LangUtils.hasValue(this.email.get())
					&& !EmailRule.isValidEmail(this.email.get())) {
				throw new BuilderException(Error.EMAIL_INVALID, "Invalid email address");
			}

			if (this.newPassword instanceof Some<?> && this.newPassword.get() == null) {
				throw new BuilderException("New password cannot be null");
			}

			if (this.maxBytes instanceof Some<?>) {

				/*
				 * A zero is invalid; if we receive one, interpret it as unlimited.
				 */
				if (this.maxBytes.get().longValue() == 0) {
					this.maxBytes = new Some<Long>(CpcConstants.Users.UNLIMITED_BYTES);
				}
				if (this.maxBytes.get() < 1 && this.maxBytes.get() != CpcConstants.Users.UNLIMITED_BYTES) {
					throw new BuilderException("Illegal value for user maxBytes: " + this.maxBytes.get());
				}
			}

			if (this.personalPlanId instanceof Some<?>) {

				/*
				 * A zero or negative number will be interpreted as removing the personal plan reference
				 */
				if (this.personalPlanId.get() < 1) {
					this.personalPlanId = null;
				}
			}

		}

		public UserUpdateCmd build() throws CommandException {
			this.validate();
			return new UserUpdateCmd(this);
		}
	}
}
