package com.code42.org.destination;

import java.util.ArrayList;
import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.org.BackupOrg;
import com.code42.org.Org;
import com.code42.org.OrgFindAllQuery;

/**
 * Find the Orgs that are offering the given Destination, either directly or through inheritance.
 * 
 * NOTE: This is currently very inefficient; need to restructure data to improve.
 */
public class OrgDestinationFindOrgByAvailableCmd extends DBCmd<List<Integer>> {

	private int destinationId;

	public OrgDestinationFindOrgByAvailableCmd(int destinationId) {
		this.destinationId = destinationId;
	}

	@Override
	public List<Integer> exec(CoreSession session) throws CommandException {

		this.auth.isSysadmin(session);

		List<Integer> offeringOrgs = new ArrayList<Integer>();

		List<BackupOrg> orgs = this.db.find(new OrgFindAllQuery());
		for (Org org : orgs) {
			List<OrgDestination> orgDests = this.run(new OrgDestinationFindAvailableByOrgCmd(org.getOrgId()), session);
			if (this.isOffering(orgDests)) {
				offeringOrgs.add(org.getOrgId());
			}
		}

		return offeringOrgs;
	}

	private boolean isOffering(List<OrgDestination> orgDests) {
		for (OrgDestination orgDest : orgDests) {
			if (this.destinationId == orgDest.getDestinationId()) {
				return true;
			}
		}
		return false;
	}

}
