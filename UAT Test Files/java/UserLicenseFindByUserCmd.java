package com.code42.license;

import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.impl.DBCmd;

public class UserLicenseFindByUserCmd extends DBCmd<List<UserLicense>> {

	private final int userId;

	public UserLicenseFindByUserCmd(int userId) {
		this.userId = userId;
	}

	@Override
	public List<UserLicense> exec(CoreSession session) throws CommandException {
		this.run(new IsUserManageableCmd(this.userId, C42PermissionApp.User.READ), session);

		List<UserLicense> ul = this.db.find(new UserLicenseFindByUserQuery(this.userId));
		return ul;
	}

}
