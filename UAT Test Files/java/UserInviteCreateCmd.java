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
import com.code42.email.signup.WelcomeUserEmailSendCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.OrgSettingsInfo;
import com.code42.org.OrgSettingsInfoFindByOrgCmd;
import com.code42.org.OrgSettingsInfoFindByOrgCmd.Builder;
import com.code42.utils.LangUtils;
import com.code42.utils.Pair;
import com.code42.validation.rules.EmailRule;

/**
 * Create a new minimal user account for each incoming username (which may be an email address). The only 'real' value
 * will be the user's email and username. All other fields will either be null or dummy values.
 * 
 * Once each user is created, send them an email to complete the invite story.
 * 
 * NOTE: each email in the list can succeed or fail independently of the others. A list of emails that failed and the
 * corresponding exceptions will be returned.
 * 
 * ALSO NOTE: This must NOT be called from inside of a transaction because we need to rollback just the users that fail.
 */
public class UserInviteCreateCmd extends DBCmd<List<Pair<String, Exception>>> {

	private static Logger log = LoggerFactory.getLogger(UserInviteCreateCmd.class);

	private final List usernames;
	private final String emailSubject;
	private final String emailBody;
	private final int orgId;
	private final String senderEmail;

	/**
	 * @param usernames - a list of usernames (which may be email addresses)
	 * @param emailSubject - subject for the email to be sent
	 * @param emailBody - body of the email to send
	 * @param orgId - self explanatory
	 * @param senderEmail - optional
	 */
	public UserInviteCreateCmd(List usernames, String emailSubject, String emailBody, int orgId, String senderEmail) {
		this.usernames = usernames;
		this.emailSubject = emailSubject;
		this.emailBody = emailBody;
		this.orgId = orgId;
		this.senderEmail = senderEmail;
	}

	public UserInviteCreateCmd(List emails, String emailSubject, String emailBody, String orgId, String senderEmail) {
		this(emails, emailSubject, emailBody, Integer.parseInt(orgId), senderEmail);
	}

	@Override
	public List<Pair<String, Exception>> exec(final CoreSession session) throws CommandException {
		if (session == null || session.getUser() == null) {
			throw new UnauthorizedException("No Authenticated User");
		}
		this.run(new IsOrgManageableCmd(this.orgId, C42PermissionApp.Org.UPDATE_BASIC), session);

		// Find the UserNameIsAnEmail setting for this org
		Builder osBuilder = new OrgSettingsInfoFindByOrgCmd.Builder();
		osBuilder.orgId(this.orgId);
		OrgSettingsInfo os = this.run(osBuilder.build(), session);
		boolean usernameIsAnEmail = os.getUsernameIsAnEmail();

		// Throw an error if there are invalid usernames (username may be an email address) in this list
		List<String> validatedList = this.validateList(usernameIsAnEmail);

		final List<User> users = new ArrayList<User>(validatedList.size());
		List<Pair<String, Exception>> errors = new ArrayList();

		for (String username : validatedList) {
			try {
				this.db.beginTransaction();
				UserRegistrationCmd.Builder builder = new UserRegistrationCmd.Builder(this.orgId, username);
				builder.invitation(); // needed so we don't generate password for LDAP users
				if (usernameIsAnEmail || EmailRule.isValidEmail(username)) {
					builder.email(username);
				}
				UserRegistrationCmd cmd = builder.build();
				User user = this.runtime.run(cmd, session);
				if (!LangUtils.hasValue(user.getEmail())) {
					throw new CommandException("No email found for user.  Cannot invite: {}", user.getUsername());
				}

				users.add(user);
				this.db.commit();
			} catch (Exception e) {
				log.warn("UserInviteCreateCmd failed registration: {}", e);
				this.db.rollback();
				errors.add(new Pair(username, e));
			} finally {
				this.db.endTransaction();
			}
		}

		// Send Emails
		for (User user : users) {
			this.run(new WelcomeUserEmailSendCmd(user, this.emailSubject, this.emailBody, this.senderEmail), session);
		}

		return errors;
	}

	/**
	 * Validates either the username or the email address
	 * 
	 * @param usernameIsAnEmail
	 * @return a list of usernames or addresses
	 * @throws UnsupportedRequestException
	 */
	private List<String> validateList(boolean usernameIsAnEmail) throws UnsupportedRequestException {
		if (!LangUtils.hasElements(this.usernames)) {
			throw new UnsupportedRequestException("Empty List provided; no action taken");
		}

		List<String> validatedList = new ArrayList<String>(this.usernames.size());
		for (Object s : this.usernames) {
			String username = String.valueOf(s).trim();
			if (usernameIsAnEmail) {
				if (!EmailRule.isValidEmail(username)) {
					throw new UnsupportedRequestException("Invalid email address included; email: " + username);
				}
			} else if (username.contains(" ")) {
				throw new UnsupportedRequestException("Invalid username included; username: " + username);
			}
			validatedList.add(username);
		}
		return validatedList;
	}
}
