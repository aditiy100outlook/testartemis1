/*
 * Created on Feb 24, 2011 by Tony Lindquist
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 *
 */
package com.code42.user;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.impl.DBCmd;

public class UserFindByUsernameAndOrgCmd extends DBCmd<User> {

	private final String username;
	private final int orgId;

	// transient
	private User user;

	public UserFindByUsernameAndOrgCmd(String username, int orgId) {
		this.username = username;
		this.orgId = orgId;
	}

	@Override
	public User exec(CoreSession session) throws CommandException {
		this.user = this.db.find(new UserFindByUsernameAndOrgQuery(this.username, this.orgId));
		if (this.user != null) {
			this.run(new IsUserManageableCmd(this.user, C42PermissionApp.User.READ), session);
		}
		return this.user;
	}

}
