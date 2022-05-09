package com.code42.computer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.hierarchy.HierarchyException;
import com.code42.core.hierarchy.IHierarchyService;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;
import com.code42.org.BackupOrg;
import com.code42.org.OrgFindByIdCmd;
import com.google.inject.Inject;

public class ComputerSsoFindMultipleByOrgCmd extends AbstractCmd<Map<Long, ComputerSso>> {

	private static final Logger log = Logger.getLogger(ComputerSsoFindMultipleByOrgCmd.class);

	@Inject
	private IHierarchyService hierarchyService;

	private final Set<Integer> orgIds;

	public ComputerSsoFindMultipleByOrgCmd(Set<Integer> orgIds) {
		super();
		this.orgIds = orgIds;
	}

	@Override
	public Map<Long, ComputerSso> exec(CoreSession session) throws CommandException {

		final Map<Long, ComputerSso> rv = new HashMap<Long, ComputerSso>();

		for (Integer orgId : this.orgIds) {
			Set<Long> guids = null;
			try {
				guids = this.hierarchyService.getGuidsForOrg(orgId);
			} catch (HierarchyException he) {
				// This was added for the situation where a new org has been created, but not yet committed,
				// hence the hierarchy does not yet exist.
				// The known situation this occurs in is during registration of a new LDAP user that includes the
				// creation of a new org. We don't want to commit the org or the user until the entire registration
				// is finished, but we need to know about the user's org and any computers in it.
				BackupOrg org = this.run(new OrgFindByIdCmd(orgId), session);
				if (org == null) {
					log.warn("Org Not Found in CHS: {}", orgId);
				} else {
					log.info("Assuming this is a brand new org which has not yet been committed: {}", org);
				}
				guids = new HashSet(); // This is probably a brand new org with no computers. Set it to an empty list.
			}

			if (!guids.isEmpty()) {
				Map<Long, ComputerSso> computers = this.run(new ComputerSsoFindMultipleByGuidCmd(guids), session);
				rv.putAll(computers);
			}
		}

		return rv;
	}
}
