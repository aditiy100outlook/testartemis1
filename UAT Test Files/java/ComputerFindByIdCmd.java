package com.code42.computer;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.DBCmd;

/**
 * Command to find a computer object by its id
 */
public class ComputerFindByIdCmd extends DBCmd<Computer> {

	private long computerId;

	public ComputerFindByIdCmd(long computerId) {
		this.computerId = computerId;
	}

	@Override
	public Computer exec(CoreSession session) throws CommandException {

		Computer c = this.db.find(new ComputerFindByIdQuery(this.computerId));

		if (c == null) {
			return null;
		}

		// Authorize: Make sure the subject is allowed to view/read this computer
		this.runtime.run(new IsComputerManageableCmd(this.computerId, C42PermissionApp.Computer.READ), session);

		return c;
	}

}