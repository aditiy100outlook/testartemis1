package com.code42.archivesummary;

import java.util.Collection;
import java.util.List;

import org.hibernate.Hibernate;
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
import com.code42.executor.jsr166.Arrays;

/**
 * Gather archive summary information from the DB for a specific user
 */
public class ArchiveSummaryFindByUserCmd extends DBCmd<List<ArchiveSummary>> {

	private int userId;

	public ArchiveSummaryFindByUserCmd(int userId) {
		this.userId = userId;
	}

	@Override
	public List<ArchiveSummary> exec(CoreSession session) throws CommandException {
		this.runtime.run(new IsUserManageableCmd(this.userId, C42PermissionApp.User.READ), session);
		return this.db.find(new ArchiveSummaryFindByUserQuery(this.userId));
	}

	@CoreNamedQuery(name = "ArchiveSummaryFindByUser", query = "from ArchiveSummary a where a.userId in (:userIds)")
	public static class ArchiveSummaryFindByUserQuery extends FindQuery<List<ArchiveSummary>> {

		private Collection<Integer> userIds;

		public ArchiveSummaryFindByUserQuery(int userId) {
			this(Arrays.asList(new Integer[] { userId }));
		}

		public ArchiveSummaryFindByUserQuery(Collection<Integer> userIds) {
			this.userIds = userIds;
		}

		@Override
		public List<ArchiveSummary> query(Session session) throws DBServiceException {
			Query q = this.getNamedQuery(session);
			q.setParameterList("userIds", this.userIds, Hibernate.INTEGER);
			List<ArchiveSummary> list = q.list();
			return list;
		}
	}
}