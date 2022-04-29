package com.code42.user;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import com.backup42.history.CpcHistoryLogger;
import com.code42.auth.RoleFindByNameCmd.RoleFindByNameQuery;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.DeleteQuery;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.user.UserRoleSyncCmd.Result;
import com.code42.utils.LangUtils;

/**
 * Synchronize a user's roles; make their roles match a list of input role names. <br>
 * <br>
 * Strongly based on UserRoleCreateCmd and UserRoleDeleteCmd.
 */
public class UserRoleSyncCmd extends DBCmd<Result> {

	private static final Logger log = LoggerFactory.getLogger(UserRoleSyncCmd.class);

	private final int userId;
	private final Collection<String> roleNames;
	private final boolean simulate;

	public UserRoleSyncCmd(int userId, Collection<String> roleNames) {
		this(userId, roleNames, false);
	}

	public UserRoleSyncCmd(int userId, Collection<String> roleNames, boolean simulate) {
		this.userId = userId;
		this.roleNames = new HashSet<String>(roleNames);
		this.simulate = simulate;
	}

	/**
	 * @return a list of all the UserRole instances for the user and a list of the ignored role names
	 */
	@Override
	public Result exec(CoreSession session) throws CommandException {

		/* Subject must at least be able to update this user */
		this.run(new IsUserManageableCmd(this.userId, C42PermissionApp.User.UPDATE), session);

		User user = this.db.find(new UserFindByIdQuery(this.userId));
		if (user == null) {
			throw new CommandException("User does not exist: " + this.userId);
		}

		Result result = new Result();

		Set<String> newRoleNames = new HashSet<String>(this.roleNames);

		try {
			this.db.beginTransaction();

			/*
			 * Loop through the existing roles and remove them from the new roles. If not there to remove, then delete the
			 * existing role
			 */
			List<UserRole> currentUserRoles = this.db.find(new UserRoleFindByUserQuery(this.userId));
			assert (currentUserRoles != null);
			for (UserRole ur : currentUserRoles) {
				String existingRoleName = ur.getRole().getRoleName();

				// Attempt to remove existing role from new role set
				if (newRoleNames.remove(existingRoleName)) {
					result.roleNames.add(existingRoleName); // No change necessary, but we want to return it in the list
				} else {
					// Existing user role was not found in the new set so we delete the existing one
					if (!this.simulate) {
						this.db.delete(new UserRoleDeleteQuery(ur));
					}
					result.removedRoleNames.add(existingRoleName);
				}
			}

			/*
			 * Any roles left in newRoleNames need to be added
			 */
			for (String roleName : newRoleNames) {
				Role role = this.db.find(new RoleFindByNameQuery(roleName));
				if (role == null) {
					log.info("Could not find role " + roleName + " to add to user: " + user);
					result.ignoredRoleNames.add(roleName);
					continue;
				}
				UserRole userRole = new UserRole();
				userRole.setUser(user);
				userRole.setRole(role);
				if (!this.simulate) {
					this.db.create(new UserRoleCreateQuery(userRole));
				}
				result.roleNames.add(roleName);
				result.addedRoleNames.add(roleName);
			}

			this.db.commit();

			// Only log if we actually changed something.
			// Every update command needs to do this for a more accurate history log.
			if (result.addedRoleNames.size() > 0 || result.removedRoleNames.size() > 0 && !this.simulate) {
				CpcHistoryLogger.info(session, "changed user roles for {}/{}, removed:{}, added:{}, ignored: {}", this.userId,
						user.getUsername(), LangUtils.toString(result.removedRoleNames, "[", ",", "]"), LangUtils.toString(
								result.addedRoleNames, "[", ",", "]"), LangUtils.toString(result.ignoredRoleNames, "[", ",", "]"));
			}

		} catch (Exception e) {
			this.db.rollback();
			throw new CommandException("Error synching roles for user: " + user.getUsername(), e);
		} finally {
			this.db.endTransaction();
		}

		return result;
	}

	/* Delete a UserRole object */
	public static class UserRoleDeleteQuery extends DeleteQuery<Collection<UserRole>> {

		UserRole userRole;

		public UserRoleDeleteQuery(UserRole userRole) {
			if (userRole == null) {
				throw new IllegalArgumentException("userRole argument may not be null");
			}
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

	public static class Result {

		public Collection<String> roleNames = new TreeSet<String>();
		public Collection<String> addedRoleNames = new TreeSet<String>();
		public Collection<String> removedRoleNames = new TreeSet<String>();
		public Collection<String> ignoredRoleNames = new TreeSet<String>();

		public boolean hasChange() {
			return this.addedRoleNames.size() > 0 || this.removedRoleNames.size() > 0;
		}
	}
}
