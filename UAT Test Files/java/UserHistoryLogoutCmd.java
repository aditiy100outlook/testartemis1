package com.code42.user;

import java.util.Date;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

/**
 * Encapsulation of the business logic around updating a notion of "user history" when a user logs out of the webapp.
 * 
 * @author bmcguire
 */
public class UserHistoryLogoutCmd extends DBCmd<Boolean> {

	private final int userId;

	public UserHistoryLogoutCmd(int userId) {
		this.userId = userId;
	}

	@Override
	public Boolean exec(CoreSession session) throws CommandException {

		this.db.beginTransaction();
		try {
			UserHistory history = this.db.find(new UserHistoryFindByIdQuery(this.userId));
			history.setLogoutDate(new Date());
			this.db.update(new UserHistoryUpdateQuery(history));
			this.db.commit();

			return true;
		} catch (CommandException ce) {
			this.db.rollback();
			throw ce;
		} catch (Throwable t) {
			this.db.rollback();
			throw new CommandException("Unexpected exception while updating user history on logout", t);
		} finally {
			this.db.endTransaction();
		}
	}

}
