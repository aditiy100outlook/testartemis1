package com.code42.auth;

import com.code42.core.CommandException;
import com.code42.core.auth.IAuthorizationService;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.user.User;
import com.code42.user.UserFindByIdCmd;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Create a multi-use token for automatic login to this server; this one is specific to the invite process and differs
 * from a normal login token in three key ways; it's multi-use, it does not expire, and it can only apply to user
 * accounts that do not yet have a password set. This password state only exists in the "Invite" user story. Once the
 * password is set on the account, any future attempts to use this token will fail.
 */
public class InviteTokenCreateCmd extends AbstractCmd<String> {

	private static final Logger log = LoggerFactory.getLogger(InviteTokenCreateCmd.class.getName());

	/* ============ Dependencies ========== */
	private IAuthorizationService auth;
	private InviteTokenHandler handler;

	@Inject
	public void setLocal(@Named("invite") AutoTokenHandler handler) {
		this.handler = (InviteTokenHandler) handler;
	}

	@Inject
	public void setAuth(IAuthorizationService auth) {
		this.auth = auth;
	}

	/* User we wish to create the token for */
	private final int userId;
	private final IPermission requiredPermission;

	public InviteTokenCreateCmd(int userId, IPermission requiredPermission) {
		this.userId = userId;
		this.requiredPermission = requiredPermission;
	}

	@Override
	public String exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, this.requiredPermission);

		/*
		 * Note: We are NOT generating the token for the authenticated subject; we are creating one for the given userId.
		 * However, the subject must have permission to make this request on their behalf.
		 */
		User u = this.runtime.run(new UserFindByIdCmd(this.userId), session);

		InviteToken token = new InviteToken(u.getUserId());
		String encryptedToken = this.handler.handleOutboundToken(token);
		log.info("Creating token: " + token + "; tt=" + encryptedToken);
		return encryptedToken;

	}
}
