package com.code42.auth;

import java.util.regex.Pattern;

import com.code42.core.CommandException;
import com.code42.core.auth.IAuthTokenService;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.google.inject.Inject;

/**
 * Delete a given AuthToken or set of AuthTokens. In this case, a "set of AuthTokens" is equal to all tokens associated
 * with the same cookie token (the first part of any given token pair).
 */
public class AuthTokenDeleteCmd extends DBCmd<Void> {

	/*
	 * Token must be either the first half of an authtoken (26 characters) or a whole authtoken (two 26 character halves
	 * with a dash in between)
	 */
	final static Pattern AUTH_TOKEN_PATTERN = Pattern.compile("\\w{26}(-\\w{26})?");

	/* ============ Dependencies ========== */

	private IAuthTokenService authToken;

	/* ============ Injection points ======== */
	@Inject
	public void setAuthToken(IAuthTokenService authToken) {
		this.authToken = authToken;
	}

	private final String token;

	public AuthTokenDeleteCmd(String token) {
		this.token = token;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		//
		// Why is there no authorization in this command?
		// Please add or document
		//

		if (this.token == null || session == null) {
			return null;
		}

		// This check is important because without it a user could provide an empty string and wipe out all tokens
		if (!AUTH_TOKEN_PATTERN.matcher(this.token).matches()) {
			throw new IllegalArgumentException("Invalid token provided: " + this.token);
		}

		this.authToken.delete(this.token);

		return null;
	}

}
