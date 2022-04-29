package com.code42.computer;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.DBCmd;

/**
 * Command to find a computer object by its GUID
 */
public class ComputerFindByGuidCmd extends DBCmd<Computer> {

	private long guid;

	public ComputerFindByGuidCmd(long guid) {
		this.guid = guid;
	}

	public ComputerFindByGuidCmd(String uid) {
		this.guid = Long.valueOf(uid);
	}

	@Override
	public Computer exec(CoreSession session) throws CommandException {

		Computer c = this.db.find(new ComputerFindByGuidQuery(this.guid));
		if (c == null) {
			return null;
		}

		// Authorize: Make sure the subject is allowed to view/read this computer
		this.runtime.run(new IsComputerManageableCmd(c.getComputerId(), C42PermissionApp.Computer.READ), session);
		return c;
	}

}