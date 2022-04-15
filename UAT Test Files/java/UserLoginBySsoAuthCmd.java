package com.code42.ssoauth;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.code42.auth.CheckBlockedDeactivatedCmd;
import com.code42.backup.central.ICentralService;
import com.code42.core.CommandException;
import com.code42.core.auth.AuthenticationException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.OrgSso;
import com.code42.org.OrgSsoFindByUserIdCmd;
import com.code42.user.User;
import com.code42.user.UserFindByUsernameCmd;
import com.code42.user.UserSso;
import com.code42.user.UserSsoFindByUserIdCmd;
import com.code42.user.UserUpdateCmd;
import com.code42.utils.LangUtils;
import com.code42.utils.Pair;
import com.google.inject.Inject;

/**
 * Completes the Single Sign-On sequence of events for a web session. AuthenticationException is thrown if user is not
 * valid for any reason.
 * <p>
 * SsoAuth via CPD code path starts at com.backup42.desktop.view.LoginPanel.handleSsoAuthChecked()
 */
public class UserLoginBySsoAuthCmd extends AbstractCmd<Pair<CoreSession, SsoAuthUser>> {

	private static final Logger log = LoggerFactory.getLogger(UserLoginBySsoAuthCmd.class);

	public enum Error {
		USER_IS_NOT_SSO, UNREGISTERED_USER
	}

	@Inject
	private ICoreSsoAuthService ssoAuthSvc;

	@Inject
	private ICentralService central;

	private HttpServletRequest request;

	public UserLoginBySsoAuthCmd(HttpServletRequest request) {
		super();
		this.request = request;
	}

	@Override
	public Pair<CoreSession, SsoAuthUser> exec(CoreSession session) throws CommandException {
		log.trace("AUTH:: SsoAuth:: entering UserLoginBySsoAuthCmd");

		// Make sure SSO is enabled before continuing.
		this.run(new SsoAuthCheckEnabledCmd(), session);

		// Note that no standard authentication or authorization is needed to run this command
		// because this is an authentication command.
		SsoAuthUser ssoAuthUser = null;
		boolean authenticated = false;

		try {
			ssoAuthUser = this.ssoAuthSvc.validateAssertion(this.request);

			if (ssoAuthUser == null || !ssoAuthUser.isSsoAuthenticated()) {
				log.info("AUTH:: SsoAuth:: User could not be authenticated");
				throw new AuthenticationException("AUTH:: SsoAuth:: User could not be authenticated");
			}

			assert ssoAuthUser.getUsername() != null;

			CoreSession sysadmin = this.auth.getAdminSession();

			//
			// Find local user by username.
			// I decided not to use the DirectoryFindUserAnyCmd because that will search LDAP directories if any are defined
			// and we don't want that.
			//
			UserFindByUsernameCmd cmd = new UserFindByUsernameCmd.Builder(ssoAuthUser.getUsername()).build();
			List<User> users = this.runtime.run(cmd, sysadmin);

			if (!users.isEmpty()) {
				if (users.size() > 1) {
					log.info(
							"AUTH:: SsoAuth:: Multiple users with the same username ({}) were found in the local database.  Login cannot proceed.",
							ssoAuthUser.getUsername());
					throw new CommandException(
							"AUTH:: SsoAuth:: Multiple users with the same username ({}) were found in the local database.  Login cannot proceed.",
							ssoAuthUser.getUsername());
				}

				//
				// Get the UserSso
				//
				User user = users.get(0);
				Integer userId = user.getUserId();
				UserSso userSso = this.runtime.run(new UserSsoFindByUserIdCmd(userId), sysadmin);

				if (!this.ssoAuthSvc.isOrgEnabled(userSso.getOrgId())) {
					log.info("AUTH:: SsoAuth:: Username {} found, but orgId {} is not using SSO for authentication.", ssoAuthUser
							.getUsername(), userSso.getOrgId());
					throw new AuthenticationException(Error.USER_IS_NOT_SSO,
							"AUTH:: SsoAuth:: Username {} found, but orgId {} is not using SSO for authentication.", ssoAuthUser
									.getUsername(), userSso.getOrgId());
				}

				ssoAuthUser.setUserId(userId);

				//
				// If the user or org is deactivated or blocked throw the appropriate exception.
				//
				OrgSso org = this.run(new OrgSsoFindByUserIdCmd(userId), sysadmin);
				this.runtime.run(new CheckBlockedDeactivatedCmd(userSso, org), sysadmin);

				session = this.auth.getSession(userSso);

				//
				// Update any empty local user fields from the SSO user object
				//
				boolean foundFieldsToUpdate = false;
				UserUpdateCmd.Builder builder = new UserUpdateCmd.Builder(userId);
				if (!LangUtils.hasValue(user.getEmail()) && LangUtils.hasValue(ssoAuthUser.getEmail())) {
					builder.email(ssoAuthUser.getEmail());
					foundFieldsToUpdate = true;
				}
				if (!LangUtils.hasValue(user.getFirstName()) && LangUtils.hasValue(ssoAuthUser.getFirstName())) {
					builder.firstName(ssoAuthUser.getFirstName());
					foundFieldsToUpdate = true;
				}
				if (!LangUtils.hasValue(user.getLastName()) && LangUtils.hasValue(ssoAuthUser.getLastName())) {
					builder.lastName(ssoAuthUser.getLastName());
					foundFieldsToUpdate = true;
				}
				if (foundFieldsToUpdate) {
					UserUpdateCmd userUpdateCmd = builder.build();
					this.runtime.run(userUpdateCmd, session);
				}

				authenticated = true;
			} else {
				// User not found in local database
				if (ssoAuthUser.isLogin()) {
					// We're not registering so there must be a local user found
					log.info("AUTH:: SsoAuth:: User not found in local database: {}", ssoAuthUser.getUsername());
					throw new AuthenticationException(Error.UNREGISTERED_USER,
							"AUTH:: SsoAuth:: User not found in local database: {}", ssoAuthUser.getUsername());
				}
				authenticated = true;
			}

			return new Pair(session, ssoAuthUser);
		} catch (CommandException ce) {
			throw ce;
		} catch (Throwable t) {
			throw new CommandException("AUTH:: SsoAuth:: Unexpected error authenticating SSO user", t);
		} finally {
			if (authenticated) {
				this.ssoAuthSvc.authenticateUser(ssoAuthUser);
			} else {
				this.ssoAuthSvc.removeUser(ssoAuthUser);
			}
			//
			// NOTE: This should always happen for client apps.... even if authentication was unsuccessful.
			// The ssoAuthUser will not be placed into the client session if the user is not authenticated or valid for login
			// to CrashPlan.
			//
			// If the ssoAuthUser is null here, there is nothing we can do.
			if (ssoAuthUser != null && ssoAuthUser.isClientApp()) {
				//
				// This is a GUI client app (versus a browser). Let client know to send the standard login/register message now.
				//
				// The ssoAuthUser will only be in the session if we received a valid assertion from the identity
				// provider and if ...
				// 1) user is logging in and the user exists in the local database.
				// or 2) user is registering.
				//
				this.central.getPeer().sendSsoAuthFinalize(ssoAuthUser);
			}
		}
	}
}
