/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.auth;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.user.User;
import com.code42.user.UserFindByIdQuery;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Validate an incoming password reset token and return the userId it corresponds to.
 */
public class PasswordResetTokenValidateCmd extends DBCmd<User> {

	/* ================= Dependencies ================= */
	private PasswordResetTokenHandler handler;

	@Inject
	public void setLocal(@Named("reset") AutoTokenHandler handler) {
		this.handler = (PasswordResetTokenHandler) handler;
	}

	private final String encryptedToken;

	public PasswordResetTokenValidateCmd(String encryptedToken) {
		this.encryptedToken = encryptedToken;
	}

	@Override
	public User exec(CoreSession session) throws CommandException {

		/*
		 * NOTE: this command is called without an authenticated session. Do NOT depend
		 * on session having a useful value.
		 */

		/*
		 * Decrypt and validate the token.
		 */
		PasswordResetToken token = this.handler.handleInboundToken(this.encryptedToken);

		Integer userId = token.getUserId();
		User user = this.db.find(new UserFindByIdQuery(userId));
		return user;
	}
}
