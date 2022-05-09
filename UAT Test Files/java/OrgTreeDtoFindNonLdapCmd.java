package com.code42.org;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

/**
 * Returns of list of OrgTreeDto instances that do not use LDAP.
 */
public class OrgTreeDtoFindNonLdapCmd extends DBCmd<Collection<OrgTreeDto>> {

	@Override
	public Collection<OrgTreeDto> exec(CoreSession session) throws CommandException {

		/*
		 * Find all orgs and put them into a tree so we can visit them to determine the non-ldap orgs.
		 */
		OrgTree<OrgTreeDto> tree = this.runtime.run(new OrgTreeFindAllCmd(), session);

		/*
		 * This returns 0 to n objects for each org. Orgs with none inherit, orgs with 1 or more are set to non-ldap or
		 * ldap. A non-ldap (zero) ldapServerId will never be mixed with non-zero for an org.
		 */
		Map<Integer, List<Integer>> orgLdapMap = this.db.find(new OrgLdapMapFindAllQuery());
		// Wrap the above map with a boolean valued map
		Map<Integer, Boolean> ldapFlags = new OrgLdapMapFindAllQuery.OrgLdapFlagMap(orgLdapMap);

		// Use a visitor to visit each node in the tree
		OrgLdapUpwardVisitor visitor = new OrgLdapUpwardVisitor(ldapFlags);
		Iterator<OrgTreeDto> iter = tree.iterator();
		// Iterator proceeds in a downward fashion
		while (iter.hasNext()) {
			OrgTreeDto dto = iter.next();
			// visit the ancestors, to see who
			dto.acceptUpwardVisitor(visitor);
		}

		return visitor.getNonLdapOrgs();
	}

}
