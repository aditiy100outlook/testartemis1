/*
 * <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.user;

import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

/**
 * Command to find users for an org.
 */
public class UserFindByOrgCmd extends DBCmd<List<User>> {

	private int orgId;
	private boolean active; /* Only return active users */

	public UserFindByOrgCmd(int orgId) {
		this(orgId, false);
	}

	public UserFindByOrgCmd(int orgId, boolean active) {
		this.orgId = orgId;
		this.active = active;
	}

	@Override
	public List<User> exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionApp.Org.READ, this.orgId);
		List<User> users;
		if (this.active) {
			users = this.db.find(new UserFindActiveByOrgQuery(this.orgId));
		} else {
			users = this.db.find(new UserFindByOrgQuery(this.orgId));
		}
		return users;
	}
}