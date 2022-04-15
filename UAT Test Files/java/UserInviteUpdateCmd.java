/*
 * <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.user;

import com.backup42.common.AuthorizeRules;
import com.backup42.common.OrgType;
import com.backup42.common.util.CPValidation;
import com.code42.account.AuthorizeRulesFindByOrgIdCmd;
import com.code42.auth.AutoTokenHandler;
import com.code42.auth.InviteToken;
import com.code42.auth.InviteTokenHandler;
import com.code42.auth.InviteTokenValidateCmd;
import com.code42.auth.PasswordUtil;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.IAuthorizationService;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.NotFoundException;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.code42.crypto.StringHasher;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.OrgAuthType;
import com.code42.org.OrgAuthTypeFindByOrgCmd;
import com.code42.org.OrgSso;
import com.code42.org.OrgSsoFindByOrgIdCmd;
import com.code42.utils.LangUtils;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Validate and update all the fields on the given user <b>except the password</b>, ensuring that the usernameId field
 * gets populated, if it isn't already.<br>
 * <br>
 * As of Bugzilla 1587 this command also cannot block, unblock, activate or deactivate a user.
 */
public class UserInviteUpdateCmd extends DBCmd<User> {

	private static Logger log = LoggerFactory.getLogger(UserInviteUpdateCmd.class);

	/* ================= Dependencies ================= */
	private IAuthorizationService auth;
	private InviteTokenHandler handler;

	/* ================= DI injection points ================= */
	@Inject
	public void setAuthService(IAuthorizationService auth) {
		this.auth = auth;
	}

	@Inject
	public void setHandler(@Named("invite") AutoTokenHandler handler) {
		this.handler = (InviteTokenHandler) handler;
	}

	private Builder data = null;

	public enum Error {
		NEW_PASSWORD_INVALID, FIRSTNAME_INVALID, LASTNAME_INVALID, TOKEN_INVALID, USERNAME_INVALID
	}

	/**
	 * Use the Builder static inner class to construct one of these.
	 */
	private UserInviteUpdateCmd(Builder b) {
		this.data = b;
	}

	@Override
	public User exec(CoreSession session) throws CommandException {

		User user = null;

		this.db.beginTransaction();

		try {

			// First thing, validate the InviteToken
			UserInviteDto dto = this.runtime.run(new InviteTokenValidateCmd(this.data.encryptedToken.get()), null);
			if (dto == null || dto.getUserId() == null) {
				InviteToken token = this.handler.handleInboundToken(this.data.encryptedToken.get());
				throw new NotFoundException("Unable to update user; token data is invalid: " + token);
			}

			user = this.db.find(new UserFindByIdQuery(dto.getUserId()));

			OrgSso org = this.run(new OrgSsoFindByOrgIdCmd(user.getOrgId()), session);

			if (org.getType() == OrgType.BUSINESS) {
				if (!LangUtils.hasValue(this.data.firstName)) {
					throw new CommandException(Error.FIRSTNAME_INVALID, "firstName cannot be null");
				}
				if (!LangUtils.hasValue(this.data.lastName)) {
					throw new CommandException(Error.LASTNAME_INVALID, "lastName cannot be null");
				}
			}

			if (!dto.getUsernameIsAnEmail()) {
				if (this.data.username instanceof None) {
					user.setUsername(this.data.email.get());
				} else {
					user.setUsername(this.data.username.get());
				}
			}

			if (!(this.data.firstName instanceof None)) {
				user.setFirstName(this.data.firstName.get());
			}

			if (!(this.data.lastName instanceof None)) {
				user.setLastName(this.data.lastName.get());
			}

			if (!(this.data.email instanceof None)) {
				user.setEmail(this.data.email.get());
			}

			// Handle password change a little differently
			if (!(this.data.password instanceof None)) {

				String password = this.data.password.get();

				AuthorizeRules rules = this.runtime.run(new AuthorizeRulesFindByOrgIdCmd(user.getOrgId()), this.auth
						.getAdminSession());
				if (!CPValidation.isValidPassword(password, rules)) {
					throw new CommandException(Error.NEW_PASSWORD_INVALID, "Invalid password.");
				}
				user.setPassword(StringHasher.C42.isValidHash(password) ? password : StringHasher.C42.hash(password));
			}

			/*
			 * A user should NEVER be here without a usernameId
			 */
			assert (LangUtils.hasValue(user.getUserUid()));

			user = this.runtime.run(new UserValidateCmd(user), this.auth.getAdminSession());

			user = this.db.update(new UserUpdateQuery(user));
			this.db.commit();

			this.db.afterTransaction(new UserPublishUpdateCmd(user), session);

			log.info(user.getUsername() + " accepted their invitation: " + this.data.userId);

		} catch (CommandException e) {
			this.db.rollback();
			throw e;
		} catch (Throwable e) {
			this.db.rollback();
			log.error("Unexpected: ", e);
		} finally {
			this.db.endTransaction();
		}

		return user;
	}

	/**
	 * Builds the input data and the UserInviteUpdate command. This takes the place of a big long constructor.
	 */
	public static class Builder {

		/* This val must always be present; it's the only way to get a builder */
		protected final int userId;

		protected Option<String> password = None.getInstance();
		protected Option<String> firstName = None.getInstance();
		protected Option<String> lastName = None.getInstance();
		protected Option<String> username = None.getInstance();
		protected Option<String> email = None.getInstance();
		protected Option<String> encryptedToken = None.getInstance();

		public Builder(int userId) {
			this.userId = userId;
		}

		public Builder password(String password) {
			this.password = new Some<String>(password);
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

		public Builder username(String username) {
			this.username = new Some<String>(username);
			return this;
		}

		public Builder email(String email) {
			this.email = new Some<String>(email);
			return this;
		}

		public Builder encryptedToken(String encryptedToken) {
			this.encryptedToken = new Some<String>(encryptedToken);
			return this;
		}

		public void validate() throws BuilderException {

			OrgAuthType authType = this.getAuthType();

			if (authType == OrgAuthType.LOCAL) {
				if (!LangUtils.hasValue(this.password)) {
					throw new BuilderException("password cannot be null");
				}
			} else {
				// Set a random password for non local authtypes
				this.password = new Some<String>(StringHasher.C42.hash(PasswordUtil.generatePassword(20)));
			}

			if (!LangUtils.hasValue(this.encryptedToken)) {
				throw new BuilderException("encryptedToken cannot be null");
			}

		}

		public UserInviteUpdateCmd build() throws BuilderException {
			this.validate();
			return new UserInviteUpdateCmd(this);
		}

		@VisibleForTesting
		OrgAuthType getAuthType() {
			IUser u = CoreBridge.runNoException(new UserSsoFindByUserIdCmd(this.userId));
			return CoreBridge.runNoException(new OrgAuthTypeFindByOrgCmd(u.getOrgId()));
		}
	}
}