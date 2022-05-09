package com.code42.computer;

import java.util.Set;

import javax.persistence.Transient;

import com.backup42.history.CpcHistoryLogger;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.user.User;
import com.code42.user.UserFindByIdQuery;
import com.google.inject.Inject;

public class ComputerActivateCmd extends DBCmd<ComputerActivateCmd.Result> {

	public enum Result {
		NOT_FOUND, NOT_DEACTIVATED, SUCCESS
	}

	public enum Errors {
		USER_IS_BLOCKED, USER_IS_DEACTIVATED
	}

	private int computerId;

	@Transient
	private Computer computer = null;

	private Set<ComputerEventCallback> computerEventCallbacks;

	@Inject
	public void setUserEventCallbacks(Set<ComputerEventCallback> computerEventCallbacks) {
		this.computerEventCallbacks = computerEventCallbacks;
	}

	public ComputerActivateCmd(int computerId) {
		this.computerId = computerId;
	}

	public int getComputerId() {
		return this.computerId;
	}

	@Override
	public Result exec(CoreSession session) throws CommandException {
		// Make sure they are running from a master server.
		if (!this.env.isMaster()) {
			throw new CommandException("Unable to activate computer, not on master.");
		}

		this.runtime.run(new IsComputerManageableCmd(this.computerId, C42PermissionApp.Computer.UPDATE), session);

		// Validate that the computer's user is active and not blocked
		this.computer = this.runtime.run(new ComputerFindByIdCmd(this.computerId), session);
		if (this.computer != null) {
			int userId = this.computer.getUserId();
			User user = this.db.find(new UserFindByIdQuery(userId));
			if (!user.isActive()) {
				throw new CommandException(Errors.USER_IS_DEACTIVATED, "Unable to activate computer for deactivated user", user
						.getUsername(), this.computer.getGuid());
			}
			if (user.isBlocked()) {
				throw new CommandException(Errors.USER_IS_BLOCKED, "Unable to activate computer for blocked user", user
						.getUsername(), this.computer.getGuid());
			}
		}

		try {
			this.db.beginTransaction();

			// Return null if this computer does not exist.
			if (this.computer == null) {
				return Result.NOT_FOUND;
			}

			if (this.computer.getActive()) {
				return Result.NOT_DEACTIVATED;
			}

			this.computer.setActive(true);
			this.computer = this.db.update(new ComputerUpdateQuery(this.computer));

			for (ComputerEventCallback callback : this.computerEventCallbacks) {
				callback.computerActivate(this.computer, session);
			}

			this.db.afterTransaction(new ComputerPublishUpdateCmd(this.computer), session);

			this.db.commit();
			CpcHistoryLogger.info(session, "activated computer: {}", this.computer);
		} catch (CommandException e) {
			throw e;
		} catch (Throwable t) {
			throw new CommandException("Unexpected exception while activating computer", t);
		} finally {
			this.db.endTransaction();
		}

		return Result.SUCCESS;
	}

	public static void main(String[] args) {
		Computer c = new Computer();
		c.setName("Hi Mom");
		CommandException e = new CommandException(Errors.USER_IS_DEACTIVATED, "this is the message", c);
		System.out.println(e.toString());

	}
}
