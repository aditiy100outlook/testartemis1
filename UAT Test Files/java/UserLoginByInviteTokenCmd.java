package com.code42.user;

import com.code42.auth.AutoTokenHandler;
import com.code42.auth.CheckBlockedDeactivatedCmd;
import com.code42.auth.InvalidTokenException;
import com.code42.auth.InviteToken;
import com.code42.auth.InviteTokenHandler;
import com.code42.core.CommandException;
import com.code42.core.auth.UnauthenticatedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.org.OrgSso;
import com.code42.org.OrgSsoFindByUserIdCmd;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * User received an invitation email with a link in it to register with a CrashPlan PROe Server. This command
 * automatically logs them in enough to register themselves.
 */
public class UserLoginByInviteTokenCmd extends DBCmd<CoreSession> {

	/* ================= Dependencies ================= */
	private InviteTokenHandler handler;

	@Inject
	public void setLocal(@Named("invite") AutoTokenHandler handler) {
		this.handler = (InviteTokenHandler) handler;
	}

	private String encryptedToken;

	public UserLoginByInviteTokenCmd(String encryptedToken) {
		super();
		this.encryptedToken = encryptedToken;
	}

	@Override
	public CoreSession exec(CoreSession session) throws CommandException {

		CoreSession sysadmin = this.auth.getAdminSession();
		try {
			InviteToken token = this.handler.handleInboundToken(this.encryptedToken);
			Integer userId = token.getUserId();
			UserSso user = this.runtime.run(new UserSsoFindByUserIdCmd(userId), sysadmin);

			/*
			 * Check to see if the user or org is deactivated or blocked and throw the appropriate exception if they are. We
			 * need an OrgSso to make that happen.
			 */
			OrgSso org = this.run(new OrgSsoFindByUserIdCmd(userId), sysadmin);
			this.runtime.run(new CheckBlockedDeactivatedCmd(user, org), sysadmin);

			session = this.auth.getSession(user);
		} catch (InvalidTokenException e) {
			throw new UnauthenticatedException(e.getMessage(), e);
		}

		return session;
	}
}
