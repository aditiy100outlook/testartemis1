package com.code42.archive.maintenance;

import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.ICmd;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;

/**
 * Controller for running server archive maintenance commands
 */
public class ServerArchiveMaintenanceControllerCmd extends AbstractCmd<Void> {

	public enum Action {
		STOP_MAINTENANCE_JOBS, //
		START_MAINTENANCE_JOBS, //
		UPDATE_CPU_THROTTLE_USER, //
		UPDATE_CPU_THROTTLE_SYSTEM
	}

	public enum Error {
		MISSING_THROTTLE_PCT
	}

	private final Action action;
	private final int serverId;
	private final Integer throttlePct;

	public ServerArchiveMaintenanceControllerCmd(Builder builder) {
		this.action = builder.action;
		this.serverId = builder.serverId;
		this.throttlePct = builder.throttlePct;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		final ICmd command;

		switch (this.action) {

		case STOP_MAINTENANCE_JOBS:
			command = new ArchiveMaintenanceStopOrStartCmd(ArchiveMaintenanceStopOrStartCmd.CmdType.STOP, this.serverId);
			break;
		case START_MAINTENANCE_JOBS:
			command = new ArchiveMaintenanceStopOrStartCmd(ArchiveMaintenanceStopOrStartCmd.CmdType.START, this.serverId);
			break;

		case UPDATE_CPU_THROTTLE_USER:
			command = new ArchiveMaintenanceUpdateCpuThrottleCmd(ArchiveMaintenanceUpdateCpuThrottleCmd.QueueType.USER,
					this.throttlePct, this.serverId);
			break;
		case UPDATE_CPU_THROTTLE_SYSTEM:
			command = new ArchiveMaintenanceUpdateCpuThrottleCmd(ArchiveMaintenanceUpdateCpuThrottleCmd.QueueType.SYSTEM,
					this.throttlePct, this.serverId);
			break;
		default:
			// If a new Action is added and we're not handling it, fail
			command = null;
			break;
		}

		if (command == null) {
			throw new CommandException("Unknown action: " + this.action);
		}

		this.run(command, session);

		return null;
	}

	public static class Builder {

		private final Action action;
		private final int serverId;
		private Integer throttlePct = null;

		public Builder(Action action, int serverId) {
			this.action = action;
			this.serverId = serverId;
		}

		public Builder throttlePct(Integer throttlePct) {
			this.throttlePct = throttlePct;
			return this;
		}

		public ServerArchiveMaintenanceControllerCmd build() throws BuilderException {
			this.validate();
			return new ServerArchiveMaintenanceControllerCmd(this);
		}

		private void validate() throws BuilderException {

			if (this.action.equals(Action.UPDATE_CPU_THROTTLE_SYSTEM) || this.action.equals(Action.UPDATE_CPU_THROTTLE_USER)) {
				if (this.throttlePct == null) {
					throw new BuilderException(Error.MISSING_THROTTLE_PCT, "Param 'throttlePct' is required");
				}
			}
		}

	}

}
