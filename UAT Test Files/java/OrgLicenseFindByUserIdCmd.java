package com.code42.license;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.impl.DBCmd;

/**
 * Find an OrgLicense by user id
 */
public class OrgLicenseFindByUserIdCmd extends DBCmd<OrgLicense> {

	private final int userId;

	public OrgLicenseFindByUserIdCmd(int userId) {
		super();
		this.userId = userId;
	}

	@Override
	public OrgLicense exec(CoreSession session) throws CommandException {
		this.run(new IsUserManageableCmd(this.userId, C42PermissionApp.User.READ), session);

		OrgLicense ol = this.db.find(new OrgLicenseFindByUserIdQuery(this.userId));
		return ol;
	}
}
