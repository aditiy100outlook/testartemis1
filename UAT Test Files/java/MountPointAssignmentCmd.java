/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.server.mount;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.computer.Computer;
import com.code42.computer.ComputerFindByGuidQuery;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.code42.server.destination.Destination;
import com.code42.server.destination.DestinationFindByMountCmd;

/**
 * Assign a given GUID to a particular target and mount point, generally in response to an external activity like
 * seeding. This command is NOT typically called by the internal system.
 */
public class MountPointAssignmentCmd extends DBCmd<Void> {

	public enum Error {
		INVALID_GUID, INVALID_MOUNT_POINT
	}

	private final long computerGuid;
	private final int mountPointId;

	public MountPointAssignmentCmd(long computerGuid, int mountPointId) {
		this.computerGuid = computerGuid;
		this.mountPointId = mountPointId;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		final Computer computer = this.db.find(new ComputerFindByGuidQuery(this.computerGuid));
		if (computer == null) {
			throw new CommandException(Error.INVALID_GUID, "Invalid Parameter; GUID doesn't exist: {}", this.computerGuid);
		}

		final MountPoint mp = this.db.find(new MountPointFindByIdQuery(this.mountPointId));
		if (mp == null) {
			throw new CommandException(Error.INVALID_MOUNT_POINT, "Invalid Parameter; mount doesn't exist: {}",
					this.mountPointId);
		}

		// Determine the destination the mount point belongs to
		final DestinationFindByMountCmd cmd = new DestinationFindByMountCmd(this.mountPointId);
		final Destination destination = this.runtime.run(cmd, session);
		final long destinationGuid = destination.getDestinationGuid();

		CoreBridge.getCentralService().getBackup().recordMigrationCompleted(this.computerGuid, destinationGuid, null,
				this.mountPointId);

		return null;
	}
}
