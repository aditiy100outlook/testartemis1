package com.code42.user;

import java.util.LinkedList;
import java.util.List;

import com.backup42.account.AccountServices;
import com.backup42.app.cpc.clusterpeer.ISlavePeerController;
import com.backup42.app.license.MasterLicenseService;
import com.backup42.common.CPErrors.Error;
import com.backup42.history.CpcHistoryLogger;
import com.code42.app.AppConstants;
import com.code42.auth.AuthenticatorFindByOrgCmd;
import com.code42.auth.CheckBlockedDeactivatedCmd;
import com.code42.auth.DirectoryEntryAuthenticateAnyCmd;
import com.code42.auth.PasswordUtil;
import com.code42.core.CommandException;
import com.code42.core.auth.AuthenticationException;
import com.code42.core.auth.Authenticator;
import com.code42.core.auth.MalformedCredentialsException;
import com.code42.core.auth.PasswordAuthenticationFailedException;
import com.code42.core.auth.TooManyFailedAttemptsException;
import com.code42.core.auth.UnauthenticatedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.directory.Directory;
import com.code42.core.directory.DirectoryEntry;
import com.code42.core.impl.DBCmd;
import com.code42.core.security.CryptoInvalidPasswordException;
import com.code42.core.security.ICryptoService;
import com.code42.core.security.ILoginMonitoringService;
import com.code42.crypto.StringHasher;
import com.code42.directory.DirectoryFindAllByOrgCmd;
import com.code42.directory.DirectoryFindUserAnyCmd;
import com.code42.license.MasterLicenseKeyChangeCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.OrgSso;
import com.code42.org.OrgSsoFindByOrgIdCmd;
import com.code42.org.OrgSsoFindByUserIdCmd;
import com.code42.server.cluster.ClusterFindMyMasterClusterQuery;
import com.code42.server.cluster.MasterCluster;
import com.code42.utils.LangUtils;
import com.code42.utils.Pair;
import com.google.inject.Inject;

/**
 * Authenticate the requested user with the request password, returning a CoreSession instance if we're successful.<br>
 * <br>
 * Note that by default "deferred" authentication is <b>not</b> allowed. If you wish to support deferred authentication
 * you have to use the appropriate constructor below.
 */
public class UserLoginByPasswordCmd extends DBCmd<CoreSession> {

	private static final Logger log = LoggerFactory.getLogger(UserLoginByPasswordCmd.class.getName());

	@Inject
	private ILoginMonitoringService loginMonitor;

	@Inject
	private ISlavePeerController spc;

	@Inject
	private ICryptoService crypto;

	private final String username;
	private final String password;
	private final String mlk;
	private final boolean allowDeferred;

	private final String originalUsername;
	private final String originalPassword;

	private final String serverPassword;

	public UserLoginByPasswordCmd(String username, String password) throws AuthenticationException {
		this(username, password, false, "", null);
	}

	public UserLoginByPasswordCmd(String username, String password, String mlk, String serverPassword)
			throws AuthenticationException {
		this(username, password, false, mlk, serverPassword);
	}

	public UserLoginByPasswordCmd(String username, String password, boolean allowDeferred) throws AuthenticationException {
		this(username, password, allowDeferred, "", null);
	}

	private UserLoginByPasswordCmd(String username, String password, boolean allowDeferred, String mlk,
			String serverPassword) throws AuthenticationException {
		// We cannot have a session here if there is an MLK to insert...due to Hibernate screwing us once again.
		// See Peter for a 45 minute rant on the need to fork Hibernate and yank out every last piece of caching code.
		// See MasterLicenseKeyChange...it cannot have a session or it returns the old MLK instead of the new one.
		super(false/* false=no session */);

		if (!LangUtils.hasValue(username)) {
			// AUTH_MIGRATION: Reason: USER_INVALID
			throw new MalformedCredentialsException("Zero length username entered for authentication");
		}
		if (!LangUtils.hasValue(password)) {
			// AUTH_MIGRATION: Reason: PASSWORD_INVALID
			throw new PasswordAuthenticationFailedException("Zero length password entered for authentication");
		}

		this.username = username.toLowerCase().trim();
		this.password = password.trim();
		this.mlk = mlk;
		this.serverPassword = serverPassword;
		this.allowDeferred = allowDeferred;

		/* Preserve the actual username and password input by the user... in case we ever want to get back to it */
		this.originalUsername = username;
		this.originalPassword = password;
	}

	public String getUsername() {
		return this.username;
	}

	public String getPassword() {
		return this.password;
	}

	public String getMlk() {
		return this.mlk;
	}

	public String getOriginalUsername() {
		return this.originalUsername;
	}

	public String getOriginalPassword() {
		return this.originalPassword;
	}

	public boolean isAllowDeferred() {
		return this.allowDeferred;
	}

	@Override
	public CoreSession exec(CoreSession session) throws CommandException {

		/*
		 * TODO: I believe this is entirely the wrong thing to do.
		 * 
		 * Attempting to authenticate with a given username/password combination has the ability to expose sensitive
		 * information about a given user; at a minimum it will expose the fact that a given users password is NOT some
		 * string S. In other contexts we constrain the ability of this information to "leak out" by passing the input
		 * session along to every call to a subcommand. We do NOT do this here: instead we silently escalate the privilege
		 * to sysadmin and continue on with our business. The net result is that an authenticated user foo (allegedly
		 * constrained to a sandbox in which they can only see orgs they have permission to see) can now ask questions about
		 * the credentials of any other user on the system even though that user doesn't have permission to see info about
		 * the user or org they're asking about.
		 * 
		 * The question is not just about privilege escalation; that happens at many other points in the system and is
		 * perfectly acceptable. The problem is that we're doing so when user data is involved. If we want to use this
		 * command to query any arbitrary user in the system then the caller should have to somehow obtain the sysadmin
		 * privilege and pass that into the exec() call for this command. Silently raising privilege after the fact for this
		 * kind of command is a significant error.
		 * 
		 * The current code is copied from the old implementation. I can't completely remove it yet because
		 * HTTPAuthenticationStrategy makes use of this commands and it doesn't have access to a CoreSession (sysadmin or
		 * otherwise). Until that strategy is refactored into individual commands there's not a clean way to remove this
		 * wart within the system as currently written.
		 */
		CoreSession sysadmin = this.auth.getAdminSession();

		/* ================= Process MLK ========================= */
		/*
		 * Check that the server has a registered mlk otherwise it will set the provided mlk before authentication.
		 */
		/*
		 * TODO: I'm not sure this is the right place for this check. User authentication shouldn't be dependent on the
		 * presence or lack of a master license key; it's a function of the directories and authenticators in play for a
		 * given org. If we want to enforce something like this as a policy decision that's fine, but in that case this
		 * check should be moved further up the stack (i.e. the caller of this command should never even call it if an MLK
		 * isn't installed).
		 */
		if (MasterLicenseService.getInstance().getMasterLicense() == null) {
			if (!LangUtils.hasValue(this.mlk)) {
				throw new UnauthenticatedException("Master License is required for first login.");
			}
			this.runtime.run(new MasterLicenseKeyChangeCmd(this.mlk), sysadmin);
		}

		/* ================= Main processing ======================= */
		/*
		 * Username is not unique in and of itself; username + org is. In order to work around this problem we adopt
		 * something of a hack here. Even better: unfortunately the approach below means we have to hit the database on
		 * every username/password login; there's nowhere else to go to get the definitive set of users for a given
		 * username. The space is currently lazily loaded so there's no guarantee that all possible matches would've been
		 * loaded into the cache at any given point in time.
		 */

		/* Find all users with the input username */
		List<User> users = this.runtime.run(new UserFindByUsernameCmd.Builder(this.username).active(true).inclusive(false)
				.build(), sysadmin);
		if (users.size() == 0) {
			if (!this.env.isMaster()) {
				MasterCluster masterCluster = this.db.find(new ClusterFindMyMasterClusterQuery(this.env.getMyClusterId()));
				Error error = this.spc.sendValidateUser(masterCluster.getClusterGuid(), this.username, this.password);
				if (error == null) {
					users = this.runtime.run(new UserFindByUsernameCmd.Builder(this.username).active(true).inclusive(false)
							.build(), sysadmin);
				} else {
					// Generic auth error, we don't want them to have any idea what went wrong for security reasons. Rule 1.
					log.info("Could not find database entry and not validated via master for user with username " + this.username);
					throw new PasswordAuthenticationFailedException("Unable to authenticate user {}", this.username);
				}

			} else {
				// Generic auth error, we don't want them to have any idea what went wrong for security reasons. Rule 1.
				log.info("Could not find database entry for user with username " + this.username);
				throw new PasswordAuthenticationFailedException("Unable to authenticate user {}", this.username);
			}
		}

		if (this.loginMonitor.isDisabled(this.username)) {
			CpcHistoryLogger.info(session, "Attempted to login with an invalid password too many times");
			throw new TooManyFailedAttemptsException("User has attempted to login with an invalid password too many times");
		}

		/* Find the orgs for each user discovered above */
		List<Pair<User, DirectoryEntry>> matches = new LinkedList<Pair<User, DirectoryEntry>>();
		for (User candidate : users) {

			/* For each of these orgs get the directories and authenticators defined for that org */
			OrgSso org = this.runtime.run(new OrgSsoFindByOrgIdCmd(candidate.getOrgId()), sysadmin);
			if (org == null) {
				log.error("Could not find org for orgId:{} of user:{}", candidate.getOrgId(), candidate);
				continue; // Skip this candidate
			}
			List<Directory> directories = this.runtime.run(new DirectoryFindAllByOrgCmd(org), sysadmin);
			List<Authenticator> authenticators = this.runtime.run(new AuthenticatorFindByOrgCmd(org), sysadmin);

			// They can't login via an SSO Auth org via password. They MUST use SSO identity provider to login.
			if (AccountServices.getInstance().isOrgSsoAuth(candidate.getOrgId())) {
				throw new PasswordAuthenticationFailedException(
						"Unable to authenticate user {}, SsoAuth org can't login via password", this.username, org);
			}

			/* ================= Special cases ======================= */
			/*
			 * Special case; if we're presented with a deferred password and the command allows for deferred execution return
			 * immediately; no authentication is performed at this point.
			 */
			boolean isDeferredPassword = LangUtils.hasValue(this.password)
					&& StringHasher.checkPassword(AppConstants.DEFERRED_PASSWORD, this.password);
			if (this.allowDeferred && isDeferredPassword && !UserServices.getInstance().isDirLocal(directories)) {
				return this.auth.getSession(users.get(0), true);
			}

			/*
			 * Lookup a directory entry in the collection of directories and try to authenticate it using only the
			 * authenticators for the user's org
			 */
			DirectoryEntry candidateEntry = this.runtime.run(new DirectoryFindUserAnyCmd.Builder(this.username).directories(
					directories).retries(2).build(), sysadmin);
			if (candidateEntry == null) {

				/*
				 * This shouldn't happen in practice; the DB said the user exists in the org but the directories backing that
				 * org couldn't find them. Most likely explanation is that they were assigned to this org initially but
				 * subsequently moved by an administrator.
				 */
				continue;
			}

			/* If we have a non-null value and didn't throw an exception we should try to authenticate this user */
			boolean authenticated = this.runtime.run(new DirectoryEntryAuthenticateAnyCmd.Builder(candidateEntry,
					this.password).authenticators(authenticators).retries(2).build(), sysadmin);
			if (!authenticated) {
				boolean disabled = this.loginMonitor.loginFailed(this.username);
				if (disabled) {
					CpcHistoryLogger.info(this.username, 0,
							"AUTH:: Too many invalid password attempts. Account has been disabled.");
				}
				throw new PasswordAuthenticationFailedException("Unable to authenticate user {}", this.username);
			}

			// Make sure the server isn't locked
			if (this.crypto.isLocked()) {
				boolean failed = true; // assume failed for security reasons
				if (authenticated) {
					try {
						this.crypto.unlock(session, this.serverPassword);
						failed = false;
					} catch (CryptoInvalidPasswordException e) {
						log.warn("Failed to unlock server, invalid keystore password.");
						// Fall-through to throw a generic auth error, we don't want to hint to the problem.
					} catch (Throwable e) {
						log.warn("Failed to unlock server", e);
						// Fall-through to throw a generic auth error, we don't want to hint to the problem.
					}
				}
				if (failed) {
					// Generic auth error, we don't want them to have any idea what went wrong for security reasons. Rule 1.
					throw new PasswordAuthenticationFailedException("Unable to authenticate user {}", this.username);
				}
			}

			/*
			 * If we're still here, congrats, you've been authenticated... but there might be more than one of you so we can't
			 * return yet.
			 */
			matches.add(new Pair<User, DirectoryEntry>(candidate, candidateEntry));
		}

		/*
		 * If we have multiple matches there's nothing we can do but throw a horrendous exception here. This isn't
		 * technically correct (the user did present valid credentials after all) but as discussed above there's just
		 * nothing else we can do here.
		 * 
		 * Probably best to throw something like NoUniqueDirectoryEntryException here in order to clearly indicate what went
		 * wrong. Would have to implement a new type rather than re-using NoUniqueDirectoryEntryException.. maybe
		 * NoUniqueUserException?
		 */
		if (matches.size() > 1) {
			CpcHistoryLogger.info(this.username, 0,
					"AUTH:: Multiple username matches found.  Could not process login by password.");
			throw new NoUniqueUserException("Multiple viable matches found for username " + this.username);
		}
		if (matches.size() == 0) {
			// Generic auth error, we don't want them to have any idea what went wrong for security reasons. Rule 1.
			this.loginMonitor.loginFailed(this.username);
			log.info("Could not find any users with username " + this.username + " which matched the input credentials");
			throw new PasswordAuthenticationFailedException("Unable to authenticate user {}", this.username);
		}

		User matchUser = matches.get(0).getOne();

		/*
		 * While the login has not technically succeeded yet, from an invalid password monitoring standpoint all we care
		 * about is that the user supplied a valid password... so we mark it succeeded.
		 */
		this.loginMonitor.loginSucceeded(this.username);

		// This should only be needed for invited LDAP users, but it seems like a good check in any case
		if (!LangUtils.hasValue(matchUser.getPassword())) {
			// At this point, the user is no longer "invited" so they need to have a password, even if it is a generated one.
			try {
				this.db.beginTransaction();
				User u = this.db.find(new UserFindByIdQuery(matchUser.getUserId()));
				u.setPassword(StringHasher.C42.hash(PasswordUtil.generatePassword(20)));
				this.db.update(new UserUpdateQuery(u));
				this.db.commit();
			} catch (Throwable t) {
				this.db.rollback();
			} finally {
				this.db.endTransaction();
			}
		}

		/*
		 * Check to see if the user or org is deactivated or blocked and throw the appropriate exception if they are. We
		 * need an OrgSso to make that happen.
		 */
		UserSso matchUserSso = this.runtime.run(new UserSsoFindByUserIdCmd(matchUser.getUserId()), sysadmin);
		OrgSso matchOrgSso = this.runtime.run(new OrgSsoFindByUserIdCmd(matchUser.getUserId()), sysadmin);
		this.runtime.run(new CheckBlockedDeactivatedCmd(matchUserSso, matchOrgSso), sysadmin);

		return this.auth.getSession(matchUser);
	}
}
