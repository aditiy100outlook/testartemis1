package com.code42.org.destination;

import java.util.Collections;
import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.code42.org.BackupOrg;
import com.code42.org.Org;
import com.code42.org.OrgFindAllParentsCmd;

/**
 * Find the Destinations available to this org- they may be assigned directly, inherited or the system defaults if
 * nothing else applies.
 */
public class OrgDestinationFindAvailableByOrgCmd extends DBCmd<List<OrgDestination>> {

	private final int orgId;
	private final boolean inheritedOnly;

	public OrgDestinationFindAvailableByOrgCmd(int orgId) {
		this(orgId, false);
	}

	public OrgDestinationFindAvailableByOrgCmd(int orgId, boolean inheritedOnly) {
		super();
		this.orgId = orgId;
		this.inheritedOnly = inheritedOnly;
	}

	@Override
	public List<OrgDestination> exec(CoreSession session) throws CommandException {
		if (this.orgId != session.getUser().getOrgId()) {
			this.run(new IsOrgManageableCmd(this.orgId, C42PermissionApp.Org.READ), session);
		}

		final List<OrgDestination> destinations = this.getDestinations(this.orgId, !this.inheritedOnly, session);
		return destinations;
	}

	/**
	 * Searches up the org tree for the first org with configured destinations. If we do not find any in the org tree then
	 * we'll use the system defaults.
	 * 
	 * Inclusive checks the org itself as well. Exclusive searches only include the parents of the specified org and the
	 * server's defaults.
	 */
	private List<OrgDestination> getDestinations(int orgId, boolean inclusive, CoreSession session)
			throws CommandException {

		List<OrgDestination> destinations = Collections.EMPTY_LIST;

		Org org = this.findOrgDeclaringDestinations(orgId, inclusive);
		if (org != null) {
			destinations = this.db.find(new OrgDestinationFindByOrgQuery(org.getOrgId()));
		}

		// if we haven't found any destinations AND the org is inheriting from its parent then we'll provide the system
		// destinations
		if (destinations.isEmpty()) {
			boolean inheritSystem = false;
			if (org != null && org instanceof BackupOrg) {
				inheritSystem = ((BackupOrg) org).getInheritDestinations();
			}

			if (org == null || inheritSystem) {
				destinations = this.run(new OrgDestinationFindDefaultDestinationsCmd(), session);
			}
		}

		return destinations;
	}

	/**
	 * Find the org that declares the destinations this org should be using. Return null if none found.
	 */
	private Org findOrgDeclaringDestinations(int orgId, boolean inclusive) {

		if (orgId < 1) {
			return null;
		}

		Org declaringOrg = null;

		List<BackupOrg> orgs = CoreBridge.runNoException(new OrgFindAllParentsCmd(orgId, inclusive));
		for (Org org : orgs) {
			if (((BackupOrg) org).getInheritDestinations()) {
				continue;
			}

			declaringOrg = org;
			break;
		}

		return declaringOrg;
	}
}
