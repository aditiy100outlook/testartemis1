package com.code42.user;

import java.util.Collection;
import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsEveryUserManageableCmd;
import com.code42.core.impl.DBCmd;
import java.util.Arrays;

public class UserRoleFindByUserCmd extends DBCmd<List<UserRole>> {

	private Collection<Integer> userIds;

	public UserRoleFindByUserCmd(int userId) {
		this(Arrays.asList(new Integer[] { userId }));
	}

	public UserRoleFindByUserCmd(Collection<Integer> userIds) {
		this.userIds = userIds;
	}

	@Override
	public List<UserRole> exec(CoreSession session) throws CommandException {
		this.runtime.run(new IsEveryUserManageableCmd(this.userIds, C42PermissionApp.User.READ), session);

		List<UserRole> list = this.db.find(new UserRoleFindByUserQuery(this.userIds));
		return list;
	}

}
