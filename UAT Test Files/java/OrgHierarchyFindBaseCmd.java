package com.code42.hierarchy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.org.BackupOrg;
import com.code42.org.OrgFindByParentIdQuery;
import com.code42.org.OrgSso;
import com.code42.org.OrgSsoFindByOrgIdCmd;

/**
 * @deprecated Functionality now provided by IHierarchyService
 */
@Deprecated
public abstract class OrgHierarchyFindBaseCmd extends DBCmd<List<Integer>> {

	public enum Direction {
		ASCENDING, DESCENDING
	}

	@Override
	public abstract List<Integer> exec(CoreSession session) throws CommandException;

	/**
	 * Walk the tree from the named org ID up to the root, returning an unmodifiable list of orgs discovered along the way
	 * 
	 * @param originOrgId org ID to start the walk from
	 * @return a list of orgs representing the org hierarchy between the origin org and the root
	 * @throws CommandException if something goes bad
	 */
	public List<Integer> findAscending(int originOrgId, CoreSession session) throws CommandException {

		/*
		 * This is quite possibly the worst implementation of this algorithm we could come up with. This code requires a
		 * distinct requests for each step in the hierarchy. Alternate options require something considered problematic
		 * (stored procedures) or additional infrastructure.
		 * 
		 * Better option - A stored procedure within the database to handle traversal up the hierarchy. At least then we
		 * could reduce this monstrosity down to a single DB call. Best option - Install the entire org hierarchy into the
		 * space and just traverse that (with copious use of caching).
		 * 
		 * UPDATE: We get some modest improvement here by using the new OrgDtoFindBy commands... at least they check the
		 * cache in the space rather than going back to the DB every time. Still this is probably far from ideal.
		 */
		LinkedList<Integer> rv = new LinkedList<Integer>();
		Integer orgId = originOrgId;
		do {

			OrgSso dto = this.runtime.run(new OrgSsoFindByOrgIdCmd(orgId), session);

			/* OrgSsoFindByOrgIdCmd is now backed by CBO so it could return a null here */
			if (dto != null) {

				rv.addFirst(orgId);
				orgId = dto.getParentOrgId();
			} else {
				orgId = null;
			}
		} while (orgId != null);

		return Collections.unmodifiableList(rv);
	}

	/**
	 * Returns an unmodifiable list that includes the orgId argument and all child orgIds
	 * 
	 * @param orgId
	 * @return
	 * @throws CommandException
	 */
	public List<Integer> findDescending(int orgId) throws CommandException {

		List<Integer> authorizedOrgIds = new ArrayList<Integer>();
		authorizedOrgIds.add(orgId);
		this.findDescending(orgId, authorizedOrgIds);

		return Collections.unmodifiableList(authorizedOrgIds);
	}

	/**
	 * Port of logic that was previously defined in the old UserFindOrgHierarchy. That command also included logic to wrap
	 * the results up in an AuthorizedOrgs object, but to keep things general we just return a list of Integers from this
	 * command. If an instance of AuthorizedOrgs is needed you can use UserBuildAuthorizedOrgs; that command leverages
	 * this functionality when necessary.
	 */
	public void findDescending(int orgId, List<Integer> descendantOrgIds) throws CommandException {

		List<BackupOrg> children = this.db.find(new OrgFindByParentIdQuery(orgId));
		for (BackupOrg org : children) {
			descendantOrgIds.add(org.getOrgId());
			// Notice the recursion here
			this.findDescending(org.getOrgId(), descendantOrgIds);
		}
	}
}
