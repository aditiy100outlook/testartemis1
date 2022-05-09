package com.code42.recent;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.user.UserDto;

public class RecentListAddUserCmd extends AbstractCmd<Void> {

	private UserDto user;

	public RecentListAddUserCmd(UserDto user) {
		this.user = user;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		int userId = this.user.getUserId();
		String username = this.user.getUsername();
		String email = this.user.getEmail();
		String firstName = this.user.getFirstName();
		String lastName = this.user.getLastName();
		String firstLastSearch = this.user.getFirstLastSearch();
		String lastFirstSearch = this.user.getLastFirstSearch();

		RecentUser ru = new RecentUser(userId, username, email, firstName, lastName, firstLastSearch, lastFirstSearch);

		this.runtime.run(new RecentListAddItemCmd(ru), session);

		return null;
	}

}
