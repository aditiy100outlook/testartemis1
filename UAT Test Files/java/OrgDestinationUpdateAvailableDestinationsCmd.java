package com.code42.org.destination;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.org.BackupOrg;
import com.code42.org.OrgFindByIdCmd;
import com.code42.org.destination.OrgDestinationDeleteCmd.OrgDestinationDeleteListCmd;
import com.code42.org.destination.OrgDestinationForceUpdateCmd.OrgDestinationForceUpdateListCmd;

/**
 * Configure which destinations an org allows; create as necessary.
 * 
 * The destinations to remove from this org must be provided in their own list.
 * 
 * WARNING: Additions or Removals always, ALWAYS apply to all existing clients. This may DELETE DATA. Do you know what
 * you're doing?
 */
public class OrgDestinationUpdateAvailableDestinationsCmd extends DBCmd<Void> {

	private final int orgId;
	private final List<Integer> ensureDestinationIds;
	private final List<Integer> removeDestinationIds;
	private final boolean inherit;

	private static ArrayList<Integer> emptyList = new ArrayList<Integer>();

	public OrgDestinationUpdateAvailableDestinationsCmd(int orgId, List<Integer> ensureDestinationIds,
			List<Integer> removeDestinationIds) {
		this(orgId, ensureDestinationIds, removeDestinationIds, false/* inherit */);
	}

	/**
	 * 
	 * @param orgId
	 * @param ensureDestinationIds - Destinations we want
	 * @param removeDestinationIds - Destinations we do not want
	 * @param inherit - true if this org is now inheriting destinations from parent org
	 */
	public OrgDestinationUpdateAvailableDestinationsCmd(int orgId, List<Integer> ensureDestinationIds,
			List<Integer> removeDestinationIds, boolean inherit) {
		super();
		this.orgId = orgId;
		this.ensureDestinationIds = (ensureDestinationIds == null) ? emptyList : ensureDestinationIds;
		this.removeDestinationIds = (removeDestinationIds == null) ? emptyList : removeDestinationIds;
		this.inherit = inherit;
	}

	public OrgDestinationUpdateAvailableDestinationsCmd(BackupOrg org, List<OrgDestination> ensureDestinations,
			List<OrgDestination> removeDestinations) {
		this(org, ensureDestinations, removeDestinations, false/* inherit */);
	}

	/**
	 * 
	 * @param org
	 * @param ensureDestinations - Destinations we want
	 * @param removeDestinations - Destinations we do not want
	 * @param inherit - true if this org is now inheriting destinations from parent org
	 */
	public OrgDestinationUpdateAvailableDestinationsCmd(BackupOrg org, List<OrgDestination> ensureDestinations,
			List<OrgDestination> removeDestinations, boolean inherit) {

		super();
		this.orgId = org.getOrgId();

		this.ensureDestinationIds = new ArrayList(ensureDestinations.size());
		for (OrgDestination dest : ensureDestinations) {
			this.ensureDestinationIds.add(dest.getDestinationId());
		}

		this.removeDestinationIds = new ArrayList(removeDestinations.size());
		for (OrgDestination dest : removeDestinations) {
			this.removeDestinationIds.add(dest.getDestinationId());
		}

		this.inherit = inherit;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionApp.AllOrg.ALL);
		this.ensureNotProtectedOrgAllowAdmin(this.orgId);
		this.ensureNotBusinessOrg(this.orgId);

		try {
			this.db.beginTransaction();

			if (this.inherit && this.ensureDestinationIds.size() > 0) {
				throw new CommandException("When updating to inherit, the ensureDestinationIds attribute must be empty.");
			}

			boolean disjoint = Collections.disjoint(this.ensureDestinationIds, this.removeDestinationIds);
			if (!disjoint) {
				throw new CommandException("Unable to set available destinations for orgId=" + this.orgId
						+ ". The ensure and removal lists must not overlap (nothing in common).");
			}

			final BackupOrg org = this.run(new OrgFindByIdCmd(this.orgId), session);

			// make sure they aren't inheriting
			if (org.getInheritDestinations()) {
				throw new CommandException("Unable to set available destinations for orgId=" + this.orgId + ", inheriting.");
			}

			this.run(new OrgDestinationForceUpdateListCmd(this.orgId, this.ensureDestinationIds), session);
			final boolean checkInheritance = this.inherit; // Check the parental destinations if we're inheriting... so we
																											// don't kill backups when we should not
			this.run(new OrgDestinationDeleteListCmd(this.orgId, this.removeDestinationIds, checkInheritance), session);

			this.db.commit();
		} finally {
			this.db.endTransaction();
		}

		return null;
	}
}
