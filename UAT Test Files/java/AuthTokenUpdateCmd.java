package com.code42.auth;

import com.code42.core.CommandException;
import com.code42.core.auth.IAuthTokenService;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.google.inject.Inject;

/**
 * Replace the current contents of the token in the space with this the given values, if and only if an update is due.
 */
public class AuthTokenUpdateCmd extends DBCmd<Void> {

	/* ============ Dependencies ========== */
	private IAuthTokenService authToken;

	/* ============ Injection points ======== */
	@Inject
	public void setAuthToken(IAuthTokenService authToken) {
		this.authToken = authToken;
	}

	private final String keyPartOne;
	private final String keyPartTwo;
	private final AuthToken token;
	private final boolean userInitiated;

	public AuthTokenUpdateCmd(final String keyPartOne, final String keyPartTwo, final AuthToken token,
			final boolean userInitiated) {
		this.keyPartOne = keyPartOne;
		this.keyPartTwo = keyPartTwo;
		this.token = token;
		this.userInitiated = userInitiated;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		if (AuthTokenUtils.isUpdateable(this.token, this.userInitiated)) {

			this.token.updated();
			if (this.userInitiated) {
				this.token.refresh();
			}

			this.authToken.update(this.keyPartOne, this.keyPartTwo, this.token);

		}
		return null;
	}

}
