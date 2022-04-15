package com.code42.computer;

import com.backup42.computer.ComputerServices;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;

public class ComputerDeauthorizeCmd extends DBCmd<ComputerDeauthorizeCmd.Result> {

	public enum Result {
		NOT_FOUND, NOT_AUTHORIZED, SUCCESS
	}

	private static final Logger log = LoggerFactory.getLogger(ComputerDeauthorizeCmd.class);

	private long computerId;

	public ComputerDeauthorizeCmd(long computerId) {
		this.computerId = computerId;
	}

	public long getComputerId() {
		return this.computerId;
	}

	@Override
	public Result exec(CoreSession session) throws CommandException {

		this.runtime.run(new IsComputerManageableCmd(this.computerId, C42PermissionApp.Computer.UPDATE), session);

		this.db.beginTransaction();
		try {

			// Return null if this computer does not exist.
			Computer computer = this.runtime.run(new ComputerFindByIdCmd(this.computerId), session);
			if (computer == null) {
				return Result.NOT_FOUND;
			}

			if (computer.getLoginHash() == null && computer.getAuthDate() == null) {
				return Result.NOT_AUTHORIZED;
			}

			ComputerServices.getInstance().deauthorize(computer, null, "?");

			log.info(session + " deauthorized computer: " + computer);

		} catch (CommandException e) {
			this.db.rollback();
			throw e;
		} catch (Throwable t) {
			this.db.rollback();
			throw new CommandException("Unexpected exception while blocking computer", t);
		} finally {
			this.db.endTransaction();
		}

		return Result.SUCCESS;
	}
}
