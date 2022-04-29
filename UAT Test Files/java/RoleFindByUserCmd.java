/*
 * Created on Feb 24, 2011 by Tony Lindquist
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 *
 */
package com.code42.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.user.Role;
import com.code42.user.UserRole;
import com.code42.user.UserRoleFindByUserCmd;

/**
 * 
 * Command to find all Roles associated with a particular user
 * 
 * @author tlindqui
 */
public class RoleFindByUserCmd extends DBCmd<List<Role>> {

	private final int userId;

	public RoleFindByUserCmd(int userId) {
		this.userId = userId;
	}

	@Override
	public List<Role> exec(CoreSession session) throws CommandException {
		List<UserRole> userRoles = this.runtime.run(new UserRoleFindByUserCmd(this.userId), session);

		Set<Role> roles = new TreeSet<Role>();
		for (UserRole ur : userRoles) {
			roles.add(ur.getRole());
		}

		return new ArrayList<Role>(roles);
	}

}
