package com.code42.org.destination;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.backup42.CpcConstants;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.org.BackupOrg;
import com.code42.org.OrgFindByIdQuery;
import com.code42.org.OrgUpdateQuery;
import com.code42.server.destination.Destination.Type;
import com.code42.server.destination.DestinationFindByIdCmd;
import com.code42.server.destination.IDestination;

/**
 * Adjust the org's inherit destination status.
 * 
 * If newly NOT inheriting: create the same destination rows the org had before in order to ensure no data loss.
 * 
 * If newly inheriting: remove the existing destination rows first. Dangerous? yes.
 */
public class OrgDestinationUpdateInheritanceCmd extends DBCmd<Void> {

	private final int orgId;
	private final boolean inherit;

	public OrgDestinationUpdateInheritanceCmd(int orgId, boolean inherit) {
		super();
		this.orgId = orgId;
		this.inherit = inherit;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionApp.AllOrg.ALL);
		this.ensureNotProtectedOrg(this.orgId);
		this.ensureNotBusinessOrg(this.orgId);

		try {
			this.db.beginTransaction();

			final BackupOrg org = this.db.find(new OrgFindByIdQuery(this.orgId));

			// validate that the current state is changing; this is a developer safety check- you should be calling this class
			// with intent to modify
			if (this.inherit == org.getInheritDestinations()) {
				throw new CommandException("Org inherit setting MUST change.");
			}

			if (this.orgId == CpcConstants.Orgs.ADMIN_ID) {
				throw new CommandException("Can't update destination inheritance for orgId=" + CpcConstants.Orgs.ADMIN_ID);
			}

			if (org.isConsumer()) {
				throw new CommandException("Can't update consumer org.");
			}

			// Don't allow hosted organizations to inherit destinations from parent.
			if (this.inherit && org.isHostedParent()) {
				throw new CommandException("Hosted organizations can't inherit destinations.");
			}

			if (this.inherit) {
				// changing to inheriting means that we are losing our own destination list
				final List<OrgDestination> remove = this.db.find(new OrgDestinationFindByOrgQuery(this.orgId));
				this.run(new OrgDestinationUpdateAvailableDestinationsCmd(org, Collections.EMPTY_LIST, remove, this.inherit),
						session);

				// update the setting last; we cannot adjust available destinations for orgs that are inheriting
				this.updateOrgSetting(org, session);

			} else {
				// we're moving away from inheritance. the first step is to make sure we have the same destinations available
				// that we did before in order to prevent data loss
				final List<OrgDestination> available = this.run(new OrgDestinationFindAvailableByOrgCmd(this.orgId), session);
				List<OrgDestination> filteredAvailable = new ArrayList<OrgDestination>();

				// If the org is hosted then it cannot use provider destinations
				if (org.isHostedParent()) {
					for (OrgDestination dest : available) {
						IDestination d = this.run(new DestinationFindByIdCmd(dest.getDestinationId()), session);
						if (d.getType() != Type.PROVIDER) {
							filteredAvailable.add(dest);
						}
					}
				} else {
					filteredAvailable = available;
				}

				// update the setting first in order to be able to adjust the destination list
				this.updateOrgSetting(org, session);

				this.run(new OrgDestinationUpdateAvailableDestinationsCmd(org, filteredAvailable, Collections.EMPTY_LIST,
						this.inherit), session);
			}

			this.db.commit();
		} finally {
			this.db.endTransaction();
		}

		return null;
	}

	/**
	 * Update the org's inheritance setting.
	 */
	private void updateOrgSetting(BackupOrg org, CoreSession session) throws CommandException {
		// This should be the only place in the entire system that does this.
		org.setInheritDestinations(this.inherit);
		this.db.update(new OrgUpdateQuery(org));
	}
}
