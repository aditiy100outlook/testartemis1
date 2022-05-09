package com.code42.computer;

import org.hibernate.Query;
import org.hibernate.Session;

import com.code42.core.CommandException;
import com.code42.core.annotation.CoreNamedQuery;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.DeleteQuery;
import com.code42.core.impl.DBCmd;
import com.code42.social.FriendComputer;
import com.code42.social.FriendLockByIdQuery;

/**
 * Delete a friend computer
 * 
 */
public class FriendComputerDeleteCmd extends DBCmd<Void> {

	private final FriendComputer friendComputer;

	public FriendComputerDeleteCmd(FriendComputer friendComputer) {
		super();
		this.friendComputer = friendComputer;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		try {
			this.db.beginTransaction();
			this.db.find(new FriendLockByIdQuery(this.friendComputer.getFriend().getFriendId()));
			this.db.delete(new FriendComputerDeleteQuery(this.friendComputer.getFriendComputerId()));
			this.db.commit();
		} finally {
			this.db.endTransaction();
		}
		return null;
	}

	@CoreNamedQuery(name = "FriendComputerDeleteQuery", query = "delete from FriendComputer fc where fc.friendComputerId = :friendComputerId")
	private static class FriendComputerDeleteQuery extends DeleteQuery<Void> {

		private final Long friendComputerId;

		public FriendComputerDeleteQuery(Long friendComputerId) {
			this.friendComputerId = friendComputerId;
		}

		@Override
		public void query(Session session) throws DBServiceException {
			final Query q = this.getNamedQuery(session);
			q.setLong("friendComputerId", this.friendComputerId);
			q.executeUpdate();
		}

	}

}
