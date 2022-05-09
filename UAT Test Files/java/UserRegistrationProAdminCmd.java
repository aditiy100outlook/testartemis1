package com.code42.user;

import com.backup42.role.DesktopUserRole;
import com.backup42.role.ProOnlineAdminRole;
import com.backup42.role.ProOnlineUserRole;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.utils.LangUtils;

/**
 * Create a PRO admin user. Can only be called by the ProOrgCreate command.
 */
public class UserRegistrationProAdminCmd extends UserRegistrationBaseCmd {

	private UserRegistrationProAdminCmd(Builder data) {
		super(data);
	}

	@Override
	void validate(User user) throws CommandException {
		// No additional validation
	}

	/**
	 * Give them access to run the client, access account web app, access to admin console.
	 */
	@Override
	void addDefaultRoles(User user, CoreSession session) throws CommandException {
		// Give access to manage org
		this.addRole(user, ProOnlineAdminRole.ROLE_NAME, session);
		this.addRole(user, ProOnlineUserRole.ROLE_NAME, session);
		this.addRole(user, DesktopUserRole.ROLE_NAME, session);
	}

	@Override
	public User exec(CoreSession session) throws CommandException {
		return super.exec(this.auth.getAdminSession());
	}

	// ////////////////
	// BUILDER
	// ////////////////

	public static class Builder extends UserBuilder<UserRegistrationProAdminCmd> {

		public Builder(int orgId, String email) throws BuilderException {
			super(orgId, email);
		}

		@Override
		protected void validate() throws BuilderException {
			super.validate();
			if (!this.invitation && !LangUtils.hasValue(this.password)) {
				throw new BuilderException(Error.PASSWORD_MISSING, "Password is required");
			}
			if (!this.invitation && !LangUtils.hasValue(this.firstName)) {
				throw new BuilderException(Error.FIRST_NAME_MISSING, "First name is required");
			}
			if (!this.invitation && !LangUtils.hasValue(this.lastName)) {
				throw new BuilderException(Error.LAST_NAME_MISSING, "Last name is required");
			}
		}

		@Override
		public UserRegistrationProAdminCmd build() throws BuilderException {
			this.validate();
			return new UserRegistrationProAdminCmd(this);
		}
	}

}
