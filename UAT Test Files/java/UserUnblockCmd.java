package com.code42.user;

import com.backup42.history.CpcHistoryLogger;
import com.backup42.social.SocialComputerNetworkServices;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.OrgSso;
import com.code42.org.OrgSsoFindByOrgIdCmd;

public class UserUnblockCmd extends DBCmd<UserUnblockCmd.Result> {

	private static final Logger log = LoggerFactory.getLogger(UserUnblockCmd.class);

	public enum Result {
		NOT_FOUND, NOT_BLOCKED, SUCCESS
	}

	public enum Error {
		ORG_IS_BLOCKED
	}

	private int userId;
	private User user;

	public UserUnblockCmd(int userId) {
		this.userId = userId;
	}

	public UserUnblockCmd(User user) {
		if (user == null) {
			throw new IllegalArgumentException("Invalid argument; user is null");
		}
		this.user = user;
		this.userId = user.getUserId();
	}

	@Override
	public Result exec(CoreSession session) throws CommandException {
		// If userId does not exist, or permission check fails, this check will throw an exception
		this.runtime.run(new IsUserManageableCmd(this.userId, C42PermissionApp.User.UPDATE), session);

		// This is a brute-force check that prevents a user from unblocking their own account.
		if (this.userId == session.getUser().getUserId()) {
			throw new UnauthorizedException("Unable to unblock user, can't unblock yourself.");
		}

		if (this.user == null) {
			this.user = this.runtime.run(new UserFindByIdCmd(this.userId), session);
		}

		this.db.beginTransaction();
		try {
			this.ensureNotHostedOrg(this.user.getOrgId(), session);

			if (this.user == null || this.user.getUserId() == null) {
				return Result.NOT_FOUND;
			}

			/* If the user is already unblocked there's no point in continuing */
			if (!this.user.isBlocked()) {
				return Result.NOT_BLOCKED;
			}

			// Make sure the org is unblocked before unblocking the user.
			OrgSso org = this.run(new OrgSsoFindByOrgIdCmd(this.user.getOrgId()), session);
			if (org.isBlocked()) {
				log.info("Attempted to unblock a user in a blocked org: " + this.user);
				// The org needs to be unblocked first
				throw new CommandException(Error.ORG_IS_BLOCKED, "Attempted to unblock a user in a blocked org");
			}

			this.user.setBlocked(false);

			this.user = this.runtime.run(new UserValidateCmd(this.user), session);
			this.user = this.db.update(new UserUpdateQuery(this.user));

			this.db.afterTransaction(new UserPublishUpdateCmd(this.user), session);

			SocialComputerNetworkServices.getInstance().notifyUserOfChange(this.user.getUserId());

			this.db.commit();

			CpcHistoryLogger.info(session, "unblocked user: {}/{}", this.userId, this.user.getUsername());

		} catch (CommandException e) {
			this.db.rollback();
			throw e;
		} catch (Throwable t) {
			this.db.rollback();
			throw new CommandException("Unexpected exception while unblocking user", t);
		} finally {
			this.db.endTransaction();
		}

		return Result.SUCCESS;
	}
}
