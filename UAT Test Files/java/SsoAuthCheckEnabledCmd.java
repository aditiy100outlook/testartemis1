package com.code42.ssoauth;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.ssoauth.ICoreSsoAuthService;
import com.google.inject.Inject;

public class SsoAuthCheckEnabledCmd extends DBCmd<Void> {

	public enum Error {
		SSO_DISABLED
	}

	@Inject
	private ICoreSsoAuthService ssoAuthSvc;

	@Override
	public Void exec(CoreSession session) throws CommandException {

		// No authentication is needed here

		if (!this.ssoAuthSvc.isEnabled()) {
			throw new CommandException(Error.SSO_DISABLED, "AUTH:: SsoAuth:: SSO authentication has been disabled");
		}

		return null;
	}

}
