package com.code42.hierarchy;

import java.util.LinkedList;
import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.hierarchy.HierarchyNotFoundException;
import com.code42.core.hierarchy.IHierarchyService;
import com.code42.core.impl.AbstractCmd;
import com.code42.hierarchy.OrgHierarchyFindBaseCmd.Direction;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

/**
 * Command to retrieve the org hierarchy for a given org ID. Command walks up the org tree from the input org to a root
 * org, returning a list of the results in order. The first element of the returned list is the root org and the last
 * element is the input org.
 * 
 * *****************************************************************************************************************<br>
 * PLEASE NOTE that when you switch code to use IHierarchyService instead of this class, the order of the objects coming
 * back is reversed from the order this command returns orgIds.<br>
 * *****************************************************************************************************************<br>
 * 
 * @deprecated Functionality now provided by IHierarchyService
 */
@Deprecated
public class OrgHierarchyFindByOrgIdCmd extends AbstractCmd<List<Integer>> {

	/* ================= Dependencies ================= */
	private IHierarchyService hier;

	/* ================= DI injection points ================= */
	@Inject
	public void setHier(IHierarchyService hier) {
		this.hier = hier;
	}

	private int orgId;

	/* Assume we want an ascending hierarchy in the default case */
	private Direction direction = Direction.ASCENDING;

	@Deprecated
	public OrgHierarchyFindByOrgIdCmd(int orgId) {
		this.orgId = orgId;
	}

	@Deprecated
	public OrgHierarchyFindByOrgIdCmd(int orgId, Direction direction) {
		this.orgId = orgId;
		this.direction = direction;
	}

	public int getOrgId() {
		return this.orgId;
	}

	public Direction getDirection() {
		return this.direction;
	}

	@Override
	public List<Integer> exec(CoreSession session) throws CommandException {

		/**
		 * The permission check was removed because it was decided that the list of orgIds that constitute the org hierarchy
		 * is not a security risk and simplifies authorization checks (which require the ascending org hierarchy).
		 */

		switch (this.direction) {

		case ASCENDING:
			try {

				return ImmutableList.copyOf(this.hier.getAscendingOrgs(this.orgId)).reverse();
			} catch (HierarchyNotFoundException hnfe) {

				return ImmutableList.of();
			}
		case DESCENDING:
			try {

				List<Integer> rv = new LinkedList<Integer>();
				rv.add(this.orgId);
				rv.addAll(this.hier.getAllChildOrgs(this.orgId));
				return ImmutableList.copyOf(rv);
			} catch (HierarchyNotFoundException hnfe) {

				/* Old API returned a list containing the requested org ID even if it doesn't exist... handle that here */
				return ImmutableList.of(this.orgId);
			}
		default:
			throw new CommandException(String.format("Unexpected direction value: %s", this.direction));
		}
	}
}
