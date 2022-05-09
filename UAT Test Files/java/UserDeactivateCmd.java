package com.code42.user;

import java.util.Set;

import com.backup42.history.CpcHistoryLogger;
import com.backup42.social.SocialComputerNetworkServices;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.impl.DBCmd;
import com.google.inject.Inject;

public class UserDeactivateCmd extends DBCmd<UserDeactivateCmd.Result> {

	public enum Result {
		NOT_FOUND, NOT_ACTIVE, SUCCESS
	}

	private int userId;

	private User user;

	private Set<UserEventCallback> userEventCallbacks;

	@Inject
	public void setUserEventCallbacks(Set<UserEventCallback> userEventCallbacks) {
		this.userEventCallbacks = userEventCallbacks;
	}

	public UserDeactivateCmd(int userId) {
		this.userId = userId;
	}

	public int getUserId() {
		return this.userId;
	}

	@Override
	public Result exec(CoreSession session) throws CommandException {

		// If userId does not exist, or permission check fails, this check will throw an exception
		this.runtime.run(new IsUserManageableCmd(this.userId, C42PermissionApp.User.UPDATE), session);

		// This is a brute-force check that prevents a user from deactivating their own account.
		if (this.userId == session.getUser().getUserId()) {
			throw new UnauthorizedException("Request Refused; a given user cannot deactivate themselves.");
		}

		this.user = null;
		this.db.beginTransaction();
		try {

			this.user = this.runtime.run(new UserFindByIdCmd(this.userId), session);
			if (this.user == null || this.user.getUserId() == null) {
				return Result.NOT_FOUND;
			}
			this.ensureNotHostedOrg(this.user.getOrgId(), session);

			/* If the user is already deactivated, there's no point in continuing */
			if (!this.user.isActive()) {
				return Result.NOT_ACTIVE;
			}

			/*
			 * Note that we do NOT do a database update here! The deactivateUser() method below takes care of this (along with
			 * a number of other things) in the deactivation case.
			 */
			// BUG 3637: AccountServices.getInstance().deactivateUser(u)
			SocialComputerNetworkServices.getInstance().deactivateUser(this.user);

			for (UserEventCallback callback : this.userEventCallbacks) {
				callback.userDeactivate(this.user, session);
			}

			this.db.afterTransaction(new UserPublishUpdateCmd(this.user), session);

			this.db.commit();

			CpcHistoryLogger.info(session, "deactivated user: {}/{}", this.userId, this.user.getUsername());

		} catch (CommandException ce) {
			this.db.rollback();
			throw ce;
		} catch (Throwable t) {
			this.db.rollback();
			throw new CommandException("Unexpected exception while deactivating user", t);
		} finally {
			this.db.endTransaction();
		}

		return Result.SUCCESS;
	}
}
