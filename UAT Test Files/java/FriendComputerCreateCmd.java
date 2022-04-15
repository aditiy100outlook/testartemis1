package com.code42.computer;

import org.hibernate.Session;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.CreateQuery;
import com.code42.core.impl.DBCmd;
import com.code42.social.FriendComputer;
import com.code42.social.FriendLockByIdQuery;

/**
 * Persists a new friend computer using a transaction.
 * 
 * @param friendComputer
 * @return the friend computer
 */
public class FriendComputerCreateCmd extends DBCmd<FriendComputer> {

	private FriendComputer fc;

	public FriendComputerCreateCmd(FriendComputer fc) {
		super();
		this.fc = fc;
	}

	@Override
	public FriendComputer exec(CoreSession session) throws CommandException {
		try {
			this.db.beginTransaction();
			this.db.find(new FriendLockByIdQuery(this.fc.getFriend().getFriendId()));
			this.db.create(new FriendComputerCreateQuery(this.fc));
			this.db.commit();
		} finally {
			this.db.endTransaction();
		}
		return this.fc;
	}

	private static class FriendComputerCreateQuery extends CreateQuery<FriendComputer> {

		private FriendComputer fc;

		public FriendComputerCreateQuery(FriendComputer fc) {
			super();
			this.fc = fc;
		}

		@Override
		public FriendComputer query(Session session) throws DBServiceException {
			session.save(this.fc);
			return this.fc;
		}

	}

}
