/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.user;

import java.util.HashMap;
import java.util.Map;

import com.backup42.EmailPaths;
import com.backup42.app.cpc.CPCBackupProperty;
import com.backup42.common.OrgType;
import com.backup42.server.CpcEmailContext;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.content.IContentService;
import com.code42.core.content.IResource;
import com.code42.core.impl.DBCmd;
import com.code42.email.Emailer;
import com.code42.exception.DebugRuntimeException;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.utils.LangUtils;
import com.code42.utils.SystemProperties;
import com.code42.utils.SystemProperty;
import com.google.inject.Inject;

/**
 * 
 * Send a welcome email to new users. This is currently done for Consumer users only.
 */
public class UserSendWelcomeEmail extends DBCmd<Void> {

	private static Logger log = LoggerFactory.getLogger(UserSendWelcomeEmail.class.getName());

	@Inject
	private IContentService contentService;

	// Properties
	private final User user;
	private final OrgType orgType;

	public UserSendWelcomeEmail(User user, OrgType orgType) {
		this.user = user;
		this.orgType = orgType;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		this.runtime.run(new IsUserManageableCmd(this.user, C42PermissionApp.User.READ), session);

		if (OrgType.CONSUMER != this.orgType) {
			return null; // ignore
		}

		// Make sure its enabled
		final boolean disabled = SystemProperties.getOptionalBoolean(CPCBackupProperty.SEND_WELCOME_DISABLED, false);
		if (disabled) {
			return null; // ignore, not sending welcome emails
		}

		final String recipient = this.user.getEmail();
		if (!LangUtils.hasValue(recipient)) {
			log.error("Cannot send welcome email to a user with no email address: {}", this.user.getUsername());
			return null;
		}

		final Map<String, Object> ctx = new HashMap<String, Object>();
		try {
			ctx.put(CpcEmailContext.Key.SYSTEM_HOST, SystemProperties.getRequired(SystemProperty.HOST)); // better be defined
			ctx.put("user", this.user);
			IResource resource = this.contentService.getServiceInstance().getResourceByName(EmailPaths.CONSUMER_WELCOME);
			Emailer.enqueueEmail(resource, recipient, ctx);
		} catch (Exception e) {
			log.warn("Unable to send welcome email to " + recipient + ". " + ctx, e);
			throw new DebugRuntimeException("Unable to send welcome email to " + recipient + ". " + e.getMessage(), e);
		}
		return null;
	}
}
