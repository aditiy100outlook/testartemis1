package com.code42.user;

import java.util.List;

import com.code42.auth.IPermission;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.impl.DBCmd;

/**
 * Find the permissions for the given user
 */
public class UserFindPermissionsCmd extends DBCmd<List<IPermission>> {

	private IUser user;

	public UserFindPermissionsCmd(IUser user) {
		this.user = user;
	}

	@Override
	public List<IPermission> exec(CoreSession session) throws CommandException {
		this.run(new IsUserManageableCmd(this.user, C42PermissionApp.User.READ), session);

		return PermissionsCache.get(this.user.getUserId());
	}
}
