package com.code42.archive.maintenance;

import com.backup42.app.cpc.backup.CPCArchiveMaintenanceManager;
import com.backup42.common.ComputerType;
import com.backup42.common.perm.C42PermissionPro;
import com.code42.computer.Computer;
import com.code42.computer.ComputerFindByGuidCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.lang.ThreadUtils;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;

/**
 * Attempt to cancel an archive maintenance job.
 * 
 * @author mharper
 */
public class ArchiveMaintenanceCancelCmd extends AbstractCmd<Void> {

	private final static Logger log = LoggerFactory.getLogger(ArchiveMaintenanceCancelCmd.class);

	private final long sourceGuid;
	private final long targetGuid;

	public ArchiveMaintenanceCancelCmd(long sourceGuid, long targetGuid) {
		super();
		this.sourceGuid = sourceGuid;
		this.targetGuid = targetGuid;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.ARCHIVE_MAINTENANCE);

		Computer target = this.runtime.run(new ComputerFindByGuidCmd(this.targetGuid), session);
		if (target == null || target.getType() != ComputerType.SERVER) {
			throw new CommandException(ArchiveMaintainCmd.Error.UNMAINTAINABLE, "Target not a server");
		}

		CPCArchiveMaintenanceManager manager = ArchiveMaintenanceFindCmd.getManager();
		log.info("ArchiveMaintenanceCancelCmd - Attempting to cancel archive maintenance job: sourceGuid="
				+ this.sourceGuid + ", targetGuid=" + this.targetGuid);
		// XXX: the job may be in one of several places, but there is no API to find out where. So, we try several.
		boolean didCancel = manager.removePendingMaintJob(this.sourceGuid, this.targetGuid);
		if (didCancel == true) {
			log.info("ArchiveMaintenanceCancelCmd - Successfully removed a pending job");
		} else {
			didCancel = manager.cancelMaintJob(this.sourceGuid, this.targetGuid);
			if (didCancel == true) {
				log.info("ArchiveMaintenanceCancelCmd - Successfully cancelled a running job");
			} else {
				log.info("ArchiveMaintenanceCancelCmd - Failed to cancel a job (it may have already finished)");
			}
		}
		ThreadUtils.delay(1000); // XXX: give the queue a chance to react.
		return null;
	}
}