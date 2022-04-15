package com.code42.org;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.businessobjects.BusinessObjectsException;
import com.code42.core.businessobjects.IBusinessObjectsService;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.google.inject.Inject;

/**
 * Get the OrgSso (Space Storage Object) for this orgId, checking auth cache first
 */
public class OrgSsoFindByOrgIdCmd extends AbstractCmd<OrgSso> {

	/* ================= Dependencies ================= */
	private IBusinessObjectsService busobj;

	/* ================= DI injection points ================= */
	@Inject
	public void setBusobj(IBusinessObjectsService busobj) {
		this.busobj = busobj;
	}

	@SuppressWarnings("unused")
	private static Logger log = LoggerFactory.getLogger(OrgSsoFindByOrgIdCmd.class);

	// Properties
	private final int orgId;

	public OrgSsoFindByOrgIdCmd(int orgId) {
		this.orgId = orgId;
	}

	@Override
	public OrgSso exec(CoreSession session) throws CommandException {

		try {

			return this.busobj.getOrg(this.orgId);
		} catch (BusinessObjectsException boe) {
			throw new CommandException("Exception while getting org SSO", boe);
		}
	}
}
