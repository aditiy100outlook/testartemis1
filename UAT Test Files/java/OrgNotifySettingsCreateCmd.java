package com.code42.org;

import org.hibernate.Session;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.CreateQuery;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;

public class OrgNotifySettingsCreateCmd extends DBCmd<OrgNotifySettings> {

	private static final Logger log = LoggerFactory.getLogger(OrgNotifySettingsCreateCmd.class.getName());

	private final int orgId;

	public OrgNotifySettingsCreateCmd(int orgId) {
		this.orgId = orgId;
	}

	@Override
	public OrgNotifySettings exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionApp.Org.UPDATE_BASIC, this.orgId);

		log.info("Creating OrgNotifySettings instance for orgId: " + this.orgId);

		OrgNotifySettings ons = new OrgNotifySettings();
		ons.setOrgId(this.orgId);
		this.db.create(new OrgNotifySettingsCreateQuery(ons));
		return ons;
	}

	private class OrgNotifySettingsCreateQuery extends CreateQuery<OrgNotifySettings> {

		private final OrgNotifySettings orgNotifySettings;

		public OrgNotifySettingsCreateQuery(OrgNotifySettings orgNotifySettings) {
			this.orgNotifySettings = orgNotifySettings;
		}

		@Override
		public OrgNotifySettings query(Session session) throws DBServiceException {

			if (this.orgNotifySettings != null) {
				session.save(this.orgNotifySettings);
				return this.orgNotifySettings;
			} else {
				throw new DBServiceException("Error Creating OrgNotifySettings; null argument: ");
			}

		}
	}
}
