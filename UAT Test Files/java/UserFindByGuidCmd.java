/*
 * <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 *
 */
package com.code42.user;

import com.code42.computer.Computer;
import com.code42.computer.ComputerFindByGuidQuery;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

/**
 * Command to find a user object given a GUID
 */
public class UserFindByGuidCmd extends DBCmd<User> {

	private long guid;

	public UserFindByGuidCmd(long guid) {
		this.guid = guid;
	}

	@Override
	public User exec(CoreSession session) throws CommandException {
		// find the computer, then defer to the UserFindByIdCmd
		ComputerFindByGuidQuery cQuery = new ComputerFindByGuidQuery(this.guid);
		Computer computer = this.db.find(cQuery);
		if (computer == null) {
			return null;
		}
		return this.run(new UserFindByIdCmd(computer.getUserId()), session);
	}
}