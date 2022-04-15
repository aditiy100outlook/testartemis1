package com.code42.auth;

import com.code42.core.CommandException;
import com.code42.core.auth.IAuthorizationService;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.user.UserSso;
import com.code42.user.UserSsoFindByUserIdCmd;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Create a single-use token for use in the password reset user story. The resulting token is single-use, expires after
 * one hour, and can only be used to access the password reset resources.
 */
public class PasswordResetTokenCreateCmd extends DBCmd<String> {

	/* ============ Dependencies ========== */
	private IAuthorizationService auth;
	private PasswordResetTokenHandler handler;

	@Inject
	public void setLocal(@Named("reset") AutoTokenHandler handler) {
		this.handler = (PasswordResetTokenHandler) handler;
	}

	@Inject
	public void setAuth(IAuthorizationService auth) {
		this.auth = auth;
	}

	/* User we wish to create the token for */
	private final int userId;
	private final IPermission appLoginPermission;

	public PasswordResetTokenCreateCmd(int userId, IPermission appLoginPermission) {
		this.userId = userId;
		this.appLoginPermission = appLoginPermission;
	}

	@Override
	public String exec(CoreSession session) throws CommandException {

		/*
		 * Note: We are NOT generating the token for the authenticated subject; we are creating one for the given userId.
		 */
		UserSso u = null;
		u = this.run(new UserSsoFindByUserIdCmd(this.userId), session);
		if (u == null) {
			throw new CommandException("Unable to load user; userId=" + this.userId);
		}

		/*
		 * Check for login permission for the required application
		 */
		CoreSession tempSession = this.auth.getSession(u);
		this.auth.isAuthorized(tempSession, this.appLoginPermission);

		PasswordResetToken token = new PasswordResetToken(u.getUserId());
		return this.handler.handleOutboundToken(token);

	}
}
