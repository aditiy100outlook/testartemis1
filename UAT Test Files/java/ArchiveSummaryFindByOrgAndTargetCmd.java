package com.code42.archivesummary;

import org.hibernate.Query;
import org.hibernate.Session;

import com.code42.archiverecord.ArchiveSummary;
import com.code42.core.CommandException;
import com.code42.core.annotation.CoreNamedQuery;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;

public class ArchiveSummaryFindByOrgAndTargetCmd extends DBCmd<ArchiveSummary> {

	int orgId;
	long targetComputerId;

	public ArchiveSummaryFindByOrgAndTargetCmd(int orgId, long targetComputerId) {
		this.orgId = orgId;
		this.targetComputerId = targetComputerId;
	}

	@Override
	public ArchiveSummary exec(CoreSession session) throws CommandException {

		this.runtime.run(new IsOrgManageableCmd(this.orgId, C42PermissionApp.Org.READ), session);
		return this.db.find(new ArchiveSummaryFindByOrgAndTargetQuery(this.orgId, this.targetComputerId));
	}

	@CoreNamedQuery(name = "ArchiveSummaryFindByOrgAndTargetComputer", query = "from ArchiveSummary a where a.orgId = :orgId and a.targetComputerId = :targetComputerId and a.userId is null")
	public static class ArchiveSummaryFindByOrgAndTargetQuery extends FindQuery<ArchiveSummary> {

		int orgId;
		long targetComputerId;

		public ArchiveSummaryFindByOrgAndTargetQuery(int orgId, long targetComputerId) {
			this.orgId = orgId;
			this.targetComputerId = targetComputerId;
		}

		@Override
		public ArchiveSummary query(Session session) throws DBServiceException {
			Query q = this.getNamedQuery(session);
			q.setInteger("orgId", this.orgId);
			q.setLong("targetComputerId", this.targetComputerId);
			ArchiveSummary as = (ArchiveSummary) q.uniqueResult();
			return as;
		}

	}

}