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
import com.code42.core.impl.DBCmd;
import com.code42.perm.C42Scope;
import com.code42.perm.C42Scope.Scope;
import com.code42.utils.LangUtils;

/**
 * Build a list of IPermission objects that are "visible" to the given user.
 * 
 * A permission is visible to the given user, if their current permissions would satisfy it, even if it is not
 * explicitly assigned to a role they have.
 * 
 * For example, admin.org.read is "visible" to a user with admin.org.
 * 
 * Permissions without a valid description are not included; they are invisible by definition.
 * 
 * @author tlindqui
 */
public class PermissionFindVisibleByUserCmd extends DBCmd<List<IPermission>> {

	@Override
	public List<IPermission> exec(CoreSession session) throws CommandException {
		try {
			Set<IPermission> visiblePermissions = new HashSet<IPermission>();

			List<IPermission> possiblePermissions = this.auth.getAllPermissions();
			for (IPermission possiblePermission : possiblePermissions) {
				if (!visiblePermissions.contains(possiblePermission)) {
					if (LangUtils.hasValue(possiblePermission.getDescription())
							&& this.auth.hasPermission(session, possiblePermission) && this.isIncluded(possiblePermission)) {
						visiblePermissions.add(possiblePermission);
					}
				}
			}

			return new ArrayList<IPermission>(visiblePermissions);
		} catch (Exception e) {
			throw new CommandException("Error findind visible permissions by user; {}", session.getUser(), e);
		}
	}

	/**
	 * Determine whether or not this permission should be included, based on whether the scope of the permission is CPC
	 * only and whether or not we are CPC.
	 * 
	 */
	private boolean isIncluded(IPermission permission) {
		boolean isCpcMaster = this.env.isCpcMaster();
		boolean isCpcOnly = false;
		if (permission.getClass().isAnnotationPresent(C42Scope.class)) {
			C42Scope a = permission.getClass().getAnnotation(C42Scope.class);
			if (a.value() == Scope.CPC) {
				isCpcOnly = true;
			}
		}
		if (!isCpcOnly || isCpcMaster) {
			return true;
		} else {
			return false;
		}
	}

}
