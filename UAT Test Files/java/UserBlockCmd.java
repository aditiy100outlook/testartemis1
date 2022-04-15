package com.code42.user;

import com.backup42.history.CpcHistoryLogger;
import com.backup42.social.SocialComputerNetworkServices;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.impl.DBCmd;

public class UserBlockCmd extends DBCmd<UserBlockCmd.Result> {

	public enum Result {
		SUCCESS, NOT_FOUND, ALREADY_BLOCKED
	}

	private int userId;

	private User user;

	public UserBlockCmd(int userId) {
		this.userId = userId;
	}

	public UserBlockCmd(User user) {
		if (user == null) {
			throw new IllegalArgumentException("Invalid argument; user is null");
		}
		this.userId = user.getUserId();
	}

	@Override
	public Result exec(CoreSession session) throws CommandException {

		// If userId does not exist, or permission check fails, this check will throw an exception
		this.runtime.run(new IsUserManageableCmd(this.userId, C42PermissionApp.User.UPDATE), session);

		// This is a brute-force check that prevents a user from blocking their own account.
		if (this.userId == session.getUser().getUserId()) {
			throw new UnauthorizedException("Request Refused; a given user cannot block themselves.");
		}

		this.user = null;
		try {
			this.db.beginTransaction();

			this.user = this.runtime.run(new UserFindByIdCmd(this.userId), session);
			this.ensureNotHostedOrg(this.user.getOrgId(), session);

			if (this.user == null || this.user.getUserId() == null) {
				return Result.NOT_FOUND;
			}

			/* If the user is already blocked there's no point in continuing */
			if (this.user.isBlocked()) {
				return Result.ALREADY_BLOCKED;
			}

			this.user.setBlocked(true);

			this.user = this.runtime.run(new UserValidateCmd(this.user), session);
			this.user = this.db.update(new UserUpdateQuery(this.user));

			this.db.afterTransaction(new UserPublishUpdateCmd(this.user), session);

			SocialComputerNetworkServices.getInstance().notifyUserOfChange(this.user.getUserId());

			this.db.commit();

			CpcHistoryLogger.info(session, "blocked user: {}/{}", this.userId, this.user.getUsername());

		} catch (CommandException e) {
			throw e;
		} catch (Throwable t) {
			throw new CommandException("Unexpected exception while blocking user", t);
		} finally {
			this.db.endTransaction();
		}

		return Result.SUCCESS;
	}
}
