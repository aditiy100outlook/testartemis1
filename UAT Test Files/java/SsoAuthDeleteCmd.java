/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.ssoauth;

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
import com.code42.org.OrgSso;
import com.code42.org.OrgSsoAuth;
import com.code42.org.OrgSsoAuthFindByIdQuery;
import com.code42.org.OrgSsoFindByOrgIdCmd;
import com.code42.server.sync.SyncUtils;
import com.code42.ssoauth.SsoAuth;
import com.code42.ssoauth.SsoAuthFindByIdQuery;

/**
 * BE CAREFUL, This command will completely delete an SsoAuth row.
 * 
 * This is executable only by someone with the SYSTEM_SETTINGS permission.
 */
public class SsoAuthDeleteCmd extends DBCmd<Void> {

	public enum Error {
		IN_USE
	}

	private final int ssoAuthId;

	public SsoAuthDeleteCmd(int orgId) {
		this.ssoAuthId = orgId;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.ensureNotCPCentral();

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		SyncUtils.isDeleteAuthorized(session);

		List<OrgSsoAuth> used = this.db.find(new OrgSsoAuthFindByIdQuery(this.ssoAuthId));
		if (used.size() > 0) {
			List<String> orgList = new ArrayList<String>();
			for (OrgSsoAuth ols : used) {
				if (ols.getOrgId() == CpcConstants.Orgs.ADMIN_ID) {
					orgList.add("System");
				} else {
					OrgSso org = this.run(new OrgSsoFindByOrgIdCmd(ols.getOrgId()), this.auth.getAdminSession());
					orgList.add(org.getOrgName());
				}
			}
			throw new CommandException(Error.IN_USE, "ssoAuthId {} is referenced by these orgs: {}", this.ssoAuthId, orgList);
		}

		SsoAuth ssoAuth = this.db.find(new SsoAuthFindByIdQuery(this.ssoAuthId));
		if (ssoAuth != null) {
			this.db.delete(new SsoAuthDeleteQuery(ssoAuth));
			CpcHistoryLogger.info(session, "SsoAuth:: deleted sso auth: {}", ssoAuth);
		}

		return null;
	}

	private static class SsoAuthDeleteQuery extends DeleteQuery<Void> {

		private final SsoAuth ssoAuth;

		private SsoAuthDeleteQuery(SsoAuth s) {
			this.ssoAuth = s;
		}

		@Override
		public void query(Session session) throws DBServiceException {
			session.delete(this.ssoAuth);
		}

	}

}
