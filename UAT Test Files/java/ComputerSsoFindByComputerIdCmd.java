package com.code42.computer;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.businessobjects.BusinessObjectsException;
import com.code42.core.businessobjects.IBusinessObjectsService;
import com.code42.core.impl.AbstractCmd;
import com.google.inject.Inject;

/**
 * Get the ComputerSso (space storage object) for this computerId, checking auth cache first
 */
public class ComputerSsoFindByComputerIdCmd extends AbstractCmd<ComputerSso> {

	/* ================= Dependencies ================= */
	private IBusinessObjectsService busobj;

	/* ================= DI injection points ================= */
	@Inject
	public void setBusobj(IBusinessObjectsService busobj) {
		this.busobj = busobj;
	}

	private final long computerId;

	public ComputerSsoFindByComputerIdCmd(long computerId) {
		this.computerId = computerId;
	}

	@Override
	public ComputerSso exec(CoreSession session) throws CommandException {

		/* TODO: This was true when we were a DB command... is it still true? */
		// Authorizing here causes infinite loops.

		try {

			return this.busobj.getComputer(this.computerId);
		} catch (BusinessObjectsException boe) {
			throw new CommandException("Exception while getting org SSO", boe);
		}
	}
}
