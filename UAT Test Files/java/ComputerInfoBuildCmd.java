package com.code42.computer;

import com.backup42.app.computer.ComputerInfo;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.db.DBServiceException;
import com.code42.core.impl.DBCmd;

/**
 * Wraps ComputerInfo.build(computer) so that we have a session around
 */
public class ComputerInfoBuildCmd extends DBCmd<ComputerInfo> {

	private Computer computer;

	public ComputerInfoBuildCmd(Computer c) {
		this.computer = c;
	}

	@Override
	public ComputerInfo exec(CoreSession session) throws CommandException {
		this.runtime.run(new IsComputerManageableCmd(this.computer.getComputerId(), C42PermissionApp.Computer.READ),
				session);

		try {
			this.db.openSession();
			try {
				return ComputerInfo.build(this.computer);
			} finally {
				this.db.closeSession();
			}
		} catch (DBServiceException e) {
			throw new CommandException("Unexpected DBServiceException", e);
		}
	}
}
