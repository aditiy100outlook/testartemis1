/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.user;

import org.hibernate.Query;
import org.hibernate.Session;

import com.code42.archiverecord.ArchiveRecordDeleteByUserCmd;
import com.code42.archiverecord.ArchiveSummaryDeleteByUserCmd;
import com.code42.archiverecord.ArchiveSummaryRollupDeleteByUserCmd;
import com.code42.auth.DeleteAuthCheckCmd;
import com.code42.auth.DeleteAuthCheckCmd.CheckType;
import com.code42.core.CommandException;
import com.code42.core.annotation.CoreNamedQuery;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.DeleteQuery;
import com.code42.core.impl.DBCmd;
import com.code42.user.destination.UserDestinationDeleteByUserCmd;

/**
 * BE VERY CAREFUL!!!
 * 
 * This command will completely delete and remove all references to a user, its social network, computers, settings,
 * history, archives, etc.
 * 
 * It is EXTREMELY destructive and executable only by a System Administrator.
 */
public class UserDeleteCmd extends DBCmd<Void> {

	private final int userId;

	public UserDeleteCmd(int userId) {
		this.userId = userId;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		try {
			this.db.beginTransaction();

			this.run(new DeleteAuthCheckCmd(CheckType.USER, this.userId), session);

			this.run(new ArchiveRecordDeleteByUserCmd(this.userId), session);
			this.run(new ArchiveSummaryDeleteByUserCmd(this.userId), session);
			this.run(new ArchiveSummaryRollupDeleteByUserCmd(this.userId), session);
			this.run(new UserDestinationDeleteByUserCmd(this.userId), session);
			this.run(new UserRemoveFromSocialNetworkCmd(this.userId), session);
			this.run(new UserRemoveAllComputersCmd(this.userId), session);
			this.run(new UserRemoveAllReferencesCmd(this.userId), session);

			User user = this.db.find(new UserFindByIdQuery(this.userId));
			if (user != null) {
				this.db.delete(new UserDeleteQuery(user));
			}

			this.db.afterTransaction(new UserPublishDeleteCmd(user), session);

			this.db.commit();
			return null;
		} finally {
			this.db.endTransaction();
		}
	}

	@CoreNamedQuery(name = "UserDeleteQuery", query = "delete from User u where u.userId = :userId")
	private static class UserDeleteQuery extends DeleteQuery<Void> {

		private final User user;

		private UserDeleteQuery(User user) {
			this.user = user;
		}

		@Override
		public void query(Session session) throws DBServiceException {
			if (this.user != null) {
				final Query q = this.getNamedQuery(session);
				q.setInteger("userId", this.user.getUserId());
				q.executeUpdate();
			}
		}

	}

}
