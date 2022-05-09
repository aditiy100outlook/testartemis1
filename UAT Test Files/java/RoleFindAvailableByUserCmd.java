/*
 * Created on Nov 22, 2010 by Tony Lindquist <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.auth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.user.Role;
import com.code42.user.UserSso;

/**
 * Build a list of Roles that are "visible" to the given user.
 * 
 * A role is visible to the given user, if it meets one of two criteria:
 * 
 * <ol>
 * <li>1) It is a role that the user already has, or
 * <li>2) It consists of permissions that the user has
 * </ol>
 * 
 * For example, ADMIN users have admin.org.all and admin.user.all.
 * 
 * If there is a theoretical role FOO that requires admin.org.read and admin.user.read, an ADMIN user would have the
 * authority to assign that role since their permissions meet or exceed that requirement, even though they do not
 * explicitly have the role of FOO itself.
 */
public class RoleFindAvailableByUserCmd extends DBCmd<List<Role>> {

	private static final Logger log = LoggerFactory.getLogger(RoleFindAvailableByUserCmd.class.getName());

	@Override
	public List<Role> exec(CoreSession session) throws CommandException {

		UserSso user = session.getUser();
		try {
			this.db.openSession();
			Set<Role> visibleRoles = new HashSet<Role>();

			if (user != null) {
				List<Role> possibleRoles = this.db.find(new RoleFindAllQuery());
				for (Role possibleRole : possibleRoles) {

					// Don't return hidden roles
					if (this.auth.isHidden(possibleRole)) {
						continue;
					}

					boolean addRole = true;
					if (!visibleRoles.contains(possibleRole)) {
						for (IPermission permission : possibleRole.getPermissions()) {
							if (!this.auth.hasPermission(session, permission)) {
								// This role requires a permission the user does not have; skip it.
								log.trace("user=" + user.getUsername() + ": Rejected role: " + possibleRole.getRoleName());
								addRole = false;
							}
							if (!addRole) {
								break;
							}
						}
						if (addRole) {
							log.trace("user=" + user.getUsername() + ": Added role: " + possibleRole.getRoleName());
							visibleRoles.add(possibleRole);
						}
					}
				}
			}

			return new ArrayList<Role>(visibleRoles);
		} catch (DBServiceException e) {
			throw new CommandException("Error finding visible roles by user; user=" + user, e);
		} finally {
			this.db.closeSession();
		}
	}
}
