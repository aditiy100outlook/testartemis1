/*
 * Created on Feb 24, 2011 by Tony Lindquist
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 *
 */
package com.code42.user;

import java.util.ArrayList;
import java.util.List;

import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;

public class UserFindByUsernameCmd extends DBCmd<List<User>> {

	private final Builder data;

	public UserFindByUsernameCmd(Builder data) {
		this.data = data;
	}

	@Override
	public List<User> exec(CoreSession session) throws CommandException {
		List<User> response = new ArrayList<User>();

		List<User> users = this.db.find(new UserFindByUsernameQuery(this.data.username));
		if (this.data.inclusive.get()) {
			for (User user : users) {
				this.db.find(new UserLoadQuery(user));
			}
		}

		if (!(this.data.active instanceof None)) {
			// Filter by active state
			for (User user : users) {
				if (user.isActive() == this.data.active.get()) {
					response.add(user);
				}
			}
		} else {
			response = users;
		}

		return response;
	}

	// //////////////////////////
	// BUILDER CLASS
	// //////////////////////////

	/**
	 * Builds the input data and the UserFindByUsername command.
	 * 
	 * Required value (username)
	 * 
	 * <ol>
	 * Default values if not set:
	 * <li>inclusive : false</li>
	 * <li>active : not used; if set, the results will be filtered accordingly</li>
	 * <ol>
	 */
	public static class Builder {

		/* These values must always be present; it's the only way to get a builder */
		public String username;

		// Required options (none)

		// Defaulted options; can be overridden
		private Option<Boolean> active = None.getInstance();
		private Option<Boolean> inclusive = new Some<Boolean>(false);

		public Builder(String username) {
			this.username = username;
		}

		public Builder active(boolean active) {
			this.active = new Some<Boolean>(active);
			return this;
		}

		public Builder inclusive(boolean inclusive) {
			this.inclusive = new Some<Boolean>(inclusive);
			return this;
		}

		public void validate() throws BuilderException {

		}

		public UserFindByUsernameCmd build() throws BuilderException {

			this.validate();
			return new UserFindByUsernameCmd(this);
		}
	}

}
