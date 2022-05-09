package com.code42.user;

import com.code42.auth.AutoTokenHandler;
import com.code42.auth.CheckBlockedDeactivatedCmd;
import com.code42.auth.InvalidTokenException;
import com.code42.auth.LoginToken;
import com.code42.auth.LoginTokenHandler;
import com.code42.core.CommandException;
import com.code42.core.auth.TokenAuthenticationFailedException;
import com.code42.core.auth.UnauthenticatedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.org.OrgSso;
import com.code42.org.OrgSsoFindByUserIdCmd;
import com.google.inject.Inject;
import com.google.inject.name.Named;

public class UserLoginByLoginTokenCmd extends DBCmd<CoreSession> {

	/* ================= Dependencies ================= */
	private LoginTokenHandler handler;

	@Inject
	public void setLocal(@Named("login") AutoTokenHandler handler) {
		this.handler = (LoginTokenHandler) handler;
	}

	private String encryptedToken;

	public UserLoginByLoginTokenCmd(String encryptedToken) {
		super();
		this.encryptedToken = encryptedToken;
	}

	String getEncryptedToken() {
		return this.encryptedToken;
	}

	@Override
	public CoreSession exec(CoreSession session) throws UnauthenticatedException, CommandException {

		CoreSession sysadmin = this.auth.getAdminSession();
		try {
			LoginToken token = this.handler.handleInboundToken(this.encryptedToken);
			Integer userId = token.getUserId();
			UserSso user = this.runtime.run(new UserSsoFindByUserIdCmd(userId), sysadmin);

			/*
			 * Check to see if the user or org is deactivated or blocked and throw the appropriate
			 * exception if they are. We need an OrgSso to make that happen.
			 */
			OrgSso org = this.run(new OrgSsoFindByUserIdCmd(userId), sysadmin);
			this.runtime.run(new CheckBlockedDeactivatedCmd(user, org), sysadmin);

			session = this.auth.getSession(user);
		} catch (InvalidTokenException e) {
			throw new TokenAuthenticationFailedException(e.getMessage(), e);
		}

		return session;
	}
}