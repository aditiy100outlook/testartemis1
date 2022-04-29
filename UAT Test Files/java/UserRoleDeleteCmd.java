/*
 * Created on Apr 21, 2011 by Tony Lindquist <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.user;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import com.backup42.history.CpcHistoryLogger;
import com.code42.auth.RoleFindByNameCmd.RoleFindByNameQuery;
import com.code42.core.CommandException;
import com.code42.core.UnsupportedRequestException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.DeleteQuery;
import com.code42.core.impl.DBCmd;

public class UserRoleDeleteCmd extends DBCmd<Void> {

	private final Integer userId;
	private final String roleName;

	public enum Errors {
		ROLE_NOT_FOUND
	}

	public UserRoleDeleteCmd(UserRole userRole) {
		// Do not save the hibernate entity; can cause session troubles
		this.userId = (userRole.getUser() != null) ? userRole.getUser().getUserId() : null;
		this.roleName = (userRole.getRole() != null) ? userRole.getRole().getRoleName() : null;
	}

	public UserRoleDeleteCmd(int userId, String roleName) {
		this.userId = userId;
		this.roleName = roleName;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.validate();

		this.run(new IsUserManageableCmd(this.userId, C42PermissionApp.User.UPDATE), session);

		// This is a brute-force check that prevents a user from removing a role from their own
		// account. This could be refined, if more exact definitions of allowable actions could
		// be determined, but the current goal is to prevent a user from demoting themselves.
		if (this.userId.equals(session.getUser().getUserId())) {
			throw new UnauthorizedException("Request Refused; a given user cannot demote themselves.");
		}

		UserRole userRole = null;
		try {
			this.db.beginTransaction();

			final Role role = this.db.find(new RoleFindByNameQuery(this.roleName));
			if (role == null) {
				throw new CommandException(Errors.ROLE_NOT_FOUND, "Invalid roleName: " + this.roleName);
			}
			userRole = this.db.find(new UserRoleFindByUserAndRoleQuery(this.userId, role.getRoleId()));

			if (userRole == null) {
				// The user doesn't have the role; consider it deleted
				return null;
			}

			// If we're still here, we have a valid relationship; delete it
			this.db.delete(new UserRoleDeleteQuery(userRole));

			// Touch the user to indicate a change (see UserFindLastModified)
			this.db.update(new UserUpdateQuery(userRole.getUser()));

			this.db.afterTransaction(new UserPublishUpdateCmd(userRole.getUser()), session);

			this.db.commit();

		} catch (Exception e) {
			throw new CommandException("Error deleting role from user: " + userRole, e);
		} finally {
			this.db.endTransaction();
		}

		CpcHistoryLogger.info(session, "deleted user role. user:{}/{} role:{}", this.userId, userRole.getUser()
				.getUsername(), this.roleName);

		return null;
	}

	/**
	 * Ensure that we have a valid set of arguments on which to operate
	 * 
	 * @throws UnsupportedRequestException
	 */
	private void validate() throws UnsupportedRequestException {

		if (this.userId == null || this.roleName == null) {
			throw new UnsupportedRequestException("Insufficient info; userId: " + this.userId + "; roleName: "
					+ this.roleName);
		}
	}

	private static class UserRoleDeleteQuery extends DeleteQuery<UserRole> {

		private UserRole userRole;

		public UserRoleDeleteQuery(UserRole userRole) {
			this.userRole = userRole;
		}

		@Override
		public void query(Session session) throws DBServiceException {

			try {
				session.delete(this.userRole);
			} catch (HibernateException e) {
				throw new DBServiceException(e.getMessage(), e);
			}

		}

	}

}
