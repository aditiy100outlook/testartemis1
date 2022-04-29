/*
 * Created on Feb 16, 2011 by Tony Lindquist <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.archive;

import com.code42.computer.Computer;
import com.code42.computer.cpc.FriendComputerUsageFindCpcUsageByGuidCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.social.FriendComputerUsage;

/**
 * Determines whether the given source computer has an archive on any server at all, master, slave or clustered. The
 * command is null-safe; a null value will result in a response of false.
 * 
 * @author tlindqui
 */
public class ArchiveExistsCmd extends DBCmd<Boolean> {

	private final Computer computer;

	public ArchiveExistsCmd(Computer computer) {
		this.computer = computer;
	}

	@Override
	public Boolean exec(CoreSession session) throws CommandException {
		if (this.computer != null) {
			long computerId = this.computer.getComputerId();
			this.runtime.run(new IsComputerManageableCmd(computerId, C42PermissionApp.Computer.READ), session);
		}

		boolean hasArchive = false;
		boolean isClustered = this.env.isCpcMaster();
		if (this.computer != null) {
			if (isClustered) {
				FriendComputerUsage fcu = this.run(new FriendComputerUsageFindCpcUsageByGuidCmd(this.computer.getGuid()),
						session);
				if (fcu != null && fcu.getMountPointId() != null) {
					hasArchive = true;
				}
			}

			if (!hasArchive) {
				hasArchive = this.runtime.run(new ArchiveExistsLocalCmd(this.computer.getGuid()), session);
			}

			if (!hasArchive) {
				hasArchive = this.runtime.run(new ArchiveExistsSlaveCmd(this.computer.getComputerId()), session);
			}
		}

		return hasArchive;
	}

}
