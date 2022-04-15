package com.code42.archive.maintenance;

import com.backup42.app.cpc.backup.CPCArchiveMaintenanceManager;
import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.IEnvironment;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.server.ServerSettings;
import com.code42.server.ServerSettingsFindByIdQuery;
import com.code42.server.ServerSettingsUpdateCmd;
import com.google.inject.Inject;

/**
 * Adjust the throttling rate for the user and system archive maintenance queues. This affects both the in-memory
 * representation of the job queues, and a system settings property in the db.
 * 
 * @author mharper
 */
public class ArchiveMaintenanceUpdateCpuThrottleCmd extends DBCmd<Void> {

	private final static Logger log = LoggerFactory.getLogger(ArchiveMaintenanceUpdateCpuThrottleCmd.class);

	@Inject
	private IEnvironment environment;

	public enum QueueType {
		USER, SYSTEM
	}

	public enum Error {
		INVALID_THROTTLE_PCT
	}

	private final QueueType queueType;
	private final int throttlePct;
	private final int serverId;

	public ArchiveMaintenanceUpdateCpuThrottleCmd(QueueType queueType, int throttlePct, int serverId) {
		super();
		this.queueType = queueType;
		this.throttlePct = throttlePct;
		this.serverId = serverId;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		// Because this command adjusts the in-memory ArchiveMaintenanceManager, we only allow it to be issued
		// against "my" server.
		if (this.serverId != this.environment.getMyNodeId()) {
			log.warn("ArchiveMaintenanceStopOrStartCmd - Called on an invalid server id: " + this.serverId);
			return null;
		}

		// validate priority value
		if (this.throttlePct < 1 || this.throttlePct > 100) {
			throw new CommandException(Error.INVALID_THROTTLE_PCT,
					"Throttling rate must be a value between 1 and 100 (inclusive)");
		}

		ServerSettings serverSettings = this.db.find(new ServerSettingsFindByIdQuery(this.serverId)); // db representation
		CPCArchiveMaintenanceManager manager = ArchiveMaintenanceFindCmd.getManager(); // in-memory representation

		if (this.queueType == QueueType.SYSTEM) {
			manager.setSystemRate(this.throttlePct);
			serverSettings.setMaintenanceRate(this.throttlePct);
		} else {
			manager.setUserRate(this.throttlePct);
			serverSettings.setUserMaintenanceRate(this.throttlePct);
		}
		this.run(new ServerSettingsUpdateCmd(serverSettings), session);
		log.info("ArchiveMaintenanceAdjustCpuThrottleCmd - set " + this.queueType + " throttling rate to "
				+ this.throttlePct);
		return null;
	}
}