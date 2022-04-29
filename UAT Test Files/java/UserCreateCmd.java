/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.user;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.EnumSet;

import org.hibernate.Session;

import com.backup42.common.AuthorizeRules;
import com.backup42.common.OrgType;
import com.backup42.common.util.CPValidation;
import com.backup42.server.MasterServices;
import com.code42.account.AuthorizeRulesFindByOrgIdCmd;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.CreateQuery;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.code42.crypto.StringHasher;
import com.code42.encryption.EncryptionServices;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.Org;
import com.code42.org.OrgFindByIdCmd;
import com.code42.org.OrgSettingsInfo;
import com.code42.org.OrgSettingsInfoFindByOrgCmd;
import com.code42.recent.RecentListCreateQuery;
import com.code42.user.UserRegistrationBaseCmd.Error;
import com.code42.utils.LangUtils;
import com.code42.utils.option.None;
import com.code42.utils.option.Some;
import com.code42.validation.rules.EmailRule;

/**
 * Create a user with simple validation of org and user values. It also ensures that there is a valid usernameId and an
 * initial empty "Recent List" for searching.
 * 
 * Note: UserRegistrationCmd should be preferred over this low-level command.
 */
public class UserCreateCmd extends DBCmd<User> {

	private static Logger log = LoggerFactory.getLogger(UserCreateCmd.class);

	// Properties
	private final UserBuilder data;

	// Transient Properties
	private Org org;

	public UserCreateCmd(UserBuilder<UserCreateCmd> data) {
		this.data = data;
	}

	@Override
	public User exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionApp.User.CREATE, this.data.orgId);

		this.org = CoreBridge.runNoException(new OrgFindByIdCmd(this.data.orgId));

		this.validate(session);

		User user = null;
		try {
			this.db.beginTransaction();

			// Get org to default in the user's max bytes
			OrgSettingsInfoFindByOrgCmd.Builder osBuilder = new OrgSettingsInfoFindByOrgCmd.Builder();
			osBuilder.org(this.org);
			OrgSettingsInfo os = this.runtime.run(osBuilder.build(), session);

			user = this.data.populateUser(new BackupUser());
			user.setMaxBytes(os.getDefaultUserMaxBytes());
			user = this.db.create(new UserCreateQuery((BackupUser) user));

			// This is done after the above save to ensure we have a userId
			String id = (LangUtils.hasValue(this.data.userUid)) ? this.data.userUid.get().toString()
					: generateUsernameId(user.getUserId());
			user.setUserUid(id);
			user = this.db.update(new UserUpdateQuery(user));

			// A new user requires an empty recent list (Bret was confused, so explanation is required)
			int myClusterId = this.env.getMyClusterId();
			this.db.create(new RecentListCreateQuery(user.getUserId(), new byte[0], myClusterId));

			this.db.afterTransaction(new UserPublishCreateCmd(user), session);

			this.db.commit();
		} catch (Exception e) {
			this.db.rollback();
			throw new CommandException("Error creating user; user:" + user, e);
		} finally {
			this.db.endTransaction();
		}

		log.info("New User Created: " + user);

		return user;
	}

	private void validate(CoreSession session) throws CommandException {
		// verify the org exists
		if (this.org == null) {
			throw new CommandException(Error.ORG_NOT_FOUND, "Org not found.");
		}

		boolean override = false;
		if (this.data instanceof UserCreateCmd.Builder) {
			override = ((UserCreateCmd.Builder) this.data).override;
		}
		if (!override && !MasterServices.getInstance().isMasterOrg(this.org)) {
			throw new UnauthorizedException("Cannot create users in hosted orgs; orgId=" + this.org.getOrgId());
		}

		// rules for this org
		final AuthorizeRules rules = this.run(new AuthorizeRulesFindByOrgIdCmd(this.org.getOrgId()), this.auth
				.getAdminSession());

		// Verify the type
		EnumSet<OrgType> expectedTypes = this.env.getClusterOrgTypes();
		if (!expectedTypes.contains(this.org.getType())) {
			throw new CommandException("Unable to create this user in this cluster; type=" + this.org.getType()
					+ "; expected=" + expectedTypes);
		}

		// Make sure the org is active.
		if (!this.org.isActive()) {
			throw new CommandException(Error.ORG_INACTIVE, "Org deactivated.");
		}

		// Make sure the org is not blocked.
		if (this.org.isBlocked()) {
			throw new CommandException(Error.ORG_BLOCKED, "Org blocked");
		}

		// verify password but only if it hasn't already been hashed
		if (!(this.data.password instanceof None) && !StringHasher.C42.isValidHash(this.data.password.get().toString())) {
			if (!CPValidation.isValidPassword(this.data.password.get().toString(), rules)) {
				throw new CommandException(Error.PASSWORD_INVALID, "Invalid password.");
			}
		}

		// verify username
		if (rules.isUsernameIsAnEmail()) {
			if (!EmailRule.isValidEmail(this.data.username)) {
				throw new CommandException(Error.USERNAME_INVALID, "Username must be a valid email address.");
			} else {
				this.data.email = new Some<String>(this.data.username);
			}
		}
		// verify email - not required
		if (LangUtils.hasValue(this.data.email) && !EmailRule.isValidEmail(this.data.email.get().toString())) {
			throw new CommandException(Error.EMAIL_INVALID, "Invalid email address.");
		}
	}

	public static String generateUsernameId(int userId) throws CommandException {
		try {
			final String encryptedId = EncryptionServices.getCrypto().getKey(userId);
			final String id = URLEncoder.encode(encryptedId, "US-ASCII");
			return id;
		} catch (UnsupportedEncodingException e) {
			throw new CommandException("Unable to generate usernameId; userId=" + userId, e);
		}
	}

	/**
	 * Query class
	 */
	private class UserCreateQuery extends CreateQuery<BackupUser> {

		BackupUser user;

		private UserCreateQuery(BackupUser user) {
			this.user = user;
		}

		@Override
		public BackupUser query(Session session) throws DBServiceException {
			session.save(this.user);
			return this.user;
		}
	}

	// ///////////////////////
	// BUILDER
	// ///////////////////////

	public static class Builder extends UserBuilder<UserCreateCmd> {

		boolean override = false;

		public Builder(int orgId, String username) throws BuilderException {
			super(orgId, username);
		}

		public Builder override(boolean override) {
			this.override = override;
			return this;
		}

		@Override
		public UserCreateCmd build() throws BuilderException {
			this.validate();
			return new UserCreateCmd(this);
		}
	}

}
