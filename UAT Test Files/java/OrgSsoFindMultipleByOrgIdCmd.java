package com.code42.org;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.businessobjects.BusinessObjectsException;
import com.code42.core.businessobjects.IBusinessObjectsService;
import com.code42.core.impl.AbstractCmd;
import com.google.inject.Inject;

/**
 * Given a list of orgIds, returns an OrgSso map keyed by orgId.
 */
public class OrgSsoFindMultipleByOrgIdCmd extends AbstractCmd<Map<Integer, OrgSso>> {

	private final Set<Integer> orgIds;

	@Inject
	private IBusinessObjectsService busObj;

	public OrgSsoFindMultipleByOrgIdCmd(Collection<Integer> orgIds) {
		this(new HashSet<Integer>(orgIds));
	}

	public OrgSsoFindMultipleByOrgIdCmd(Set<Integer> orgIds) {
		this.orgIds = orgIds;
	}

	@Override
	public Map<Integer, OrgSso> exec(CoreSession session) throws CommandException {

		final Map<Integer, OrgSso> rv;
		try {
			rv = this.busObj.getOrgs(this.orgIds);
		} catch (BusinessObjectsException e) {
			throw new CommandException("Error loading OrgSsos", e);
		}

		return rv;
	}
}
