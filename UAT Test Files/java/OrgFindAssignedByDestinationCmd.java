package com.code42.org.destination;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.executor.jsr166.Arrays;
import com.code42.org.BackupOrg;
import com.code42.org.OrgFindAllQuery;
import com.code42.server.destination.Destination;

/**
 * Find the orgs that have the given Destination in their available list, either directly or through inheritance.
 * 
 * TONY re-do this algorithm; it's horrible, but it's late and I'm tired.
 */
public class OrgFindAssignedByDestinationCmd extends DBCmd<List<BackupOrg>> {

	private final int destinationId;

	public OrgFindAssignedByDestinationCmd(int destinationId) {
		this.destinationId = destinationId;
	}

	@Override
	public List<BackupOrg> exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		Set<BackupOrg> orgsAssigned = new HashSet<BackupOrg>();

		List<BackupOrg> allOrgs = this.db.find(new OrgFindAllQuery());
		for (BackupOrg org : allOrgs) {
			List<Destination> destinations = this.run(new DestinationFindAvailableByOrgCmd(org.getOrgId()), session);
			for (Destination d : destinations) {
				if (this.destinationId == d.getDestinationId().intValue()) {
					orgsAssigned.add(org);
				}
			}
		}
		return Arrays.asList(orgsAssigned.toArray());
	}
}
