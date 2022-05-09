package com.code42.org;

import java.util.List;

import org.hibernate.Query;
import org.hibernate.Session;

import com.code42.archiverecord.ArchiveRecordDeleteByOrgCmd;
import com.code42.archiverecord.ArchiveSummaryDeleteByOrgCmd;
import com.code42.archiverecord.ArchiveSummaryRollupDeleteByOrgCmd;
import com.code42.auth.DeleteAuthCheckCmd;
import com.code42.auth.DeleteAuthCheckCmd.CheckType;
import com.code42.core.CommandException;
import com.code42.core.annotation.CoreNamedQuery;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.DeleteQuery;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.org.destination.OrgDestination;
import com.code42.org.destination.OrgDestinationDeleteCmd;
import com.code42.org.destination.OrgDestinationFindByOrgQuery;
import com.code42.utils.LangUtils;

/**
 * BE VERY CAREFUL!!!
 * 
 * This command will completely delete and remove all references to an org, all its children, users, computers,
 * settings, history, archives, etc.
 * 
 * It is EXTREMELY destructive and executable only by a System Administrator.
 */
public class OrgDeleteCmd extends DBCmd<Void> {

	private static final Logger log = Logger.getLogger(OrgDeleteCmd.class);

	private final int orgId;

	public OrgDeleteCmd(int orgId) {
		this.orgId = orgId;
	}

	public OrgDeleteCmd(Org org) {
		this(org.getOrgId());
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.run(new DeleteAuthCheckCmd(CheckType.ORG, this.orgId), session);

		try {
			this.db.beginTransaction();
			BackupOrg org = this.db.find(new OrgFindByIdQuery(this.orgId));
			if (org != null) {
				log.info("DELETE:: ORG: {}", org);
				this.deleteOrg(org, session);

				this.db.afterTransaction(new OrgPublishDeleteCmd(org), session);
			}

			this.db.commit();

		} finally {
			this.db.endTransaction();
		}

		return null;
	}

	/**
	 * Delete an org, including all its children, regardless of their state (active or inactive)
	 * 
	 * @param org
	 * @param session
	 * @throws CommandException
	 */
	private void deleteOrg(Org org, CoreSession session) throws CommandException {
		List<BackupOrg> childOrgs = CoreBridge.find(new OrgFindByParentIdQuery(org.getOrgId()));
		if (LangUtils.hasElements(childOrgs)) {
			for (Org child : childOrgs) {
				this.deleteOrg(child, session);
			}
		}
		this.deleteOrg(org.getOrgId(), session);
	}

	private void deleteOrg(int orgId, CoreSession session) throws CommandException {

		this.run(new ArchiveRecordDeleteByOrgCmd(orgId), session);
		this.run(new ArchiveSummaryDeleteByOrgCmd(orgId), session);
		this.run(new ArchiveSummaryRollupDeleteByOrgCmd(orgId), session);
		this.run(new OrgRemoveFromSocialNetworkCmd(orgId), session);
		this.run(new OrgRemoveAllComputersCmd(orgId), session);
		this.run(new OrgRemoveAllUsersCmd(orgId), session);
		this.run(new OrgRemoveAllReferencesCmd(orgId), session);
		this.run(new OrgDestinationDeleteByOrgCmd(orgId), session);

		Org org = this.db.find(new OrgFindByIdQuery(orgId));
		if (org != null) {
			this.db.delete(new OrgDeleteQuery(org));
		}
	}

	/**
	 * Private.
	 */
	@CoreNamedQuery(name = "OrgDeleteQuery", query = "delete from Org o where o.orgId = :orgId")
	private static class OrgDeleteQuery extends DeleteQuery<Void> {

		private final Org org;

		private OrgDeleteQuery(Org org) {
			this.org = org;
		}

		@Override
		public void query(Session session) throws DBServiceException {
			if (this.org != null) {
				final Query q = this.getNamedQuery(session);
				q.setInteger("orgId", this.org.getOrgId());
				q.executeUpdate();
			}
		}
	}

	/**
	 * Private. This command is not remotely safe. It's located here because it is simply not relevant outside the context
	 * of deleting an entire org.
	 */
	private static class OrgDestinationDeleteByOrgCmd extends DBCmd<Void> {

		private final int orgId;

		public OrgDestinationDeleteByOrgCmd(int orgId) {
			super();
			this.orgId = orgId;
		}

		@Override
		public Void exec(CoreSession session) throws CommandException {
			try {
				this.db.beginTransaction();
				List<OrgDestination> dests = this.db.find(new OrgDestinationFindByOrgQuery(this.orgId));
				for (OrgDestination dest : dests) {
					boolean checkInheritance = false; // not sure false is right, but it's the historical behavior here
					this.run(new OrgDestinationDeleteCmd(dest, checkInheritance), session);
				}
				this.db.commit();
			} finally {
				this.db.endTransaction();
			}
			return null;
		}
	}
}
