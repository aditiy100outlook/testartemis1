package com.code42.user;

import com.code42.auth.AuthToken;
import com.code42.auth.AuthTokenFindCmd;
import com.code42.auth.AuthTokenUpdateCmd;
import com.code42.auth.AuthTokenUtils;
import com.code42.auth.CheckBlockedDeactivatedCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.TokenAuthenticationFailedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.core.security.ICryptoService;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.OrgSso;
import com.code42.org.OrgSsoFindByUserIdCmd;
import com.code42.utils.Pair;
import com.google.inject.Inject;

/**
 * Authenticate a user based on an input token.
 * 
 * @author bmcguire
 */
public class UserLoginByTokenCmd extends DBCmd<CoreSession> {

	private static final Logger log = LoggerFactory.getLogger(UserLoginByTokenCmd.class.getName());

	private ICryptoService crypto;

	/* ================= DI injection points ================= */

	@Inject
	public void setCrypto(ICryptoService crypto) {
		this.crypto = crypto;
	}

	private final Pair<String, String> token;
	private final boolean userInitiated;
	private final boolean allowZombieTokens;

	public UserLoginByTokenCmd(String partOne, String partTwo) {
		this(new Pair(partOne, partTwo));
	}

	public UserLoginByTokenCmd(Pair<String, String> token) {
		this(token, true, false);
	}

	public UserLoginByTokenCmd(Pair<String, String> token, boolean userInitiated) {
		this(token, userInitiated, !userInitiated);
	}

	public UserLoginByTokenCmd(Pair<String, String> token, boolean userInitiated, boolean allowZombieTokens) {
		this.token = token;
		this.userInitiated = userInitiated;
		this.allowZombieTokens = allowZombieTokens;
	}

	public Pair<String, String> getToken() {
		return this.token;
	}

	@Override
	public CoreSession exec(CoreSession session) throws CommandException {
		if (this.crypto.isLocked()) {
			throw new TokenAuthenticationFailedException("Invalid token, server locked: " + this.token);
		}

		CoreSession sysadmin = this.auth.getAdminSession();

		if (this.token == null) {
			throw new TokenAuthenticationFailedException("Invalid token: " + this.token);
		}

		AuthToken token = this.run(new AuthTokenFindCmd(this.token.getOne(), this.token.getTwo()), session);

		String key = this.token.getOne() + '-' + this.token.getTwo();
		if (token == null) {
			if (log.isDebugEnabled()) {
				log.debug("AUTH:: Cannot authenticate request.  Token not found in 'space': " + key);
			}
			throw new TokenAuthenticationFailedException("Invalid token: " + key);
		}
		if (!(token instanceof AuthToken)) {
			throw new CommandException("Expected to receive AuthToken from cache, observed {}", token);
		}

		AuthTokenUtils.updateState(token);

		// Update the token, regardless of state
		this.run(new AuthTokenUpdateCmd(this.token.getOne(), this.token.getTwo(), token, this.userInitiated), session);

		if (!token.isActive()) {
			// token is either expired or a zombie; check if allowed to continue
			if (token.isExpired() || !this.allowZombieTokens || !token.isZombie()) {
				throw new TokenAuthenticationFailedException("Expired token: {}", key);
			}
			log.debug("Zombie token: {}", key);
		}

		Integer realUserId = token.getRealUserId();
		Integer userId = token.getUserId();
		UserSso user = this.runtime.run(new UserSsoFindByUserIdCmd(userId), sysadmin);

		/*
		 * Check to see if the user or org is deactivated or blocked and throw the appropriate exception if they are. We
		 * need an OrgSso to make that happen.
		 */
		OrgSso org = this.run(new OrgSsoFindByUserIdCmd(userId), sysadmin);
		this.runtime.run(new CheckBlockedDeactivatedCmd(user, org), sysadmin);

		UserSso realUser = this.runtime.run(new UserSsoFindByUserIdCmd(realUserId), sysadmin);
		CoreSession userSession = this.auth.getSession(user, realUser, token.getPermissions());

		return userSession;
	}
}
