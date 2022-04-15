/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.user;

import com.backup42.CpcConstants;
import com.code42.auth.PasswordResetTokenValidateCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.NotFoundException;
import com.code42.core.impl.DBCmd;

/**
 * Set a user's password has a result of a password reset operation
 */
public class UserResetPasswordCmd extends DBCmd<Boolean> {

	public enum Error {
		PASSWORD_INVALID, //
		USER_NOT_FOUND
	}

	private final String encryptedToken;
	private final String password;

	public UserResetPasswordCmd(String encryptedToken, String password) {
		this.encryptedToken = encryptedToken;
		this.password = password;
	}

	@Override
	public Boolean exec(CoreSession session) throws CommandException {
		boolean result = false;

		try {
			User user = this.runtime.run(new PasswordResetTokenValidateCmd(this.encryptedToken), null);
			if (user == null || user.getUserId() == null) {
				throw new NotFoundException(Error.USER_NOT_FOUND, "Unable to set user's password; token data is invalid");
			}
			if (user.getUserId().equals(CpcConstants.Users.ADMIN_ID)) {
				throw new UnauthorizedException("Unable to reset the root admin user password with this command");
			}

			final UserPasswordUpdateCmd.Builder builder = new UserPasswordUpdateCmd.Builder(user.getUserId(), this.password);
			this.run(builder.build(), this.auth.getAdminSession());

			result = true;

		} catch (UnauthorizedException ue) {
			throw ue;
		} catch (Exception e) {
			throw new CommandException("Unable to set password", e);
		}

		return result;
	}
}
