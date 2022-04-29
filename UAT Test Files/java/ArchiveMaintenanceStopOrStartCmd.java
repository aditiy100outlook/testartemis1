package com.code42.archive.maintenance;

import com.backup42.app.cpc.backup.CPCArchiveMaintenanceManager;
import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.IEnvironment;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.google.inject.Inject;

/**
 * Start or stop the maintenance queues.
 * 
 * @author mharper
 */
public class ArchiveMaintenanceStopOrStartCmd extends AbstractCmd<Void> {

	private final static Logger log = LoggerFactory.getLogger(ArchiveMaintenanceStopOrStartCmd.class);

	@Inject
	private IEnvironment environment;

	public enum CmdType {
		STOP, START
	}

	private final CmdType cmdType;
	private final int serverId;

	public ArchiveMaintenanceStopOrStartCmd(CmdType cmdType, int serverId) {
		super();
		this.cmdType = cmdType;
		this.serverId = serverId;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		if (this.serverId != this.environment.getMyNodeId()) {
			log.warn("ArchiveMaintenanceStopOrStartCmd - Called on an invalid server id: " + this.serverId);
			return null;
		}
		CPCArchiveMaintenanceManager manager = ArchiveMaintenanceFindCmd.getManager();

		if (this.cmdType == CmdType.STOP) {
			manager.stop();
			log.info("ArchiveMaintenanceStopOrStartCmd - Stopped the archive maintenance jobs");
		} else {
			manager.start();
			log.info("ArchiveMaintenanceStopOrStartCmd - Started the archive maintenance jobs");
		}
		return null;
	}
}