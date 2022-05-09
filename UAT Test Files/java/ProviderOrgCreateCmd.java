package com.code42.org;

import com.backup42.CpcConstants;
import com.backup42.common.OrgType;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.OrgDef;
import com.code42.core.auth.impl.CoreSession;
import com.code42.utils.LangUtils;
import com.code42.utils.option.None;

/**
 * Creates an org underneath the given HostedParentOrg. This class is specific to Providers and is called only by the
 * system itself when orgs are synched from the Dependent Master. If the incoming org has no parent, it's created as a
 * child directly underneath the HostedParentOrg. If it has a parent, its position will then be relative to that
 * underneath the HostedParentOrg. In effect, the HostedParentOrg is a "root" for all orgs synched from the related
 * Dependent Master.
 */
public class ProviderOrgCreateCmd extends OrgCreateBaseCmd {

	public enum Error {
		ORG_NAME_MISSING, //
		ORG_DUPLICATE, //
		PARENT_ORG_BLOCKED, //
		PARENT_ORG_NOT_ACTIVE, //
		ORG_NAME_TOO_SHORT, //
		BAD_REMOTE_GUID, //
		SYSTEM
	}

	private final HostedParentOrg hostedParentOrg;

	public ProviderOrgCreateCmd(Builder data) {
		super(data);
		this.hostedParentOrg = data.hostedParentOrg;
	}

	@Override
	public OrgType getOrgType() {
		return OrgType.ENTERPRISE;
	}

	@Override
	public Org exec(CoreSession session) throws CommandException {

		this.auth.isSysadmin(session); // sysadmin only

		if (!this.env.isMaster()) {
			throw new CommandException("Creating children under HostedParentOrgs is a Master function only");
		}

		this.parentOrg = this.getParentOrg();

		if (this.parentOrg == null) {
			// We are creating a top-level org underneath the hostedParentOrg
			this.parentOrg = new OrgSso(this.hostedParentOrg);
		}

		BackupOrg org = new BackupOrg();
		org.setMasterGuid(this.hostedParentOrg.getMasterGuid());

		final Org result = super.createOrg(session, org);

		return result;
	}

	public static class Builder extends OrgCreateBaseCmd.Builder {

		private final HostedParentOrg hostedParentOrg;

		public Builder(String orgName, HostedParentOrg hostedParentOrg) throws BuilderException {
			super(orgName);
			this.hostedParentOrg = hostedParentOrg;
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

			if (this.hostedParentOrg == null) {
				throw new BuilderException(Error.SYSTEM, "SYSTEM ERROR: No HostedParentOrg provided");
			}
		}

		@Override
		public ProviderOrgCreateCmd build() throws BuilderException {
			this.validate();
			return new ProviderOrgCreateCmd(this);
		}
	}
}
