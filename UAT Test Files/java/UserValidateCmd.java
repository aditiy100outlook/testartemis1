/*
 * Created on Nov 29, 2010 by Tony Lindquist <a href="http://www.code42.com">(c)2004 Code 42 Software, Inc.</a>
 */
package com.code42.user;

import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.DuplicateExistsException;
import com.code42.core.impl.DBCmd;
import com.code42.org.OrgSettingsInfo;
import com.code42.org.OrgSettingsInfoFindByOrgCmd;
import com.code42.user.UserUpdateCmd.Error;
import com.code42.utils.LangUtils;
import com.code42.validation.rules.EmailRule;

/**
 * Validate the given user; ensure that it has sufficient identifying characteristics and an org. Also make sure that
 * there are no duplicates.
 */
class UserValidateCmd extends DBCmd<User> {

	private User user;

	protected UserValidateCmd(User user) {
		this.user = user;
	}

	@Override
	public User exec(CoreSession session) throws CommandException {

		// Validate; Must have something to identify the user
		if (null == this.user || !LangUtils.hasValue(this.user.getUsername())
				&& !LangUtils.hasValue(this.user.getLastName()) && !LangUtils.hasValue(this.user.getMobilePhone())) {
			throw new CommandException("User._identity_ " + this.user);
		}

		// Must be assigned to an org
		if (this.user.getOrgId() < 1) {
			throw new CommandException("User.orgId: " + this.user.getOrgId());
		}

		// Must have a unique username
		List<User> dups;
		try {
			dups = this.db.find(new UserFindDuplicatesQuery(this.user));
		} catch (DBServiceException e) {
			throw new CommandException("Unable to check for duplicates", e);
		}
		if (!dups.isEmpty()) {
			throw new DuplicateExistsException("Duplicate User account exists; user=" + this.user);
		}

		// Conditional checks based on org settings
		OrgSettingsInfo osi = this.run(new OrgSettingsInfoFindByOrgCmd.Builder().orgId(this.user.getOrgId()).build(),
				this.auth.getAdminSession());
		if (osi.getUsernameIsAnEmail() == true) {
			// user's org requires username to be an email address
			if (!EmailRule.isValidEmail(this.user.getUsername())) {
				throw new CommandException(Error.USERNAME_NOT_AN_EMAIL, "User's org requires email address as username");
			}
		}

		// clean the data
		this.user.transform();

		return this.user;

	}
}
