/*
 * Created on Dec 3, 2010 by Tony Lindquist <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.auth;

import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.user.Role;

/**
 * Command to find a Role by its ID; filtered by those roles the logged in user is allowed to see.
 */
public class RoleFindByIdCmd extends DBCmd<Role> {

	private int roleId;

	public RoleFindByIdCmd(int roleId) {
		this.roleId = roleId;
	}

	@Override
	public Role exec(CoreSession session) throws CommandException {

		Role foundRole = null;

		List<Role> userRoles = this.runtime.run(new RoleFindAvailableByUserCmd(), session);
		Role role = this.db.find(new RoleFindByIdQuery(this.roleId));
		if (userRoles != null && role != null) {
			if (userRoles.contains(role)) {
				foundRole = role;
			}
		}

		return foundRole;
	}
}