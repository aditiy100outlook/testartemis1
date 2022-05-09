package com.code42.auth;

import com.code42.core.CommandException;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.user.User;
import com.code42.user.UserFindByIdCmd;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Create a single-use token for automatic login to this server; typical use is to generate such a token for inclusion
 * on a URL that is then used to automatically log the already authenticated user into the website, but that may not be
 * the only use.
 */
public class LoginTokenCreateCmd extends DBCmd<String> {

	/* ============ Dependencies ========== */
	private LoginTokenHandler handler;

	@Inject
	public void setTokenHandler(@Named("login") AutoTokenHandler handler) {
		this.handler = (LoginTokenHandler) handler;
	}

	/* User we wish to create the token for */
	private final int userId;
	private IPermission appLoginPermission;

	public LoginTokenCreateCmd(int userId) {
		this(userId, null);
	}

	public LoginTokenCreateCmd(int userId, IPermission appLoginPermission) {
		this.userId = userId;
		this.appLoginPermission = appLoginPermission;
	}

	@Override
	public String exec(CoreSession session) throws CommandException {

		/*
		 * Note: We are NOT generating the token for the authenticated subject; we are creating one for the given userId.
		 * However, the subject must have permission to make this request on their behalf.
		 */
		User u = this.runtime.run(new UserFindByIdCmd(this.userId), session);

		// If no login permission was provided use the defaults
		if (this.appLoginPermission == null) {
			this.appLoginPermission = this.serverService.getRequiredLoginPermission(this.userId);
		}

		/*
		 * Check for login permission for the required application
		 */
		if (!this.auth.hasPermission(session, this.appLoginPermission)) {
			// AUTH_MIGRATION: Unauthenticated exception, reason: USER_INVALID
			/*
			 * This is the incorrect error to indicate here... should be a basic AuthorizationException indicating that the
			 * necessary permissions weren't present.
			 */
			throw new UnauthorizedException("User not authorized for this application");
		}

		if (!this.auth.hasPermission(this.auth.getSession(u), this.appLoginPermission)) {
			/*
			 * The user we are generating the token for must have the correct login permission as well
			 */
			throw new UnauthorizedException("User not authorized for this application");
		}

		LoginToken token = new LoginToken(u.getUserId());
		return this.handler.handleOutboundToken(token);
	}
}
