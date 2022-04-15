package com.code42.org;

import java.util.ArrayList;
import java.util.List;

import com.backup42.CpcConstants;
import com.backup42.common.OrgType;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.OrgDef;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.org.destination.OrgDestination;
import com.code42.org.destination.OrgDestinationFindAvailableByOrgCmd;
import com.code42.org.destination.OrgDestinationUpdateAvailableDestinationsCmd;
import com.code42.org.destination.OrgDestinationUpdateInheritanceCmd;
import com.code42.utils.LangUtils;
import com.code42.utils.UniqueId;
import com.code42.utils.option.None;

/**
 * Provides an authorization wrapper around the database access. The Query class is currently public so that it can be
 * created without the command (authorization check) if necessary. We may rethink this openness.
 */
public class OrgCreateCmd extends OrgCreateBaseCmd {

	public enum Error {
		ORG_NAME_MISSING, ORG_DUPLICATE, PARENT_ORG_BLOCKED, PARENT_ORG_NOT_ACTIVE, ORG_NAME_TOO_SHORT
	}

	public OrgCreateCmd(Builder data) {
		super(data);
	}

	/* OrgCreateCmd _always_ returns an ENTERPRISE org */
	@Override
	public OrgType getOrgType() {
		return OrgType.ENTERPRISE;
	}

	@Override
	public Org exec(CoreSession session) throws CommandException {

		this.parentOrg = this.getParentOrg();

		if (this.parentOrg == null) {
			// We are creating a top-level org

			if (!this.env.isMaster()) {
				throw new CommandException("Trying to save a top-level org to a non-master cluster");
			}

			// Make sure user has elevated permission for all orgs
			this.auth.isAuthorized(session, C42PermissionApp.AllOrg.CREATE);
		} else { // We are creating a sub-org

			this.ensureNotHostedOrg(this.parentOrg.getOrgId(), session);

			// Authorize the user for permission to add children to this org
			this.runtime.run(new IsOrgManageableCmd(this.parentOrg.getOrgId(), C42PermissionApp.Org.CREATE), session);
		}

		if ((!this.env.isBusinessCluster() && !this.env.isConsumerCluster())
				&& this.runtime.run(new OrgFindAllByNameCmd(this.data.orgName), session).size() > 0) {
			throw new CommandException(Error.ORG_DUPLICATE, "Duplicate Organization Name");
		}

		BackupOrg org = new BackupOrg();
		if (this.data.hosted.get()) {
			org = new HostedParentOrg();
			long placeholderGuid = UniqueId.generateId();
			org.setMasterGuid(placeholderGuid * -1);
		}
		final Org result = super.createOrg(session, org);

		// Hosted orgs shouldn't have any offered destinations and shouldn't inherit.
		if (this.data.hosted.get()) {
			this.runtime.run(new OrgDestinationUpdateInheritanceCmd(org.getOrgId(), false), session);

			// Remove all currently offered destinations.
			final ArrayList<Integer> deleteList = new ArrayList<Integer>();
			final List<OrgDestination> orgDestinations = this.runtime.run(new OrgDestinationFindAvailableByOrgCmd(org
					.getOrgId()), session);
			for (OrgDestination orgDestination : orgDestinations) {
				deleteList.add(orgDestination.getDestinationId());
			}

			this.runtime.run(new OrgDestinationUpdateAvailableDestinationsCmd(org.getOrgId(), new ArrayList<Integer>(),
					deleteList), session);
		}

		return result;
	}

	public static class Builder extends OrgCreateBaseCmd.Builder {

		public Builder(String orgName) throws BuilderException {
			super(orgName);
		}

		@Override
		protected void validate() throws BuilderException {
			if (!LangUtils.hasValue(this.orgName)) {
				throw new BuilderException(Error.ORG_NAME_MISSING, "Org Name must be provided");
			}

			if (!(this.parentOrgId instanceof None) && this.parentOrgId.get() <= 1) {
				throw new BuilderException(Error.PARENT_ORG_BLOCKED, "Children of org not allowed");
			}

			if (!(this.parentOrgUid instanceof None) && OrgDef.ADMIN.getOrgUid().equals(this.parentOrgUid.get())) {
				throw new BuilderException(Error.PARENT_ORG_BLOCKED, "Children of org not allowed");
			}

			/* We don't allow for the creation of orgs underneath the CrashPlan org */
			if (!(this.parentOrgId instanceof None) && this.parentOrgId.get() == CpcConstants.Orgs.CP_ID) {
				throw new BuilderException(Error.PARENT_ORG_BLOCKED, "Children of org not allowed");
			}

			if (LangUtils.length(this.orgName) < 3) {
				throw new BuilderException(Error.ORG_NAME_TOO_SHORT, "Org name must be at least three characters long");
			}
		}

		@Override
		public OrgCreateCmd build() throws BuilderException {
			this.validate();
			return new OrgCreateCmd(this);
		}
	}
}
