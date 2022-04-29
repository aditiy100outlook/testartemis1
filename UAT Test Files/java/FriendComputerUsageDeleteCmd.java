package com.code42.computer;

import org.hibernate.Session;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.DeleteQuery;
import com.code42.core.impl.DBCmd;
import com.code42.social.FriendComputerUsage;

/**
 * Delete a row of t_friend_computer_usage.
 * 
 * @author mharper
 */
public class FriendComputerUsageDeleteCmd extends DBCmd<Void> {

	private final FriendComputerUsage fcu;

	public FriendComputerUsageDeleteCmd(FriendComputerUsage fcu) {
		super();
		this.fcu = fcu;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		// Use most strict auth check for now. It's possible this could be loosened.
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		try {
			this.db.beginTransaction();

			this.db.delete(new FriendComputerUsageDeleteQuery(this.fcu));

			this.run(new FriendComputerUsageChangePublishCmd(this.fcu, false), session);

			this.db.commit();

			return null;
		} finally {
			this.db.endTransaction();
		}
	}

	private static class FriendComputerUsageDeleteQuery extends DeleteQuery<FriendComputerUsage> {

		private final FriendComputerUsage fcu;

		public FriendComputerUsageDeleteQuery(FriendComputerUsage fcu) {
			this.fcu = fcu;
		}

		@Override
		public void query(Session session) throws DBServiceException {
			// In some cases, the FCU that is passed to this query has the proper id, but is not cached in the Hibernate
			// Session. This means that the passed in FCU is essentially transient and therefore cannot be deleted with this
			// form. As a compromise, first looking up the cached FCU in the session.
			final Object fcuFromSession = session.get(FriendComputerUsage.class, this.fcu.getFriendComputerUsageId());
			session.delete(fcuFromSession);
		}
	}
}
