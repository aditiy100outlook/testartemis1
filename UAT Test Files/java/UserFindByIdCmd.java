/*
 * Created on Dec 3, 2010 by Tony Lindquist
 * <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 *
 */
package com.code42.user;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

/**
 * Command to find a user object by its ID
 */
public class UserFindByIdCmd extends DBCmd<User> {

	private int userId;

	public UserFindByIdCmd(int userId) {
		this.userId = userId;
	}

	public UserFindByIdCmd(IUser user) {
		this.userId = user.getUserId();
	}

	@Override
	public User exec(CoreSession session) throws CommandException {

		// Find the user
		UserFindByIdQuery query = new UserFindByIdQuery(this.userId);
		User user = this.db.find(query);
		if (user == null) {
			return null;
		}

		if (session.getUser().getUserId().equals(user.getUserId())) {
			// A subject can always read its own user
			return user;
		}

		// Make sure the subject is allowed to read this user
		this.auth.isAuthorized(session, C42PermissionApp.Org.READ, user.getOrgId());

		return user;
	}
}