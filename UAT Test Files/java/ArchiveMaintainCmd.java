/*
 * Created on Nov 22, 2011 by John Lundberg <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.archive.maintenance;

import com.backup42.app.cpc.clusterpeer.PeerCommunicationException;
import com.backup42.common.ComputerType;
import com.backup42.history.CpcHistoryLogger;
import com.code42.backup.archive.ArchiveMaintenanceRequest;
import com.code42.computer.Computer;
import com.code42.computer.ComputerFindByGuidCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;

/**
 * Send a message to the holder of the archive to perform maintenance on the archive for this computer.
 * 
 * NOTE: the message is always sent, even if the recipient is the server itself.
 */
public class ArchiveMaintainCmd extends DBCmd<Void> {

	private IArchiveMaintenanceController amController;

	private final long sourceGuid;
	private final long targetGuid;
	private final boolean required;

	public enum Error {
		INVALID_GUID, ERROR, FAIL, UNMAINTAINABLE
	}

	public ArchiveMaintainCmd(long sourceGuid, long targetGuid, boolean required) {
		this.sourceGuid = sourceGuid;
		this.targetGuid = targetGuid;
		this.required = required;
	}

	@Inject
	public void setAmController(IArchiveMaintenanceController amController) {
		this.amController = amController;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.validateTarget();
		this.validateSource(session);

		try {
			CpcHistoryLogger.info(session, "REQUEST: Maintenance: source: {}, target: {}", this.sourceGuid, this.targetGuid);
			ArchiveMaintenanceRequest msg = new ArchiveMaintenanceRequest(this.sourceGuid, this.targetGuid, this.required);
			this.amController.send(msg);
			CpcHistoryLogger.info(session, "SUCCESS: Maintenance: source: {}, target: {}", this.sourceGuid, this.targetGuid);
			return null;
		} catch (PeerCommunicationException e) {
			throw e;
		} catch (Exception e) {
			throw new CommandException(Error.ERROR, "ERROR: Maintenance: source: {}, target: {}", this.sourceGuid,
					this.targetGuid, e);
		}
	}

	/**
	 * Ensures the source computer exists and is of type {@link ComputerType#COMPUTER} or {@link ComputerType#PLAN}.
	 * 
	 * Also ensures that the user has permission to read (access) the computer.
	 * 
	 * @param session the user's session
	 * @throws CommandException if user does not have permission to the computer or the computer is invalid.
	 */
	@VisibleForTesting
	void validateSource(CoreSession session) throws CommandException {

		/*
		 * Using the user's session to get the source computer to ensure the user can read the archive that is being
		 * maintained.
		 */
		Computer source = this.runtime.run(new ComputerFindByGuidCmd(this.sourceGuid), session);
		if (source == null
				|| !(ComputerType.COMPUTER.equals(source.getType()) || ComputerType.PLAN.equals(source.getType()))) {
			throw new CommandException(Error.INVALID_GUID, "Source not computer or plan");
		}
	}

	/**
	 * Ensures the target computer exists and is of type {@link ComputerType#SERVER}. Permissions are not checked to allow
	 * a user to start maintenance on their computer while not having access to the target server.
	 * 
	 * @throws CommandException if the target server is not valid.
	 */
	@VisibleForTesting
	void validateTarget() throws CommandException {
		/*
		 * Using the system session to get the target computer because a PRO user does not have permission to read a server
		 * computer. But we only need the target computer to validate that it exists and is a server.
		 */
		CoreSession systemSession = this.auth.getSystemSession();
		Computer target = this.runtime.run(new ComputerFindByGuidCmd(this.targetGuid), systemSession);
		if (target == null || target.getType() != ComputerType.SERVER) {
			throw new CommandException(Error.UNMAINTAINABLE, "Target not a server");
		}
	}
}
