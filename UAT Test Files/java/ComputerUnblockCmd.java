package com.code42.computer;

import javax.persistence.Transient;

import com.backup42.history.CpcHistoryLogger;
import com.backup42.social.SocialComputerNetworkServices;
import com.code42.core.CommandException;
import com.code42.core.IEnvironment;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.user.User;
import com.code42.user.UserFindByIdQuery;
import com.google.inject.Inject;

public class ComputerUnblockCmd extends DBCmd<ComputerUnblockCmd.Result> {

	public enum Errors {
		USER_IS_BLOCKED, USER_IS_DEACTIVATED
	}

	/* ================= Dependencies ================= */
	private IEnvironment env;

	/* ================= DI injection points ================= */
	@Inject
	public void setEnv(IEnvironment env) {
		this.env = env;
	}

	public enum Result {
		NOT_FOUND, NOT_BLOCKED, SUCCESS
	}

	private long computerId;

	@Transient
	private Computer computer = null;

	public ComputerUnblockCmd(long computerId) {
		this.computerId = computerId;
	}

	public ComputerUnblockCmd(Computer computer) {
		if (computer == null) {
			throw new IllegalArgumentException("Invalid Parameter; computer is null");
		}
		this.computer = computer;
		this.computerId = computer.getComputerId();
	}

	@Override
	public Result exec(CoreSession session) throws CommandException {
		// Make sure they are running from a master server.
		if (!this.env.isMaster()) {
			throw new CommandException("Unable to unblock computer, not on master.");
		}

		// If permission check fails, this will throw an exception
		this.runtime.run(new IsComputerManageableCmd(this.computerId, C42PermissionApp.Computer.UPDATE), session);

		if (this.computer == null) {
			this.computer = this.runtime.run(new ComputerFindByIdCmd(this.computerId), session);
		}

		// Validate that the computer's user is active and not blocked
		if (this.computer != null) {
			int userId = this.computer.getUserId();
			User user = this.db.find(new UserFindByIdQuery(userId));
			if (user.isBlocked()) {
				throw new CommandException(Errors.USER_IS_BLOCKED, "Unable to unblock computer for blocked user", new Object[] {
						user, this.computer });
			}
			if (!user.isActive()) {
				throw new CommandException(Errors.USER_IS_DEACTIVATED, "Unable to unblock computer for deactivated user",
						new Object[] { user, this.computer });
			}
		}

		this.db.beginTransaction();
		try {

			// Return null if this computer does not exist.
			if (this.computer == null) {
				return Result.NOT_FOUND;
			}

			if (!this.computer.getBlocked()) {
				return Result.NOT_BLOCKED;
			}

			this.computer.setBlocked(false);
			this.computer = this.db.update(new ComputerUpdateQuery(this.computer));

			// Notice that the work actually occurs outside of the transaction
			SocialComputerNetworkServices.getInstance().notifyComputerOfChange(this.computer.getGuid());

			this.db.afterTransaction(new ComputerPublishUpdateCmd(this.computer), session);

			this.db.commit();

			CpcHistoryLogger.info(session, "unblocked computer: {}", this.computer);
		} catch (CommandException e) {
			this.db.rollback();
			throw e;
		} catch (Throwable t) {
			this.db.rollback();
			throw new CommandException("Unexpected exception while blocking org", t);
		} finally {
			this.db.endTransaction();
		}

		return Result.SUCCESS;
	}
}
