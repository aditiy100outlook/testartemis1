/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.user;

import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.crypto.StringHasher;
import com.code42.user.UserRegistrationBaseCmd.Error;
import com.code42.utils.LangUtils;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;

public abstract class UserBuilder<T> {

	// Required fields
	protected int orgId;
	protected String username;

	// Optional fields
	protected Option<String> email = None.getInstance();
	protected Option<String> password = None.getInstance();
	protected Option<String> firstName = None.getInstance();
	protected Option<String> lastName = None.getInstance();
	protected Option<String> userUid = None.getInstance();
	protected Option<Boolean> emailPromo = new Some<Boolean>(true);
	protected boolean invitation = false;

	// Internal Properties (not an API param)
	protected String passwordHashed;

	public UserBuilder(int orgId, String username) throws BuilderException {
		if (LangUtils.hasValue(username)) {
			this.username = username.toLowerCase().trim();
		}
		this.orgId = orgId;
	}

	public UserBuilder email(String email) {
		if (LangUtils.hasValue(email)) {
			this.email = new Some<String>(email.toLowerCase().trim());
		}
		return this;
	}

	public UserBuilder password(String password) {

		this.password = new Some<String>(password);
		// Set the passwordHashed field
		if (LangUtils.hasValue(password)) {
			if (StringHasher.C42.isValidHash(password)) {
				this.passwordHashed = password;
			} else {
				// Hash the password if not hashed already
				this.passwordHashed = StringHasher.C42.hash(password.trim());
			}
		}
		return this;
	}

	public UserBuilder firstName(String firstName) {
		if (LangUtils.hasValue(firstName)) {
			this.firstName = new Some<String>(firstName.trim());
		}
		return this;
	}

	public UserBuilder lastName(String lastName) {
		if (LangUtils.hasValue(lastName)) {
			this.lastName = new Some<String>(lastName.trim());
		}
		return this;
	}

	public UserBuilder userUid(String userUid) {
		if (LangUtils.hasValue(userUid)) {
			this.userUid = new Some<String>(userUid);
		}
		return this;
	}

	public UserBuilder emailPromo(boolean emailPromo) {
		this.emailPromo = new Some<Boolean>(emailPromo);
		return this;
	}

	public UserBuilder invitation() {
		this.invitation = true;
		return this;
	}

	/**
	 * Simple helper method to populate the given User object with the data from this builder.
	 */
	public User populateUser(User user) throws CommandException {
		user.setUsername(this.username);
		user.setOrgId(this.orgId);
		user.setActive(true);
		user.setBlocked(false);

		user.setEmailPromo(this.emailPromo.get(), "");
		if (!(this.email instanceof None)) {
			user.setEmail(this.email.get());
		}
		if (!(this.firstName instanceof None)) {
			user.setFirstName(this.firstName.get());
		}
		if (!(this.lastName instanceof None)) {
			user.setLastName(this.lastName.get());
		}
		if (this.passwordHashed != null) {
			user.setPassword(this.passwordHashed);
		}

		return user;
	}

	/**
	 * Validate the standard parameters
	 * 
	 * @throws BuilderException
	 */
	protected void validate() throws BuilderException {
		if (!LangUtils.hasValue(this.username)) {
			throw new BuilderException(Error.USERNAME_MISSING, "Missing username.");
		}

		if (this.orgId < 1) {
			throw new BuilderException(Error.ORG_NOT_FOUND, "Invalid org.");
		}

		if (this.orgId == 1) {
			throw new BuilderException(Error.ORG_BLOCKED, "Org Blocked; cannot create user in this org");
		}

	}

	/**
	 * Build an instance of the command; implementation specific validation should be called here.
	 * 
	 * @return
	 */
	abstract T build() throws BuilderException;
}
