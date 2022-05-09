package com.code42.computer;

import org.hibernate.Session;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.UpdateQuery;
import com.code42.core.impl.DBCmd;
import com.code42.social.FriendComputer;
import com.code42.social.FriendLockByIdQuery;

/**
 * Updates a friend computer using a transaction.
 * 
 * @param friendComputer
 * @return the friend computer
 */
public class FriendComputerUpdateCmd extends DBCmd<FriendComputer> {

	private FriendComputer fc;

	public FriendComputerUpdateCmd(FriendComputer fc) {
		super();
		this.fc = fc;
	}

	@Override
	public FriendComputer exec(CoreSession session) throws CommandException {
		try {
			this.db.beginTransaction();
			this.db.find(new FriendLockByIdQuery(this.fc.getFriend().getFriendId()));
			this.db.update(new FriendComputerUpdateQuery(this.fc));
			this.db.commit();
		} finally {
			this.db.endTransaction();
		}
		return this.fc;
	}

	private static class FriendComputerUpdateQuery extends UpdateQuery<Object> {

		private FriendComputer fc;

		public FriendComputerUpdateQuery(FriendComputer fc) {
			super();
			this.fc = fc;
		}

		@Override
		public Object query(Session session) throws DBServiceException {
			session.update(this.fc);
			return this.fc;
		}

	}

}
