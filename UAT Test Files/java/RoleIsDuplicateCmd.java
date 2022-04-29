/*
 * Created on Nov 22, 2010 by Tony Lindquist
 * <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 *
 */
package com.code42.auth;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.impl.DBCmd;
import com.code42.user.Role;

/**
 * 
 * Determine whether or not the given role name is a duplicate. If a full role object
 * is provided, it will exclude that role's ID from consideration, and will only return
 * true, if it finds another role with the same name, but a different ID.
 * 
 * @author tlindqui
 */
public class RoleIsDuplicateCmd extends DBCmd<Boolean> {

	private String roleName;
	private Role role;

	public RoleIsDuplicateCmd(String roleName) {
		this.roleName = roleName;
		this.role = new Role();
		this.role.setRoleName(roleName);
	}

	public RoleIsDuplicateCmd(Role role) {
		this.role = role;
		if (role != null) {
			this.roleName = role.getRoleName();
		}
	}

	@Override
	public Boolean exec(CoreSession session) throws CommandException {
		boolean duplicate = false;
		if (this.role != null) {
			try {
				Role r = this.db.find(new RoleFindDuplicateQuery(this.role));
				if (r != null) {
					duplicate = true;
				}
			} catch (DBServiceException e) {
				throw new CommandException("Error checking for duplicate role; roleName=" + this.roleName, e);
			}
		}
		return duplicate;
	}

}
