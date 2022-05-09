package com.code42.auth;

import java.security.SecureRandom;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.IAuthTokenService;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.user.User;
import com.code42.user.UserFindByUsernameCmd;
import com.code42.user.UserFindPermissionsCmd;
import com.code42.user.UserSso;
import com.code42.user.UserSsoFindByUserIdCmd;
import com.code42.utils.LangUtils;
import com.code42.utils.Pair;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;
import com.google.inject.Inject;

/**
 * Create an auth token for the authenticated user.
 * 
 * An auth token consists of two parts; by convention, the first part comes from the browser cookie, and the second part
 * becomes a 'session token' and is used as a fragment on all URLs within the browser application. BOTH tokens are
 * required in order to authenticate a particular request, after the user has authenticated with their username and
 * password.
 * 
 * NOTE: In order to prevent the cookie token from being overwritten in the case where the user has multiple tabs open
 * within the same browser, the cookie token is preserved where an existing token starting with it already exists in the
 * space. If no other tokens are in existence already, then the cookie token is refreshed along with the session token.
 */
public class AuthTokenCreateCmd extends DBCmd<Pair<String, String>> {

	private static Logger log = LoggerFactory.getLogger(AuthTokenCreateCmd.class);

	/* ============ Dependencies ========== */
	private SecureRandom random;
	private IAuthTokenService authToken;

	/* ============ Injection points ======== */
	@Inject
	public void setRandom(SecureRandom random) {
		this.random = random;
	}

	@Inject
	public void setAuthToken(IAuthTokenService authToken) {
		this.authToken = authToken;
	}

	private final Builder data;

	// transient
	private Integer realUserId;
	private Integer userId;

	public AuthTokenCreateCmd(Builder data) {
		this.data = data;
	}

	@Override
	public Pair<String, String> exec(CoreSession session) throws CommandException {

		this.authorize(session);

		String partOne = this.data.token.getOne();
		String oldPartTwo = this.data.token.getTwo();
		AuthToken token = new AuthToken(this.realUserId, this.userId);

		if (partOne != null && oldPartTwo != null) {
			AuthToken oldToken = this.authToken.get(partOne, oldPartTwo);

			if (oldToken != null) {
				token.updateFrom(oldToken);
			}
		}

		if (token.getPermissions() == null) {
			UserSso sso = this.run(new UserSsoFindByUserIdCmd(token.getUserId()), session);
			token.setPermissions(this.run(new UserFindPermissionsCmd(sso), session));
		}

		if (!LangUtils.hasValue(partOne)) {
			partOne = this.generateId();
		}
		String partTwo = this.generateId();

		this.authToken.create(partOne, partTwo, token);
		return new Pair(partOne, partTwo);
	}

	private void authorize(CoreSession session) throws CommandException {

		// Setup the default situation
		UserSso user = session.getUser();
		this.userId = user.getUserId();
		this.realUserId = user.getUserId();

		// If the subject is attempting to assume the identity of another account, validate their permission
		// and the existence of that account
		if (LangUtils.hasValue(this.data.assumedUsername)) {

			// Check Permission to switch user at all
			this.auth.isAuthorized(session, C42PermissionPro.System.ASSUME_USER);

			// Marshal parameters and build the command
			String assumedUsername = this.data.assumedUsername.get();
			UserFindByUsernameCmd.Builder builder = new UserFindByUsernameCmd.Builder(assumedUsername);
			builder.active(true);
			builder.inclusive(true);

			// Validate user existence and that the subject has authority to manage this account
			List<User> users = this.run(builder.build(), session);
			if (LangUtils.hasElements(users)) {

				// Assume the first one until findByUsername is made unique, as it should be
				User assumedUser = users.get(0);
				this.run(new IsUserManageableCmd(assumedUser, C42PermissionApp.User.UPDATE), session);

				// Setup the override of user information and log it
				this.userId = assumedUser.getUserId();
				UserSso assumedUserSso = new UserSso(assumedUser);
				session = this.auth.getSession(assumedUserSso, user);

				log.info("Assumed Identity: " + assumedUser.getUsername() + " by: " + user.getUsername());
			} else {
				throw new UnauthorizedException("User does not exist; username=" + assumedUsername);
			}
		}

		/*
		 * Check for login permission for the required cluster and org type.
		 */
		int userId = (session.getUser() != null) ? session.getUser().getUserId() : 0;
		IPermission reqPermission = this.serverService.getRequiredLoginPermission(userId);

		if (!this.auth.hasPermission(session, reqPermission)) {
			// AUTH_MIGRATION: Unauthenticated exception, reason: USER_INVALID
			/*
			 * This is the incorrect error to indicate here... should be a basic AuthorizationException indicating that the
			 * necessary permissions weren't present.
			 */
			throw new UnauthorizedException("User not authorized for this application");
		}
	}

	private String generateId() {
		/* Logic below is what's used in the session ID generation code within Jetty */
		long r0 = LangUtils.nextAbsRandom(this.random);
		long r1 = LangUtils.nextAbsRandom(this.random);

		/*
		 * Long.toString(Math.abs(Long.MAX_VALUE),36) == 1y2p0ij32e8e7 (13 chars), so let's make sure the strings built from
		 * r0 and r1 are exactly 13 characters long
		 */
		return StringUtils.leftPad(Long.toString(r0, 36), 13, '0') + StringUtils.leftPad(Long.toString(r1, 36), 13, '0');
	}

	// /**
	// * Determine whether or not there are any existing key entries for the given cookie token.
	// *
	// * @param cookieToken
	// * @return boolean true if they exist; false otherwise
	// */
	// private boolean keyExists(String cookieToken) {
	//
	// // this.space.g
	//
	// IMap<String, CacheItem> hazelcast = Hazelcast.getMap(CoreSpace.DEFAULT.getSpaceName());
	//
	// Set<String> keys = hazelcast.localKeySet(new LikePredicate(new EntryKeyObject(), SpaceNamingStrategy.createKey(
	// DefaultSpaceNamespace.AUTHORIZATION_TOKENS.toString(), cookieToken + "%")));
	// return keys != null && keys.size() > 0;
	// }

	public static class Builder {

		private final Pair<String, String> token;
		private Option<String> assumedUsername = None.getInstance();

		public Builder(Pair<String, String> token) {
			this.token = (token != null) ? token : new Pair<String, String>(null, null);
		}

		public Builder assumedUsername(String assumedUsername) {
			this.assumedUsername = new Some<String>(assumedUsername);
			return this;
		}

		public AuthTokenCreateCmd build() {
			return new AuthTokenCreateCmd(this);
		}
	}
}
