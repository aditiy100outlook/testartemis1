package com.code42.user;

import com.backup42.history.CpcHistoryLogger;
import com.code42.auth.RoleFindByNameCmd.RoleFindByNameQuery;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.utils.LangUtils;

/**
 * Used to give a user a role with permissions.
 */
public class UserRoleCreateCmd extends DBCmd<UserRole> {

	private static final Logger log = LoggerFactory.getLogger(UserRoleCreateCmd.class);

	private final Builder data;

	public enum Errors {
		USER_NOT_FOUND, ROLE_NAME_MISSING, ROLE_NOT_FOUND
	}

	private UserRoleCreateCmd(Builder data) {
		super();
		this.data = data;
	}

	@Override
	public UserRole exec(CoreSession session) throws CommandException {
		this.run(new IsUserManageableCmd(this.data.user, C42PermissionApp.User.UPDATE), session);

		// Find Role by name
		final Role role = this.db.find(new RoleFindByNameQuery(this.data.roleName));
		if (role == null) {
			throw new CommandException(Errors.ROLE_NOT_FOUND, "Cannot add role; not found. roleName=" + this.data.roleName);
		}

		// Skip if the user already has the role.
		int userId = this.data.user.getUserId();
		int roleId = role.getRoleId();
		final UserRole existingUserRole = this.db.find(new UserRoleFindByUserAndRoleQuery(userId, roleId));
		if (existingUserRole != null) {
			Object[] args = new Object[] { this.data.user.getUsername(), this.data.roleName };
			log.debug("User already has the role. Ignore. username={}, role={}", args);
			return existingUserRole;
		}

		this.db.beginTransaction();

		UserRole createdUserRole = null;
		try {

			// Now associate the role to the user
			UserRole userRole = new UserRole();
			userRole.setUser(this.data.user);
			userRole.setRole(role);
			createdUserRole = this.db.create(new UserRoleCreateQuery(userRole));

			this.db.afterTransaction(new UserPublishUpdateCmd(this.data.user), session);

			this.db.commit();

			CpcHistoryLogger.info(session, "created user role. user:{}/{} role:{}", this.data.user.getUserId(),
					this.data.user.getUsername(), role.getRoleName());

		} catch (Exception e) {
			this.db.rollback();
			throw new CommandException("Error creating role for user: {}", createdUserRole, e);
		} finally {
			this.db.endTransaction();
		}

		return createdUserRole;
	}

	public static class Builder {

		private final User user;
		private final String roleName;

		public Builder(User user, String roleName) {

			this.user = user;
			this.roleName = roleName;
		}

		public void validate() throws BuilderException {
			if (this.user == null || this.user.getUserId() == null) {
				throw new BuilderException(Errors.USER_NOT_FOUND, "Unable to create user role, missing user.");
			}
			if (!LangUtils.hasValue(this.roleName)) {
				throw new BuilderException(Errors.ROLE_NAME_MISSING, "Unable to create user role, missing role name.");
			}
		}

		public UserRoleCreateCmd build() throws BuilderException {
			this.validate();
			return new UserRoleCreateCmd(this);
		}
	}

}
