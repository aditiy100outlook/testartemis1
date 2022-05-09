/*
 * Created on Feb 24, 2011 by Tony Lindquist <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.auth;

import java.util.List;

import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;

import com.code42.core.CommandException;
import com.code42.core.annotation.CoreNamedQuery;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;
import com.code42.user.Role;

/**
 * 
 * Command to find a Role by its name; filtered by those roles the given user is allowed to see.
 * 
 * @author tlindqui
 */
public class RoleFindByNameCmd extends DBCmd<Role> {

	private final String roleName;

	public RoleFindByNameCmd(String roleName) {
		this.roleName = roleName;
	}

	@Override
	public Role exec(CoreSession session) throws CommandException {

		Role foundRole = null;

		List<Role> userRoles = this.runtime.run(new RoleFindAvailableByUserCmd(), session);
		Role role = this.db.find(new RoleFindByNameQuery(this.roleName));
		if (userRoles != null && role != null) {
			if (userRoles.contains(role)) {
				foundRole = role;
			}
		}

		return foundRole;
	}

	@CoreNamedQuery(name = "findRoleByRoleName", query = "select r from Role as r where r.roleName = :roleName")
	public static class RoleFindByNameQuery extends FindQuery<Role> {

		private final String roleName;

		public RoleFindByNameQuery(String roleName) {
			this.roleName = roleName;
		}

		@Override
		public Role query(Session session) throws DBServiceException {
			try {
				Query query = this.getNamedQuery(session);
				query.setString("roleName", this.roleName);
				Role role = (Role) query.uniqueResult();
				return role;
			} catch (HibernateException e) {
				throw new DBServiceException("Error Finding Role(s); roleName=" + this.roleName);
			}
		}
	}
}
