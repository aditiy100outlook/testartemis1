package com.code42.auth;

import com.code42.core.CommandException;
import com.code42.core.auth.IAuthTokenService;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.google.inject.Inject;

/**
 * Find the AuthToken associated with the given session keys.
 */
public class AuthTokenFindCmd extends DBCmd<AuthToken> {

	/* ============ Dependencies ========== */
	private IAuthTokenService authToken;

	/* ============ Injection points ======== */
	@Inject
	public void setAuthToken(IAuthTokenService authToken) {
		this.authToken = authToken;
	}

	private final String keyPartOne;
	private final String keyPartTwo;

	public AuthTokenFindCmd(String keyPartOne, String keyPartTwo) {
		this.keyPartOne = keyPartOne;
		this.keyPartTwo = keyPartTwo;
	}

	@Override
	public AuthToken exec(CoreSession session) throws CommandException {
		return this.authToken.get(this.keyPartOne, this.keyPartTwo);
	}

}
