package com.code42.license;

import java.util.List;

import com.backup42.history.CpcHistoryLogger;
import com.code42.computer.Computer;
import com.code42.computer.ComputerFindByIdCmd;
import com.code42.computer.ComputerFindIdsNotAssignedALicense;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.code42.exception.DebugException;
import com.code42.logging.Logger;
import com.code42.user.User;

/**
 * Automatically assign a computer license to a computer if there is exactly one unassigned, active computer license and
 * exactly one active computer that is not assigned to a license.
 */
public class LicenseAutoAssignToComputerCmd extends DBCmd<Void> {

	private final static Logger log = Logger.getLogger(LicenseAutoAssignToComputerCmd.class);

	private final int userId;

	public LicenseAutoAssignToComputerCmd(int userId) {
		super();
		this.userId = userId;
	}

	public LicenseAutoAssignToComputerCmd(User user) {
		this(user.getUserId());
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		// find unassigned, active computer licenses
		final List<ComputerLicense> unassigned = this.runtime.run(new ComputerLicenseFindActiveUnassignedByUserIdCmd(
				this.userId), session);
		final int numUnassigned = unassigned.size();
		if (numUnassigned == 1) { // exactly one unassigned computer license
			// get the active computers that are not assigned to a computer license
			final List<Long> computerIds = this.db.find(new ComputerFindIdsNotAssignedALicense(this.userId));
			final int numComputers = computerIds.size();
			if (numComputers == 1) { // exactly one unassigned computer
				final ComputerLicense computerLicense = unassigned.get(0);
				final Computer computer = CoreBridge.runNoException(new ComputerFindByIdCmd(computerIds.get(0)));
				CpcHistoryLogger.info(null, this.getAutoAssignMsg(computer, computerLicense));
				log.info("Auto-assigning computer license to computer. " + computer + ", " + computerLicense);
				try {
					this.run(new LicenseAssignToComputerCmd(computerLicense.getKey(), computer, true), session);
				} catch (Exception e) {
					DebugException d = new DebugException("Failed to auto-assign a computer license to computer. " + computer
							+ ", " + computerLicense + ", " + e, e);
					log.warn(d.getMessage(), d);
				}
			}
		}

		return null;
	}

	private String getAutoAssignMsg(Computer c, ComputerLicense cl) {
		StringBuilder b = new StringBuilder("Auto-assigning computer license to computer. ");
		b.append("Computer: ").append(c.getComputerId());
		b.append(", name=").append(c.getName());
		b.append(", userId=").append(c.getUserId());
		b.append("License: ").append(cl.getKey());
		b.append(", endDate=").append(cl.getEndDate());
		return b.toString();
	}
}
