package com.code42.computer;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.businessobjects.BusinessObjectsException;
import com.code42.core.businessobjects.IBusinessObjectsService;
import com.code42.core.impl.DBCmd;
import com.google.inject.Inject;

/**
 * Get the ComputerSso (space storage object) for this guid, checking auth cache first
 */
public class ComputerSsoFindByGuidCmd extends DBCmd<ComputerSso> {

	/* ================= Dependencies ================= */
	private IBusinessObjectsService busobj;

	/* ================= DI injection points ================= */
	@Inject
	public void setBusobj(IBusinessObjectsService busobj) {
		this.busobj = busobj;
	}

	private final long guid;

	public ComputerSsoFindByGuidCmd(long guid) {
		this.guid = guid;
	}

	@Override
	public ComputerSso exec(CoreSession session) throws CommandException {

		try {

			return this.busobj.getComputerByGuid(this.guid);
		} catch (BusinessObjectsException boe) {
			throw new CommandException("Exception while getting org SSO", boe);
		}
	}
}