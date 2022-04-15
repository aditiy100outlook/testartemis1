package com.code42.org;

import java.util.List;
import java.util.Set;

import com.backup42.CpcConstants;
import com.backup42.common.OrgType;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.OrgDef;
import com.code42.core.UnsupportedRequestException;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.email.signup.WelcomeOrgEmailSendBlueCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.user.User;
import com.code42.user.UserFindByUsernameCmd;
import com.code42.user.UserMoveCmd;
import com.code42.user.UserRegistrationCmd;
import com.code42.user.UserRegistrationProAdminCmd;
import com.code42.utils.LangUtils;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;
import com.code42.validation.rules.EmailRule;
import com.google.inject.Inject;

/**
 * This is the standard pro org create. It will require password for the created user and will create the user
 * immediately.
 */
public class ProOrgCreateCmd extends OrgCreateBaseCmd {

	private static final Logger log = LoggerFactory.getLogger(ProOrgCreateCmd.class);

	public enum Error {
		USER_DUPLICATE_CONSUMER, ORG_NAME_TOO_SHORT
	}

	// Services
	private Builder myData;

	private Set<OrgEventCallback> orgEventCallbacks;

	@Inject
	public void setOrgEventCallbacks(Set<OrgEventCallback> orgEventCallbacks) {
		this.orgEventCallbacks = orgEventCallbacks;
	}

	private ProOrgCreateCmd(Builder data) {
		super(data);
		this.myData = data;
	}

	/* ProOrgCreateCmd _always_ returns a BUSINESS org */
	@Override
	public OrgType getOrgType() {
		return OrgType.BUSINESS;
	}

	@Override
	public Org exec(CoreSession session) throws CommandException {

		this.authorize(session);

		try {

			// This command is not authenticated; it runs internally as sysadmin
			session = this.auth.getAdminSession();

			this.db.beginTransaction();

			BackupOrg org = new BackupOrg();
			org.setCustomConfig(true);
			org = super.createOrg(session, org);
			final int orgId = org.getOrgId();

			if (this.myData.isLocalAuthServer()) {
				// Caller does not want this org inheriting LDAP or RADIUS authentication
				// from the parent org. Primarily used in testing.
				this.runtime.run(OrgLdapServerUpdateCmd.noLdap(org.getOrgId()), session);
			}

			// Create/Update user
			final UserRegistrationProAdminCmd.Builder userBuilder = new UserRegistrationProAdminCmd.Builder(orgId,
					this.myData.email.get());
			userBuilder.firstName(this.myData.firstName.get());
			userBuilder.lastName(this.myData.lastName.get());
			userBuilder.password(this.myData.password.get());
			User user = this.runtime.run(userBuilder.build(), session);

			// wrong org? move it
			if (user.getOrgId() != orgId) {
				log.info(session + " PRO Org Create:: Moving user. user={}, org={}", user, org);
				// Move user from Pro Online org to the org you've just created
				this.run(new UserMoveCmd(user.getUserId(), orgId), session);
			}

			for (OrgEventCallback callback : this.orgEventCallbacks) {
				callback.orgCreatePRO(org, session);
			}

			this.db.commit();

			// send email post-commit
			ProOrgCreateCmd.this.run(new WelcomeOrgEmailSendBlueCmd(org, this.myData.email.get()), session);

			return org;
		} catch (CommandException ce) {
			this.db.rollback();
			throw ce;
		} catch (Throwable t) {
			this.db.rollback();
			throw new CommandException("Error Creating Org; org=" + this.data.orgName, t);
		} finally {
			this.db.endTransaction();
		}

	}

	private void authorize(CoreSession session) throws UnauthorizedException, CommandException {

		if (!this.env.isCpcMaster()) {
			throw new UnsupportedRequestException(
					"Unable to create CrashPlan PRO business org, restricted to CrashPlan Central.");
		}

		// This command is not authenticated; it runs internally as sysadmin
		session = this.auth.getAdminSession();

		// Make sure the user is not a consumer.
		UserFindByUsernameCmd.Builder builder = new UserFindByUsernameCmd.Builder(this.myData.email.get());
		UserFindByUsernameCmd cmd = builder.build();

		List<User> users = this.runtime.run(cmd, session);
		for (User u : users) {
			boolean consumer = this.env.isCrashPlanOrg(u.getOrgId());
			boolean online = this.env.isProOnlineParentOrg(u.getOrgId());
			if (consumer) {
				throw new UnsupportedRequestException(Error.USER_DUPLICATE_CONSUMER,
						"Consumer user already exists with given email.");
			} else if (online) {
				// An anonymous user account may exist in the PRO Online Org, if they used their email address to make a
				// purchase. In this case, and this case alone, the user will be MOVED to the newly created org and
				// updated to have their name and password.
			} else {
				throw new UnsupportedRequestException(UserRegistrationCmd.Error.USER_DUPLICATE,
						"User already exists with given email.");
			}
		}

		this.parentOrg = this.getParentOrg();
	}

	public static class Builder extends OrgCreateBaseCmd.Builder {

		public static enum AuthServer {
			INHERIT, LOCAL
		}

		private Option<String> firstName = None.getInstance();
		private Option<String> lastName = None.getInstance();
		private Option<String> email = None.getInstance();
		private Option<String> password = None.getInstance();
		/** Switch to LOCAL if you don't want this org inheriting. Mostly useful for testing */
		private AuthServer authServerType = AuthServer.INHERIT;

		public Builder(String company) throws BuilderException {
			super(company);
			this.parentOrgUid(OrgDef.PRO_ONLINE.getOrgUid());
		}

		public Builder firstName(String firstName) {
			this.firstName = new Some<String>(firstName);
			return this;
		}

		public Builder lastName(String lastName) {
			this.lastName = new Some<String>(lastName);
			return this;
		}

		public Builder email(String email) {
			this.email = new Some<String>(email);
			return this;
		}

		public Builder password(String password) {
			this.password = new Some<String>(password);
			return this;
		}

		public Builder useLocalAuthServer() {
			this.authServerType = AuthServer.LOCAL;
			return this;
		}

		public boolean isLocalAuthServer() {
			return this.authServerType == AuthServer.LOCAL;
		}

		@Override
		protected void validate() throws BuilderException {
			if (!LangUtils.hasValue(this.orgName)) {
				throw new BuilderException(Error.ORG_NAME_TOO_SHORT, "Org Name must be provided");
			}

			if (!(this.parentOrgId instanceof None) && this.parentOrgId.get() <= 1) {
				throw new BuilderException(OrgCreateCmd.Error.PARENT_ORG_BLOCKED, "Children of org not allowed");
			}

			if (LangUtils.hasValue(this.parentOrgUid)) {

				if (OrgDef.ADMIN.getOrgUid().equals(this.parentOrgUid.get())) {
					throw new BuilderException(OrgCreateCmd.Error.PARENT_ORG_BLOCKED, "Children of org not allowed");
				}

				/* We don't allow for the creation of orgs underneath the CrashPlan org */
				if (!(this.parentOrgId instanceof None) && this.parentOrgId.get() == CpcConstants.Orgs.CP_ID) {
					throw new BuilderException(OrgCreateCmd.Error.PARENT_ORG_BLOCKED, "Children of org not allowed");
				}

				if (!this.parentOrgUid.get().equals(OrgDef.PRO_ONLINE.getOrgUid())) {
					throw new BuilderException(OrgCreateCmd.Error.PARENT_ORG_BLOCKED,
							"PRO Online client orgs must be a child of PRO ONLINE");
				}

			}

			if (LangUtils.length(this.orgName) < 3) {
				throw new BuilderException(Error.ORG_NAME_TOO_SHORT, "Org name must be at least three characters long");
			}

			if (!LangUtils.hasValue(this.email)) {
				throw new BuilderException(UserRegistrationCmd.Error.EMAIL_MISSING, "Email is required");
			}

			if (!EmailRule.isValidEmail(this.email.get())) {
				throw new BuilderException(UserRegistrationCmd.Error.EMAIL_INVALID, "Valid email is required");
			}

			if (!LangUtils.hasValue(this.firstName)) {
				throw new BuilderException(UserRegistrationCmd.Error.FIRST_NAME_MISSING, "First name is required");
			}
			if (!LangUtils.hasValue(this.lastName)) {
				throw new BuilderException(UserRegistrationCmd.Error.LAST_NAME_MISSING, "Last name is required");
			}
			if (!LangUtils.hasValue(this.password)) {
				throw new BuilderException(UserRegistrationCmd.Error.PASSWORD_MISSING, "Password is required");
			}
		}

		@Override
		public ProOrgCreateCmd build() throws BuilderException {
			this.validate();
			return new ProOrgCreateCmd(this);
		}
	}
}
