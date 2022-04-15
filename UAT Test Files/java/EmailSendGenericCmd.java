package com.code42.email;

import java.util.HashMap;
import java.util.Map;

import com.backup42.EmailPaths;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.content.IContentService;
import com.code42.core.content.IResource;
import com.code42.core.impl.AbstractCmd;
import com.code42.validation.rules.EmailRule;
import com.google.inject.Inject;

/**
 * This is to be used only for quickie forms that collect data to be sent to an internal recipient.
 * 
 * Our app should *never* allow a user to set the recipient for this kind of email.
 * 
 * See MailQueueResource.groovy for an example of how to use this
 */
public class EmailSendGenericCmd extends AbstractCmd<Void> {

	@Inject
	private IContentService content;

	private String recipient;
	private String sender;
	private String subject;
	private Map data;

	public EmailSendGenericCmd(String recipient, String sender, String subject, Map data) {
		super();
		this.recipient = recipient;
		this.sender = sender;
		this.subject = subject;
		this.data = data;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		if (EmailRule.isValidEmail(this.recipient)) {

			Map context = new HashMap();

			// Override the subject and sender passed in
			context.put("subject", this.subject);
			context.put("sender", this.sender);
			context.put("data", this.data);

			IResource resource = null;
			resource = this.content.getServiceInstance().getResourceByName(EmailPaths.GENERIC);

			try {
				// Send the email
				Emailer.enqueueEmail(resource, this.recipient, context);
			} catch (Exception re) {
				throw new CommandException("Unable to render generic email", re);
			}

		} else {
			throw new CommandException("Missing or invalid email recipient: {}", this.recipient);
		}

		return null;
	}

}
