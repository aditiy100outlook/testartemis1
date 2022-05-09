package com.code42.user;

import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.utils.LangUtils;
import com.code42.validation.rules.EmailRule;

/**
 * Create user via the web admin console.
 */
public class UserRegistrationCmd extends UserRegistrationBaseCmd {

	private UserRegistrationCmd(Builder builder) {
		super(builder);
	}

	@Override
	void validate(User user) throws CommandException {
		if (user != null) {
			if (LangUtils.hasValue(user.getPassword())) {
				if (user.getOrgId() != this.data.orgId) {
					throw new CommandException(Error.USER_DUPLICATE, "Duplicate user.  username: " + this.data.username);
				}
				if (user.isBlocked()) {
					throw new CommandException(Error.USER_BLOCKED, "Blocked user.  username: " + this.data.username);
				}
				if (user.isActive()) {
					throw new CommandException(Error.USER_DUPLICATE, "Duplicate user.  username: " + this.data.username);
				}
			} else {
				// The user does not have a password and is therefore a "placeholder" account. Move it to the new
				// org and let the process continue.
				user.setOrgId(this.data.orgId);
				user.setBlocked(false);
			}
		}
	}

	@Override
	void addDefaultRoles(User user, CoreSession session) throws CommandException {
		this.run(new UserRoleAssignDefaultsCmd(user, this.org), session);
	}

	@Override
	public User exec(CoreSession session) throws CommandException {
		return super.exec(session);
	}

	// //////////////////
	// BUILDER
	// //////////////////

	public static class Builder extends UserBuilder<UserRegistrationCmd> {

		public Builder(int orgId, String username) throws BuilderException {
			super(orgId, username);
		}

		@Override
		public void validate() throws BuilderException {
			super.validate();
			if (LangUtils.hasValue(this.email) && !EmailRule.isValidEmail(this.email.get())) {
				throw new BuilderException(Error.EMAIL_INVALID, "Email is invalid");
			}
		}

		@Override
		public UserRegistrationCmd build() throws BuilderException {
			this.validate();
			return new UserRegistrationCmd(this);
		}
	}
}
