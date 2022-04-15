package com.code42.computer;

import com.backup42.history.CpcHistoryLogger;
import com.backup42.social.SocialComputerNetworkServices;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.DBCmd;

public class ComputerBlockCmd extends DBCmd<ComputerBlockCmd.Result> {

	public enum Result {
		NOT_FOUND, ALREADY_BLOCKED, SUCCESS
	}

	private long computerId;
	private Computer computer;

	public ComputerBlockCmd(long computerId) {
		this.computerId = computerId;
	}

	public ComputerBlockCmd(Computer computer) {
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

		this.runtime.run(new IsComputerManageableCmd(this.computerId, C42PermissionApp.Computer.UPDATE), session);

		if (this.computer == null) {
			this.computer = this.runtime.run(new ComputerFindByIdCmd(this.computerId), session);
		}

		try {
			this.db.beginTransaction();

			// Return null if this computer does not exist.
			if (this.computer == null) {
				return Result.NOT_FOUND;
			}

			if (this.computer.getBlocked()) {
				return Result.ALREADY_BLOCKED;
			}

			this.computer.setBlocked(true);

			this.db.update(new ComputerUpdateQuery(this.computer));

			SocialComputerNetworkServices.getInstance().notifyComputerOfChange(this.computer.getGuid());

			this.db.afterTransaction(new ComputerPublishUpdateCmd(this.computer), session);

			this.db.commit();

			CpcHistoryLogger.info(session, "blocked computer: {}", this.computer);
		} catch (CommandException e) {
			throw e;
		} catch (Throwable t) {
			throw new CommandException("Unexpected exception while blocking computer", t);
		} finally {
			this.db.endTransaction();
		}

		return Result.SUCCESS;
	}
}
