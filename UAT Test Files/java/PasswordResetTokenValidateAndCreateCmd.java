/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.auth;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.user.User;
import com.code42.utils.Pair;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Validate an incoming password reset token and issue a new one.
 */
public class PasswordResetTokenValidateAndCreateCmd extends DBCmd<Pair<User, String>> {

	/* ================= Dependencies ================= */
	private PasswordResetTokenHandler handler;

	@Inject
	public void setLocal(@Named("reset") AutoTokenHandler handler) {
		this.handler = (PasswordResetTokenHandler) handler;
	}

	private final String encryptedToken;

	public PasswordResetTokenValidateAndCreateCmd(String encryptedToken) {
		this.encryptedToken = encryptedToken;
	}

	@Override
	public Pair<User, String> exec(CoreSession session) throws CommandException {
		User user = this.runtime.run(new PasswordResetTokenValidateCmd(this.encryptedToken), session);

		PasswordResetToken newToken = new PasswordResetToken(user.getUserId());
		String encryptedToken = this.handler.handleOutboundToken(newToken);

		return new Pair(user, encryptedToken);
	}
}
