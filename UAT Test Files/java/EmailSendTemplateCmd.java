package com.code42.email;

import java.util.Map;

import com.backup42.common.perm.C42PermissionPro;
import com.backup42.server.CpcEmailContext;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.content.IContentService;
import com.code42.core.content.IResource;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.validation.rules.EmailRule;
import com.google.inject.Inject;

public class EmailSendTemplateCmd extends AbstractCmd<Void> {

	@Inject
	private IContentService contentService;

	protected final static Logger log = LoggerFactory.getLogger(EmailSendTemplateCmd.class);

	private final String templateName;
	private final String recipient;
	private final int orgId;
	private final Map context;

	public EmailSendTemplateCmd(String templateName, String recipient, int orgId, Map context) {
		this.templateName = templateName;
		this.recipient = recipient;
		this.orgId = orgId;
		this.context = context;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SEND_EMAIL);

		if (!EmailRule.isValidEmail(this.recipient)) {
			throw new CommandException("Invalid email", this.recipient);
		}

		final IResource resource = this.contentService.getServiceInstance().getResourceByName(this.templateName);

		final CpcEmailContext ctx = new CpcEmailContext(this.orgId);
		ctx.putAll(this.context);

		try {
			Emailer.enqueueEmail(resource, this.recipient, ctx);
			log.info("EMAIL:: send email with email resource.", this.templateName, this.recipient);
		} catch (Exception e) {
			throw new CommandException("Failed to send templated email.", e, this.recipient);
		}

		return null;
	}

}
