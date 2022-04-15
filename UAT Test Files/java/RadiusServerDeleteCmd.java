/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.radius;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Session;

import com.backup42.CpcConstants;
import com.backup42.common.perm.C42PermissionPro;
import com.backup42.history.CpcHistoryLogger;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.DeleteQuery;
import com.code42.core.impl.DBCmd;
import com.code42.org.OrgRadiusServer;
import com.code42.org.OrgRadiusServerFindByRadiusServerQuery;
import com.code42.org.OrgSso;
import com.code42.org.OrgSsoFindByOrgIdCmd;
import com.code42.server.sync.SyncUtils;
import com.code42.user.RadiusServer;

/**
 * BE CAREFUL
 * 
 * This command will completely delete a RADIUS Server.
 * 
 * This is executable only by a System Administrator.
 */
public class RadiusServerDeleteCmd extends DBCmd<Void> {

	public enum Error {
		IN_USE
	}

	private final int radiusServerId;

	public RadiusServerDeleteCmd(int orgId) {
		this.radiusServerId = orgId;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.ensureNotCPCentral();

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);
		SyncUtils.isDeleteAuthorized(session);

		List<OrgRadiusServer> used = this.db.find(new OrgRadiusServerFindByRadiusServerQuery(this.radiusServerId));
		if (used.size() > 0) {
			List<String> orgList = new ArrayList<String>();
			for (OrgRadiusServer ols : used) {
				if (ols.getOrgId() == CpcConstants.Orgs.ADMIN_ID) {
					orgList.add("System");
				} else {
					OrgSso org = this.run(new OrgSsoFindByOrgIdCmd(ols.getOrgId()), this.auth.getAdminSession());
					orgList.add(org.getOrgName());
				}
			}
			throw new CommandException(Error.IN_USE, "radiusServerId {} is referenced by these orgs: {}",
					this.radiusServerId, orgList);
		}

		RadiusServer radiusServer = this.db.find(new RadiusServerFindByIdQuery(this.radiusServerId));
		if (radiusServer != null) {
			this.db.delete(new RadiusServerDeleteQuery(radiusServer));
			CpcHistoryLogger.info(session, "RADIUS:: deleted server: {}", radiusServer);
		}

		return null;
	}

	/**
	 * Delete query
	 */
	private static class RadiusServerDeleteQuery extends DeleteQuery<Void> {

		private final RadiusServer radiusServer;

		private RadiusServerDeleteQuery(RadiusServer radiusServer) {
			this.radiusServer = radiusServer;
		}

		@Override
		public void query(Session session) throws DBServiceException {
			session.delete(this.radiusServer);
		}

	}

}
