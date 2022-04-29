/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.archivesummary;

import org.hibernate.Query;
import org.hibernate.Session;

import com.code42.archiverecord.ArchiveSummary;
import com.code42.core.CommandException;
import com.code42.core.annotation.CoreNamedQuery;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;

/**
 * Find the unique instance of ArchiveSummary for this user and target.
 */
public class ArchiveSummaryFindByUserAndTargetCmd extends DBCmd<ArchiveSummary> {

	private final int userId;
	private final long targetComputerId;

	public ArchiveSummaryFindByUserAndTargetCmd(int userId, long targetComputerId) {
		this.userId = userId;
		this.targetComputerId = targetComputerId;
	}

	@Override
	public ArchiveSummary exec(CoreSession session) throws CommandException {
		this.run(new IsUserManageableCmd(this.userId, C42PermissionApp.User.READ), session);
		return this.db.find(new ArchiveSummaryFindByUserAndTargetQuery(this.userId, this.targetComputerId));
	}

	@CoreNamedQuery(name = "ArchiveSummaryFindByUserAndTargetQuery", query = "from ArchiveSummary a where a.userId = :userId and a.targetComputerId = :targetComputerId")
	private static class ArchiveSummaryFindByUserAndTargetQuery extends FindQuery<ArchiveSummary> {

		private final int userId;
		private final long targetComputerId;

		public ArchiveSummaryFindByUserAndTargetQuery(int userId, long targetComputerId) {
			this.userId = userId;
			this.targetComputerId = targetComputerId;
		}

		@Override
		public ArchiveSummary query(Session session) throws DBServiceException {
			Query q = this.getNamedQuery(session);
			q.setInteger("userId", this.userId);
			q.setLong("targetComputerId", this.targetComputerId);
			return (ArchiveSummary) q.uniqueResult();
		}

	}

}
