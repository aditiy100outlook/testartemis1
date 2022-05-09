package com.code42.ssoauth;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.code42.core.CommandException;
import com.code42.core.auth.AuthenticationException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.utils.LangUtils;
import com.google.inject.Inject;

/**
 * This command kicks off the Single Sign-On sequence of events for a web session.
 * <p>
 * SsoAuth via CPD code path starts at com.backup42.desktop.view.LoginPanel.handleSsoAuthChecked()
 */
public class SsoAuthLoginCmd extends AbstractCmd<Void> {

	private static final Logger log = LoggerFactory.getLogger(SsoAuthLoginCmd.class);

	@Inject
	private ICoreSsoAuthService ssoAuthSvc;

	private HttpServletRequest request;
	private HttpServletResponse response;

	public SsoAuthLoginCmd(HttpServletRequest request, HttpServletResponse response) {
		super();
		this.request = request;
		this.response = response;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		log.trace("AUTH:: SsoAuth:: entering SsoAuthLoginCmd");

		// Make sure SSO is enabled before continuing.
		this.run(new SsoAuthCheckEnabledCmd(), session);

		// This uuid query parameter is provided by the client app only
		final String uuid = this.request.getParameter("uuid");

		SsoAuthUser ssoAuthUser;
		if (LangUtils.hasValue(uuid)) {
			// Client - CPS/CPD
			ssoAuthUser = this.ssoAuthSvc.getUser(uuid);
		} else {
			// Browser
			ssoAuthUser = this.ssoAuthSvc.createAuthUser(false, null);
		}

		// Make sure we have an auth user, if not then must be client with invalid uuid.
		if (ssoAuthUser == null) {
			log.error("Client tried to login via SSO with an invalid UUID: {}", uuid);
			throw new AuthenticationException("Invalid uuid={}", uuid);
		}

		// This call handles the HTTP response so we don't have to do anything.
		this.ssoAuthSvc.requestAuthentication(this.request, this.response, ssoAuthUser.getAuthToken());

		if (!this.response.isCommitted()) {
			throw new CommandException(
					"AUTH:: SsoAuth:: An error must have occurred. The HTTP response was not committed inside the ICoreSsoAuthService#requestAuthentication(...)");
		}

		return null;
	}
}
