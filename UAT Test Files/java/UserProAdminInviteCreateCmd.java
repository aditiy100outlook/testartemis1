package com.code42.user;

import java.util.ArrayList;
import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.UnsupportedRequestException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.OrgSettingsInfo;
import com.code42.org.OrgSettingsInfoFindByOrgCmd;
import com.code42.org.OrgSettingsInfoFindByOrgCmd.Builder;
import com.code42.utils.LangUtils;
import com.code42.utils.Pair;
import com.code42.validation.rules.EmailRule;

/**
 * Create a new admin pro user account for each incoming username (which may be an email address). The only 'real' value
 * will be the user's email and username. All other fields will either be null or dummy values.
 * 
 * Once each user is created, send them an email to complete the invite story.
 * 
 * NOTE: each email in the list can succeed or fail independently of the others. A list of emails that failed and the
 * corresponding exceptions will be returned.
 * 
 * ALSO NOTE: This must NOT be called from inside of a transaction because we need to rollback just the users that fail.
 */
public class UserProAdminInviteCreateCmd extends DBCmd<Pair<List<User>, List<Pair<String, Exception>>>> {

	private static Logger log = LoggerFactory.getLogger(UserProAdminInviteCreateCmd.class);

	private final String username;
	private final String channelCustomerId;
	private final int orgId;

	/**
	 * @param username - a list of username (which may be email addresses)
	 * @param orgId - self explanatory
	 */
	public UserProAdminInviteCreateCmd(String username, String channelCustomerId, int orgId) {
		this.username = username;
		this.channelCustomerId = channelCustomerId;
		this.orgId = orgId;
	}

	public UserProAdminInviteCreateCmd(String email, String channelCustomerId, String orgId) {
		this(email, channelCustomerId, Integer.parseInt(orgId));
	}

	@Override
	public Pair<List<User>, List<Pair<String, Exception>>> exec(final CoreSession session) throws CommandException {

		if (session == null || session.getUser() == null) {
			throw new UnauthorizedException("No Authenticated User");
		}
		this.run(new IsOrgManageableCmd(this.orgId, C42PermissionApp.Org.UPDATE_BASIC), session);

		// Find the UserNameIsAnEmail setting for this org
		Builder osBuilder = new OrgSettingsInfoFindByOrgCmd.Builder();
		osBuilder.orgId(this.orgId);
		OrgSettingsInfo os = this.run(osBuilder.build(), session);
		boolean usernameIsAnEmail = os.getUsernameIsAnEmail();

		// Throw an error if there is an invalid username
		String validatedUsername = this.validateList(usernameIsAnEmail);

		final List<User> users = new ArrayList<User>(1);
		List<Pair<String, Exception>> errors = new ArrayList();

		try {
			this.db.beginTransaction();
			UserRegistrationProAdminCmd.Builder builder = new UserRegistrationProAdminCmd.Builder(this.orgId,
					validatedUsername);
			builder.invitation();
			builder.email(validatedUsername);
			UserRegistrationProAdminCmd cmd = builder.build();
			User user = this.runtime.run(cmd, session);
			if (!LangUtils.hasValue(user.getEmail())) {
				throw new CommandException("No email found for user.  Cannot invite: {}", user.getUsername());
			}

			this.runtime.run(new UserMapToChannelCreateCmd(user.getUserId(), this.channelCustomerId), session);

			users.add(user);
			this.db.commit();
		} catch (Exception e) {
			log.warn("UserRegistrationProAdminCmd failed registration: {}", e);
			this.db.rollback();
			errors.add(new Pair(this.username, e));
		} finally {
			this.db.endTransaction();
		}

		return new Pair(users, errors);
	}

	/**
	 * Validates either the username or the email address
	 * 
	 * @param usernameIsAnEmail
	 * @return a list of usernames or addresses
	 * @throws UnsupportedRequestException
	 */
	private String validateList(boolean usernameIsAnEmail) throws UnsupportedRequestException {
		if (this.username == null) {
			throw new UnsupportedRequestException("Empty username provided; no action taken");
		}

		if (usernameIsAnEmail) {
			if (!EmailRule.isValidEmail(this.username)) {
				throw new UnsupportedRequestException("Invalid email address included; email: " + this.username);
			}
		} else if (this.username.contains(" ")) {
			throw new UnsupportedRequestException("Invalid username included; username: " + this.username);
		}

		return this.username;
	}
}
