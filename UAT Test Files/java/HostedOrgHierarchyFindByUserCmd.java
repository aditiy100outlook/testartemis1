package com.code42.hierarchy;

import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.org.OrgSso;
import com.code42.org.OrgSsoFindByOrgIdCmd;
import com.code42.user.IUser;

/**
 * Find all the orgs within a HostedOrg virtual tree. The given user is assumed to be within that tree somewhere, and
 * the returned list includes all the orgs in that tree. If the user is NOT within a hosted org tree, the returned list
 * is the user's normal org hierarchy.
 * 
 * <code>
 * The logic is as follows:
 * 	- find the Org for the given user
 * 	- find the HostedParentOrg up the tree from the user's org
 * 	- find all the orgs underneath that HostedParentOrg
 * </code>
 * 
 */
public class HostedOrgHierarchyFindByUserCmd extends OrgHierarchyFindBaseCmd {

	private IUser user;

	public HostedOrgHierarchyFindByUserCmd(IUser user) {
		this.user = user;
	}

	@Override
	public List<Integer> exec(CoreSession session) throws CommandException {

		if (this.user == null || session == null) {
			throw new UnauthorizedException("No authenticated session passed in; request rejected");
		}

		List<Integer> orgIds = this.run(new OrgHierarchyFindByOrgIdCmd(this.user.getOrgId(), Direction.ASCENDING), session);

		// Find the last "hosted org" walking up the ascending tree; that will be our HostedParentOrg
		OrgSso hostedParentOrg = null;
		for (Integer orgId : orgIds) {
			OrgSso orgSso = this.run(new OrgSsoFindByOrgIdCmd(orgId), session);
			if (orgSso.isHosted() && orgSso.isMaster()) {
				hostedParentOrg = orgSso;
				break;
			}
		}

		if (hostedParentOrg == null) {
			return orgIds;
		}

		int hOrgId = hostedParentOrg.getOrgId();
		List<Integer> hOrgIds = this.run(new OrgHierarchyFindByOrgIdCmd(hOrgId, Direction.DESCENDING), session);
		return hOrgIds;
	}
}
