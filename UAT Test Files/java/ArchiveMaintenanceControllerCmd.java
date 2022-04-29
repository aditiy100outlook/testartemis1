package com.code42.archive.maintenance;

import com.code42.core.CommandException;
import com.code42.core.ICmd;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;

/**
 * Controller for running archive maintenance commands
 */
public class ArchiveMaintenanceControllerCmd extends AbstractCmd<Void> {

	public static enum Action {
		MAINTAIN_ARCHIVE_NO_INTERRUPT, //
		RESET_ARCHIVE, //
		CANCEL_MAINTENANCE_JOB
	}

	private final Action action;
	private final long sourceGuid;
	private final long targetGuid;

	public ArchiveMaintenanceControllerCmd(Action action, long sourceGuid, long targetGuid) {
		this.action = action;
		this.sourceGuid = sourceGuid;
		this.targetGuid = targetGuid;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		ICmd command = null;

		switch (this.action) {
		case MAINTAIN_ARCHIVE_NO_INTERRUPT:
			command = new ArchiveMaintainCmd(this.sourceGuid, this.targetGuid, true);
			break;
		case RESET_ARCHIVE:
			command = new ArchiveResetCmd(this.sourceGuid, this.targetGuid);
			break;
		case CANCEL_MAINTENANCE_JOB:
			command = new ArchiveMaintenanceCancelCmd(this.sourceGuid, this.targetGuid);
			break;
		}

		this.run(command, session);

		return null;
	}

}
