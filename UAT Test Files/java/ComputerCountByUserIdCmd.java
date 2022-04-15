package com.code42.computer;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

/**
 * Returns the current count of
 * 
 * @author bmcguire
 */
public class ComputerCountByUserIdCmd extends DBCmd<Integer> {

	private int userId;

	public ComputerCountByUserIdCmd(int userId) {
		this.userId = userId;
	}

	public int getUserId() {
		return this.userId;
	}

	@Override
	public Integer exec(CoreSession session) throws CommandException {

		return this.db.find(new ComputerCountByUserIdQuery(this.userId, null));
	}
}
