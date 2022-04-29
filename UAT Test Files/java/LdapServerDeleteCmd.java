/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.ldap;

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
import com.code42.org.OrgLdapServer;
import com.code42.org.OrgLdapServerFindByLdapServerQuery;
import com.code42.org.OrgSso;
import com.code42.org.OrgSsoFindByOrgIdCmd;
import com.code42.server.sync.SyncUtils;

/**
 * BE CAREFUL, This command will completely delete an LDAP Server.
 * 
 * This is executable only by someone with the SYSTEM_SETTINGS permission.
 */
public class LdapServerDeleteCmd extends DBCmd<Void> {

	public enum Error {
		IN_USE
	}

	private final int ldapServerId;

	public LdapServerDeleteCmd(int orgId) {
		this.ldapServerId = orgId;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.ensureNotCPCentral();

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		SyncUtils.isDeleteAuthorized(session);

		List<OrgLdapServer> used = this.db.find(new OrgLdapServerFindByLdapServerQuery(this.ldapServerId));
		if (used.size() > 0) {
			List<String> orgList = new ArrayList<String>();
			for (OrgLdapServer ols : used) {
				if (ols.getOrgId() == CpcConstants.Orgs.ADMIN_ID) {
					orgList.add("System");
				} else {
					OrgSso org = this.run(new OrgSsoFindByOrgIdCmd(ols.getOrgId()), this.auth.getAdminSession());
					orgList.add(org.getOrgName());
				}
			}
			throw new CommandException(Error.IN_USE, "ldapServerId {} is referenced by these orgs: {}", this.ldapServerId,
					orgList);
		}

		LdapServer ldapServer = this.db.find(new LdapServerFindByIdQuery(this.ldapServerId));
		if (ldapServer != null) {
			this.db.delete(new LdapServerDeleteQuery(ldapServer));
			CpcHistoryLogger.info(session, "LDAP:: deleted server: {}", ldapServer);
		}

		return null;
	}

	private static class LdapServerDeleteQuery extends DeleteQuery<Void> {

		private final LdapServer ldapServer;

		private LdapServerDeleteQuery(LdapServer ldapServer) {
			this.ldapServer = ldapServer;
		}

		@Override
		public void query(Session session) throws DBServiceException {
			session.delete(this.ldapServer);
		}

	}

}
