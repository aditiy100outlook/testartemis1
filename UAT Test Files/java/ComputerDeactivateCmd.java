package com.code42.computer;

import java.util.Set;

import com.backup42.alerts.AlertInfo;
import com.backup42.history.CpcHistoryLogger;
import com.backup42.social.SocialComputerNetworkServices;
import com.code42.backup.alert.AlertStatusEvent;
import com.code42.backup.stats.alerts.AlertType;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.google.inject.Inject;

public class ComputerDeactivateCmd extends DBCmd<ComputerDeactivateCmd.Result> {

	public enum Result {
		NOT_FOUND, //
		NOT_ACTIVE, //
		SUCCESS
	}

	private final long computerId;
	private final ComputerSettings options;

	private Set<ComputerEventCallback> computerEventCallbacks;

	@Inject
	public void setUserEventCallbacks(Set<ComputerEventCallback> computerEventCallbacks) {
		this.computerEventCallbacks = computerEventCallbacks;
	}

	public ComputerDeactivateCmd(long computerId) {
		this(computerId, null);
	}

	public ComputerDeactivateCmd(long computerId, ComputerSettings options) {
		this.computerId = computerId;
		this.options = options;
	}

	public long getComputerId() {
		return this.computerId;
	}

	@Override
	public Result exec(CoreSession session) throws CommandException {
		// Make sure they are running from a master server.
		if (!this.env.isMaster()) {
			throw new CommandException("Unable to deactivate computer, not on master.");
		}

		this.runtime.run(new IsComputerManageableCmd(this.computerId, C42PermissionApp.Computer.UPDATE), session);

		/*
		 * This TX appears to be completely redundant (since SocialComputerNetworkServices manages it's own transactions).
		 * Without this scoping, however, we get complaints about the Computer object already being defined in the state...
		 * presumably from the ComputerFindByIdCmd call just before we get to SocialComputerNetworkServices. Bundling
		 * everything within a single TX seems to resolve the problem.
		 */
		try {
			this.db.beginTransaction();

			// Return null if this computer does not exist.
			Computer computer = this.runtime.run(new ComputerFindByIdCmd(this.computerId), session);
			if (computer == null) {
				return Result.NOT_FOUND;
			}

			if (!computer.getActive()) {
				return Result.NOT_ACTIVE;
			}

			/*
			 * Disable any alerts that are set for this computer.
			 */
			AlertInfo alertFlags = computer.getAlertInfo();
			if (alertFlags.isCriticalBackupAlert()) {
				CoreBridge.post(new AlertStatusEvent(computer.getGuid(), false, AlertType.BACKUP_CRITICAL));
			} else if (alertFlags.isWarningBackupAlert()) {
				CoreBridge.post(new AlertStatusEvent(computer.getGuid(), false, AlertType.BACKUP_WARNING));
			}

			// Never join this set of if/else with the above. There could be both backup and connection alerts
			if (alertFlags.isCriticalConnectionAlert()) {
				CoreBridge.post(new AlertStatusEvent(computer.getGuid(), false, AlertType.CONNECTION_CRITICAL));
			} else if (alertFlags.isWarningConnectionAlert()) {
				CoreBridge.post(new AlertStatusEvent(computer.getGuid(), false, AlertType.CONNECTION_WARNING));
			}

			if (alertFlags.isRepositoryMissingAlert()) {
				CoreBridge.post(new AlertStatusEvent(computer.getGuid(), false, AlertType.REPOSITORY_MISSING));
			}

			computer.setAlertState(0);

			/*
			 * Note that deactivateComputer() handles all DB TX ops so there's no need for us to define a top-level
			 * transaction here
			 */
			SocialComputerNetworkServices.getInstance().deactivateComputer(computer, "?", this.options);

			for (ComputerEventCallback callback : this.computerEventCallbacks) {
				callback.computerDeactivate(computer, session);
			}

			this.db.afterTransaction(new ComputerPublishUpdateCmd(computer), session);

			this.db.commit();

			CpcHistoryLogger.info(session, "deactivated computer: {}", computer);

		} catch (CommandException e) {
			throw e;
		} catch (Throwable t) {
			throw new CommandException("Unexpected exception while deactivating computer", t);
		} finally {
			this.db.endTransaction();
		}

		return Result.SUCCESS;
	}
}
