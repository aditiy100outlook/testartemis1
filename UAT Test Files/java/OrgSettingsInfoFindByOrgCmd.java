package com.code42.org;

import org.hibernate.Session;

import com.code42.core.CommandException;
import com.code42.core.UnsupportedRequestException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;

/**
 * Merges multiple OrgSettings rows into an OrgSettingsInfo instance. Taken from SettingsServices. Lazily creates an
 * OrgSettings row if the Org exists, but the corresponding OrgSettings row does not.
 * <p>
 * Note: Use the Builder inner class to configure this command.
 */
public class OrgSettingsInfoFindByOrgCmd extends DBCmd<OrgSettingsInfo> {

	private static Object[] monitor = new Object[0];

	private Builder data;

	private OrgSettingsInfoFindByOrgCmd(Builder builder) {
		this.data = builder;
	}

	@Override
	public OrgSettingsInfo exec(CoreSession session) throws CommandException {

		this.authorize(session);

		//
		// We are setup now. Start loading up the object.
		//

		OrgSettingsInfo info = new OrgSettingsInfo();
		if (this.data.org == null) {
			// A new OSI object that inherits from the system settings
			OrgSettings os = this.db.find(new OrgSettingsFindByOrgIdQuery(1));
			info.setNullFields(os, true);
			return info;
		}

		Org tempOrg = this.data.org;
		boolean inherited = this.data.newOrg; // new orgs inherit from the parent
		while (tempOrg != null) {
			if (tempOrg.getOrgId() != null) {
				OrgSettings os = this.db.find(new OrgSettingsFindByOrgIdQuery(tempOrg.getOrgId()));
				if (os == null) {

					// Synchronize this block to avoid a race condition where two threads try to create the OrgSettings object
					// at the same time.
					synchronized (monitor) {
						os = this.db.find(new OrgSettingsFindByOrgIdQuery(tempOrg.getOrgId()));
						if (os == null) {
							os = new OrgSettings();
							os.setOrgId(tempOrg.getOrgId());
							// Save a new row for this org that inherits everything
							this.db.create(new OrgSettingsCreateQuery(os));
						}
					}
				}
				info.setNullFields(os, inherited);
			}
			if (tempOrg == null || tempOrg.getParentOrgId() == null) {
				tempOrg = null;
			} else {
				tempOrg = this.db.find(new OrgFindByIdQuery(tempOrg.getParentOrgId()));
			}

			inherited = true;
		}

		if (this.data.org.getOrgId() != null && this.data.org.getOrgId() > 1) {
			OrgSettings os = this.db.find(new OrgSettingsFindByOrgIdQuery(1));
			info.setNullFields(os, true);
		}

		return info;
	}

	private void authorize(CoreSession session) throws UnauthorizedException, CommandException {

		// Authorize access to this data
		this.runtime.run(new IsOrgManageableCmd(this.data.orgId, C42PermissionApp.Org.READ), session);

		// Short-circuit a request for orgId 1
		// if (this.data.orgId <= 1) {
		// throw new UnsupportedRequestException("Invalid OrgId: " + this.data.orgId);
		// }

		//
		// Note: If data.newOrg is true then...
		// a) if the org is null then it is a new top-level org
		// b) if the org is not null then this is for a new child org
		//
		// if (this.data.orgId > 1 && this.data.org == null) {
		if (this.data.org == null) {
			this.data.org = this.db.find(new OrgFindByIdQuery(this.data.orgId));
			if (this.data.org == null) {
				throw new UnsupportedRequestException("Org not found: " + this.data.orgId);
			}
		}

		if (this.data.org.getOrgId() == null) {
			throw new UnsupportedRequestException("Org does not have an id: " + this.data.org);
		}

	}

	/**
	 * Builder class - Builds the input data. Use only one of the main builder methods or an exception will be thrown.
	 */
	public static class Builder {

		int orgId;
		Org org;
		boolean newOrg = false; // If true, the org is the parent org
		boolean done = false;

		public Builder() {
		}

		public Builder org(Org org) {
			if (this.done) {
				throw new IllegalArgumentException(
						"Programmer error. org() cannot be used in conjunction with other builder methods");
			}
			this.org = org;
			this.orgId = org.getOrgId();
			this.newOrg = false;
			this.done = true;
			return this;
		}

		public Builder orgId(int orgId) {
			if (this.done) {
				throw new IllegalArgumentException(
						"Programmer error. orgId() cannot be used in conjunction with other builder methods");
			}
			this.orgId = orgId;
			this.newOrg = false;
			this.done = true;
			return this;
		}

		/**
		 * @see SettingsServices.getNewChildOrgSettingsInfo(parentOrg)
		 */
		public Builder newChildOrgSettingsInfo(Org parentOrg) {
			if (this.done) {
				throw new IllegalArgumentException(
						"Programmer error. newChildOrgSettingsInfo(parentOrg) cannot be used in conjunction with other builder methods");
			}
			this.org = parentOrg;
			this.newOrg = true;
			this.done = true;
			return this;
		}

		/**
		 * @see SettingsServices.getNewTopLevelOrgSettingsInfo(parentOrg)
		 */
		public Builder newTopLevelOrgSettingsInfo() {
			if (this.done) {
				throw new IllegalArgumentException(
						"Programmer error. newTopLevelOrgSettingsInfo cannot be used in conjunction with other builder methods");
			}
			this.org = null;
			this.newOrg = true;
			this.done = true;
			return this;
		}

		public void validate() throws CommandException {
			if (this.org == null && this.orgId < 1) {
				if (this.newOrg) {
					// This is for a new top-level org
				} else {
					throw new CommandException("Illegal argument values. orgId:" + this.orgId + " org:" + this.org);
				}
			}
		}

		public OrgSettingsInfoFindByOrgCmd build() throws CommandException {
			this.validate();
			return new OrgSettingsInfoFindByOrgCmd(this);
		}
	}

	/**
	 * Locked impl.
	 */
	private static class OrgSettingsFindByOrgIdQuery extends FindQuery<OrgSettings> {

		private int orgId;

		public OrgSettingsFindByOrgIdQuery(int orgId) {
			this.orgId = orgId;
		}

		@Override
		public OrgSettings query(Session session) throws DBServiceException {
			// This query MUST be allowed to return null, if not found; uses 'get' instead of 'load'
			return (OrgSettings) session.get(OrgSettings.class, this.orgId);
		}
	}
}
