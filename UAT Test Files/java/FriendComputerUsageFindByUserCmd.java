package com.code42.computer;

import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.social.FriendComputerUsage;

public class FriendComputerUsageFindByUserCmd extends DBCmd<List<FriendComputerUsage>> {

	private int userId;

	public FriendComputerUsageFindByUserCmd(int userId) {
		this.userId = userId;
	}

	@Override
	public List<FriendComputerUsage> exec(CoreSession session) throws CommandException {
		this.runtime.run(new IsUserManageableCmd(this.userId, C42PermissionApp.User.READ), session);
		return this.db.find(new FriendComputerUsageFindBySourceUserQuery(this.userId));
	}

}
