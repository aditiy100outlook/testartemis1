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
import com.code42.email.signup.WelcomeEmailSendBlueChannelCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.perm.C42PermissionCommerce.Commerce;
import com.code42.user.User;
import com.code42.user.UserFindByUsernameCmd;
import com.code42.user.UserProAdminInviteCreateCmd;
import com.code42.user.UserRegistrationCmd;
import com.code42.utils.LangUtils;
import com.code42.utils.Pair;
import com.code42.utils.option.None;
import com.code42.validation.rules.EmailRule;
import com.google.inject.Inject;

/**
 * This command is used to create a pro org for a channel partner. It will create the org then add an invited pro admin
 * user. This shouldn't be used for normal org creation when we have a password for the admin user.
 */
public class ProOrgChannelCreateCmd extends OrgCreateBaseCmd {

	private static final Logger log = LoggerFactory.getLogger(ProOrgChannelCreateCmd.class);

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

	private ProOrgChannelCreateCmd(Builder data) {
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

			// if they are authorized for creating channel orgs then we use admin session to run command
			session = this.auth.getAdminSession();

			this.db.beginTransaction();

			BackupOrg org = new BackupOrg();
			org.setOrgName(this.myData.orgName);
			org.setCustomConfig(true);
			org = super.createOrg(session, org);
			final int orgId = org.getOrgId();

			// invite user
			final UserProAdminInviteCreateCmd inviteCmd = new UserProAdminInviteCreateCmd(this.myData.email,
					this.myData.customerId, orgId);
			Pair<List<User>, List<Pair<String, Exception>>> results = this.runtime.run(inviteCmd, session);
			List<User> users = results.getOne();
			List<Pair<String, Exception>> errors = results.getTwo();

			if (errors.size() > 0) {
				throw new CommandException(errors.get(0).getOne(), errors.get(0).getTwo());
			}

			for (OrgEventCallback callback : this.orgEventCallbacks) {
				callback.orgCreatePRO(org, session);
			}

			this.db.commit();

			// send email post-commit
			for (User user : users) {
				ProOrgChannelCreateCmd.this.run(new WelcomeEmailSendBlueChannelCmd(org, user, this.myData.email), session);
			}

			return org;
		} catch (CommandException ce) {
			this.db.rollback();
			log.warn("Error Creating Org", this.data.orgName, ce);
			throw ce;
		} catch (Throwable t) {
			this.db.rollback();
			log.warn("Error Creating Org", this.data.orgName, t);
			throw new CommandException("Error Creating Org; org=" + this.data.orgName, t);
		} finally {
			this.db.endTransaction();
		}

	}

	private void authorize(CoreSession session) throws UnauthorizedException, CommandException {

		this.auth.isAuthorized(session, Commerce.CHANNEL);

		if (!this.env.isCpcMaster()) {
			throw new UnsupportedRequestException(
					"Unable to create CrashPlan PRO business org, restricted to CrashPlan Central.");
		}

		// This command is not authenticated; it runs internally as sysadmin
		session = this.auth.getAdminSession();

		// Make sure the user is not a consumer.
		UserFindByUsernameCmd.Builder builder = new UserFindByUsernameCmd.Builder(this.myData.email);
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

		private String email;
		private String customerId;

		public Builder(String company) throws BuilderException {
			super(company);
			this.parentOrgUid(OrgDef.PRO_ONLINE.getOrgUid());
		}

		public Builder email(String email) {
			this.email = email;
			return this;
		}

		public Builder customerId(String customerId) {
			this.customerId = customerId;
			return this;
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

				if (OrgDef.ADMIN.getOrgUid() == this.parentOrgUid.get()) {
					throw new BuilderException(OrgCreateCmd.Error.PARENT_ORG_BLOCKED, "Children of org not allowed");
				}

				/* We don't allow for the creation of orgs underneath the CrashPlan org */
				if (!(this.parentOrgId instanceof None) && this.parentOrgId.get() == CpcConstants.Orgs.CP_ID) {
					throw new BuilderException(OrgCreateCmd.Error.PARENT_ORG_BLOCKED, "Children of org not allowed");
				}

				if (this.parentOrgUid.get() != OrgDef.PRO_ONLINE.getOrgUid()) {
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

			if (!EmailRule.isValidEmail(this.email)) {
				throw new BuilderException(UserRegistrationCmd.Error.EMAIL_INVALID, "Valid email is required");
			}

			if (this.customerId == null) {
				throw new BuilderException("CustomerId is required");
			}
		}

		@Override
		public ProOrgChannelCreateCmd build() throws BuilderException {
			this.validate();
			return new ProOrgChannelCreateCmd(this);
		}
	}
}
