/*
 * Created on Nov 22, 2011 by John Lundberg <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.archive.maintenance;

import com.backup42.common.ComputerType;
import com.code42.archive.maintenance.IArchiveMaintenanceController;
import com.code42.backup.archive.ArchiveResetRequest;
import com.code42.computer.Computer;
import com.code42.computer.ComputerFindByGuidCmd;
import com.code42.computer.ComputerSso;
import com.code42.computer.ComputerSsoFindByGuidCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.IAuthorizationService;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.google.inject.Inject;

public class ArchiveResetCmd extends DBCmd<Void> {

	private static final Logger log = Logger.getLogger(ArchiveResetCmd.class);

	@Inject
	private IAuthorizationService auth;

	@Inject
	private IArchiveMaintenanceController amController;

	private final long sourceGuid;
	private final long targetGuid;

	public enum Error {
		ERROR
	}

	public ArchiveResetCmd(long sourceGuid, long targetGuid) {
		this.sourceGuid = sourceGuid;
		this.targetGuid = targetGuid;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionApp.User.UPDATE);

		ComputerSso computer = this.run(new ComputerSsoFindByGuidCmd(this.sourceGuid), session);
		this.runtime.run(new IsComputerManageableCmd(computer.getComputerId(), C42PermissionApp.Computer.ALL), session);

		Computer target = this.runtime.run(new ComputerFindByGuidCmd(this.targetGuid), session);
		if (target == null || target.getType() != ComputerType.SERVER) {
			throw new CommandException(ArchiveMaintainCmd.Error.UNMAINTAINABLE, "Target not a server");
		}

		try {
			log.info("REQUEST: Archive Reset: sourceGuid: {}, targetGuid: {}", this.sourceGuid, this.targetGuid);
			ArchiveResetRequest msg = new ArchiveResetRequest(this.sourceGuid, this.targetGuid);
			this.amController.send(msg);
			log.info("SUCCESS: Archive Reset: sourceGuid: {}, targetGuid: {}", this.sourceGuid, this.targetGuid);
			return null;
		} catch (Exception e) {
			throw new CommandException(Error.ERROR, "ERROR: Archive Reset: sourceGuid: {}, targetGuid: {}", this.sourceGuid,
					this.targetGuid, e);
		}
	}
}
