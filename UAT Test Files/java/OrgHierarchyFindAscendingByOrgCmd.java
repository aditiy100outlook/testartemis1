package com.code42.hierarchy;

import java.util.ArrayList;
import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.hierarchy.HierarchyNotFoundException;
import com.code42.core.hierarchy.IHierarchyService;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.BackupOrg;
import com.code42.org.OrgFindByIdQuery;
import com.google.inject.Inject;

/**
 * Command to retrieve the org hierarchy for a given org ID. This improves on IHierarchyService.getAscendingOrgs(orgId)
 * by cleanly handling the case where the org in question has been created, but not committed, and hence does not yet
 * have a hierarchy.
 * <p>
 * When should you use this instead of using IHierarchyService.getAscendingOrgs directly? Whenever you may be looking up
 * the hierarchy for an org that has been created, but not yet committed. You can use it other times, but this command
 * only adds value in the uncommitted org case.
 * <p>
 * NOTE! The first element of the returned list is the 'self' org, the last is the root org.
 */
public class OrgHierarchyFindAscendingByOrgCmd extends DBCmd<List<Integer>> {

	private static final Logger log = LoggerFactory.getLogger(OrgHierarchyFindAscendingByOrgCmd.class);

	@Inject
	private IHierarchyService hier;

	private int orgId;

	public OrgHierarchyFindAscendingByOrgCmd(int orgId) {
		this.orgId = orgId;
	}

	public int getOrgId() {
		return this.orgId;
	}

	@Override
	public List<Integer> exec(CoreSession session) throws CommandException {

		/**
		 * The permission check was removed because it was decided that the list of orgIds that constitute the org hierarchy
		 * is not a security risk and simplifies authorization checks (which require the ascending org hierarchy).
		 */

		List<Integer> hierarchy = null;
		try {
			hierarchy = this.hier.getAscendingOrgs(this.orgId);
			log.trace("hierarchy for orgId {} is: {}", this.orgId, hierarchy);
		} catch (HierarchyNotFoundException e) {
			// This was added for the situation where a new org has been created, but not yet committed.
			// Hence the hierarchy does not yet exist.
			BackupOrg org = this.db.find(new OrgFindByIdQuery(this.orgId));
			if (org == null) {
				throw new CommandException("OrgId {} is not in the database", this.orgId);
			}
			if (org.getParentOrgId() == null) {
				hierarchy = new ArrayList<Integer>();
			} else {
				try {
					hierarchy = this.hier.getAscendingOrgs(org.getParentOrgId());
					hierarchy = new ArrayList(hierarchy); // Make it mutable
				} catch (HierarchyNotFoundException e1) {
					throw new CommandException("Hierarchy not found for parent org {}", org.getParentOrgId(), e1);
				}
			}
			hierarchy.add(0, this.orgId); // My orgId must be first
		}

		return hierarchy;
	}
}
