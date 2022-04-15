/*
 * Created on Dec 3, 2010 by Tony Lindquist <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.computer;

import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.impl.DBCmd;

/**
 * Command to find a list of computers for a user. An optional parameter for 'active' is also included.
 */
public class ComputerFindByUserCmd extends DBCmd<List<Computer>> {

	private int userId;
	private Boolean active;
	private Boolean children;

	public ComputerFindByUserCmd(int userId) {
		this(userId, null/* active */, true);
	}

	public ComputerFindByUserCmd(int userId, Boolean active) {
		this(userId, active, true);
	}

	public ComputerFindByUserCmd(int userId, Boolean active, Boolean includeChildren) {
		this.userId = userId;
		this.active = active;
		this.children = includeChildren;
	}

	@Override
	public List<Computer> exec(CoreSession session) throws CommandException {

		// A subject can always read it's own computers
		if (session.getUser().getUserId() != this.userId) {
			this.runtime.run(new IsUserManageableCmd(this.userId, C42PermissionApp.User.READ), session);
		}

		// Find the computers and return them
		ComputerFindByUserQuery query = new ComputerFindByUserQuery(this.userId, this.active, this.children);
		List<Computer> list = this.db.find(query);

		return list;
	}

}
