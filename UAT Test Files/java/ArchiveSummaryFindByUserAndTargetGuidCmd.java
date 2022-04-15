package com.code42.archivesummary;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.type.StandardBasicTypes;

import com.code42.archiverecord.ArchiveSummary;
import com.code42.core.CommandException;
import com.code42.core.annotation.CoreNamedQuery;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;

public class ArchiveSummaryFindByUserAndTargetGuidCmd extends DBCmd<List<ArchiveSummary>> {

	private int userId;
	private Collection<Long> targetComputerIds;

	public ArchiveSummaryFindByUserAndTargetGuidCmd(int userId, Collection<Long> targetComputerIds) {
		this.userId = userId;
		this.targetComputerIds = targetComputerIds;
	}

	@Override
	public List<ArchiveSummary> exec(CoreSession session) throws CommandException {

		this.runtime.run(new IsUserManageableCmd(this.userId, C42PermissionApp.User.READ), session);
		return this.db.find(new ArchiveSummaryFindByUserAndTargetGuidQuery(this.userId, this.targetComputerIds));
	}

	@CoreNamedQuery(name = "ArchiveSummaryFindByUserAndTargetGuid", query = "from ArchiveSummary a where a.userId in (:userIds) and a.targetComputerId in (:targetComputerIds)")
	public static class ArchiveSummaryFindByUserAndTargetGuidQuery extends FindQuery<List<ArchiveSummary>> {

		private Collection<Integer> userIds;
		private Collection<Long> targetComputerIds;

		public ArchiveSummaryFindByUserAndTargetGuidQuery(int userId, long targetComputerId) {
			this(userId, Arrays.asList(targetComputerId));
		}

		public ArchiveSummaryFindByUserAndTargetGuidQuery(Collection<Integer> userIds, long targetComputerId) {
			this(userIds, Arrays.asList(targetComputerId));
		}

		public ArchiveSummaryFindByUserAndTargetGuidQuery(int userId, Collection<Long> targetComputerIds) {
			this(Arrays.asList(userId), targetComputerIds);
		}

		public ArchiveSummaryFindByUserAndTargetGuidQuery(Collection<Integer> userIds, Collection<Long> targetComputerIds) {
			this.userIds = userIds;
			this.targetComputerIds = targetComputerIds;
		}

		@Override
		public List<ArchiveSummary> query(Session session) throws DBServiceException {
			Query q = this.getNamedQuery(session);
			q.setParameterList("userIds", this.userIds, StandardBasicTypes.INTEGER);
			q.setParameterList("targetComputerIds", this.targetComputerIds, StandardBasicTypes.LONG);
			List<ArchiveSummary> list = q.list();
			return list;
		}

	}

}