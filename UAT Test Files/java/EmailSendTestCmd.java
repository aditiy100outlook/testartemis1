package com.code42.email;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.email.Email;
import com.code42.email.Emailer;
import com.code42.messaging.Location;
import com.code42.org.OrgSettingsInfo;
import com.code42.org.OrgSettingsInfoFindByOrgCmd;
import com.code42.server.Brand;
import com.code42.settings.BrandFindByIdCmd;
import com.code42.utils.LangUtils;

/**
 * Immediately send a test email.
 */
public class EmailSendTestCmd extends DBCmd<Void> {

	private final String recipient;
	private String sender;
	private String host;
	private Boolean ssl;
	private String username;
	private String password;
	private Integer orgId;
	private String subject = "PROe Server test email";
	private String htmlBody;
	private String textBody = "This is a test email.  If you received this your email connection is working.";

	/**
	 * Send a sample email using system configured sender, host, port, ssl, etc.
	 * 
	 * WARNING: Only for sending a test email. Do not use this to send system emails.
	 */
	public EmailSendTestCmd(String recipient, int orgId) {
		super();
		this.recipient = recipient;
		this.orgId = orgId;
	}

	/**
	 * Send a sample email to the given recipient from the given sender. Uses internal email host [JMTA].
	 */
	public EmailSendTestCmd(String recipient, String sender) {
		super();
		this.recipient = recipient;
		this.sender = sender;
	}

	public EmailSendTestCmd(String recipient, String sender, String host, Boolean ssl, String username, String password) {
		super();
		this.recipient = recipient;
		this.sender = sender;
		this.host = host;
		this.ssl = ssl;
		this.username = username;
		this.password = password;
	}

	public void setContent(String subject, String htmlBody, String textBody) {
		this.subject = subject;
		this.htmlBody = htmlBody;
		this.textBody = textBody;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		try {
			// Build a sample email.
			final Email email = new Email();
			email.addRecipient(this.recipient);
			if (LangUtils.hasValue(this.subject)) {
				email.setSubject(this.subject);
			}
			if (LangUtils.hasValue(this.htmlBody)) {
				email.setTextOnly(false);
				email.setHtmlSource(this.htmlBody);
			} else {
				email.setTextOnly(true);
			}
			if (LangUtils.hasValue(this.textBody)) {
				email.setTextSource(this.textBody);
			}

			// Get sender from Brand if not specified.
			if (this.sender == null && this.orgId != null) {
				// Get default org settings so we can get the brand
				final OrgSettingsInfoFindByOrgCmd cmd = new OrgSettingsInfoFindByOrgCmd.Builder().orgId(this.orgId).build();
				final OrgSettingsInfo osi = this.runtime.run(cmd, session);

				// Get the brand for the mail sender address
				final Brand brand = this.runtime.run(new BrandFindByIdCmd(osi.getBrandId()), session);
				this.sender = brand.getSenderEmail();
			}
			email.setFromAddress(this.sender);

			if (LangUtils.hasValue(this.host)) {
				final Location loc = new Location(this.host);
				Emailer.sendDirect(loc.getAddress(), loc.getPort(), this.ssl, email, this.username, this.password);
			} else {
				Emailer.sendDirect(email);
			}
		} catch (Exception e) {
			throw new CommandException("Unable to send test email.", e);
		}
		return null;
	}
}
