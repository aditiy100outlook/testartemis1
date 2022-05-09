package com.code42.user;

import com.backup42.history.CpcHistoryLogger;
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

public class UserActivateCmd extends DBCmd<UserActivateCmd.Result> {

	public enum Result {
		SUCCESS, NOT_FOUND, NOT_DEACTIVATED
	}

	public enum Error {
		ORG_IS_DEACTIVATED
	}

	private static final Logger log = LoggerFactory.getLogger(UserActivateCmd.class);

	private int userId;

	private User user;

	public UserActivateCmd(int userId) {
		this.userId = userId;
	}

	public int getUserId() {
		return this.userId;
	}

	@Override
	public Result exec(CoreSession session) throws CommandException {
		this.runtime.run(new IsUserManageableCmd(this.userId, C42PermissionApp.User.UPDATE), session);

		// This is a brute-force check that prevents a user from activating their own account.
		if (this.userId == session.getUser().getUserId()) {
			throw new UnauthorizedException("Request Refused; a given user cannot activate themselves.");
		}

		this.db.beginTransaction();
		try {

			this.user = this.runtime.run(new UserFindByIdCmd(this.userId), session);
			if (this.user == null || this.user.getUserId() == null) {
				return Result.NOT_FOUND;
			}
			this.ensureNotHostedOrg(this.user.getOrgId(), session);

			/* If the user is already active there's no point in continuing */
			if (this.user.isActive()) {
				return Result.NOT_DEACTIVATED;
			}

			// Make sure the org is active before activating the user.
			OrgSso org = this.run(new OrgSsoFindByOrgIdCmd(this.user.getOrgId()), session);
			if (!org.isActive()) {
				log.info("Attempted to activate a user in a deactivated org: " + this.user);
				// The org needs to be reactivated first
				throw new CommandException(Error.ORG_IS_DEACTIVATED, "Attempted to activate a user in a deactivated org");
			}

			this.user.setActive(true);

			this.user = this.runtime.run(new UserValidateCmd(this.user), session);
			this.user = this.db.update(new UserUpdateQuery(this.user));

			this.db.afterTransaction(new UserPublishUpdateCmd(this.user), session);

			this.db.commit();

			CpcHistoryLogger.info(session, "activated user:{}/{} ", this.userId, this.user.getUsername());

		} catch (CommandException e) {
			this.db.rollback();
			throw e;
		} catch (Throwable t) {
			this.db.rollback();
			throw new CommandException("Unexpected exception while activating user", t);
		} finally {
			this.db.endTransaction();
		}

		return Result.SUCCESS;
	}
}
