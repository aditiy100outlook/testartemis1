/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.lead;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.SendFailedException;

import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.OrgDef;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.content.IContentService;
import com.code42.core.content.IResource;
import com.code42.core.impl.DBCmd;
import com.code42.email.Emailer;
import com.code42.exception.DuplicateExistsException;
import com.code42.hibernate.HibernateDataProviderException;
import com.code42.logging.Logger;
import com.code42.org.Org;
import com.code42.param.Param;
import com.code42.param.ParamFindByNameQuery;
import com.code42.param.ParamItem;
import com.code42.param.ParamItemFindByParamAndNameQuery;
import com.code42.user.User;
import com.code42.user.UserCreateCmd;
import com.code42.user.UserFindByEmailQuery;
import com.code42.user.UserParamItem;
import com.code42.user.data.hibernate.UserParamItemDataProvider;
import com.code42.utils.LangUtils;
import com.google.inject.Inject;

/**
 * A command to create a simple Marketing Lead, consisting of the following items:
 * <ul>
 * <li>email : the email address of the lead</li>
 * <li>referrer : the referring page or a reference ID for that page</li>
 * <li>source : an answer to the question "where did you hear about us"</li>
 * </ul>
 * 
 * Leads are stored as simple BackupUser objects in the CrashPlan org with an email address and no password. The other
 * values are associated with the user using UserParamItems.
 * 
 */
public class LeadCreateCmd extends DBCmd<Lead> {

	@Inject
	private IContentService cs;

	private static final Logger log = Logger.getLogger(LeadCreateCmd.class);

	protected static String P_MARKETING_LEAD = "MARKETING_LEAD";
	protected static String PI_REFERRER = "REFERRER";
	protected static String PI_SOURCE = "SOURCE";

	private Builder builder;

	private LeadCreateCmd(Builder builder) {
		this.builder = builder;
	}

	@Override
	public Lead exec(CoreSession session) throws CommandException {

		// Setup the relevant object references
		Org crashplan = OrgDef.getOrg(OrgDef.CRASHPLAN);
		Param pLeads = this.db.find(new ParamFindByNameQuery(P_MARKETING_LEAD));
		ParamItem piReferrer = this.db.find(new ParamItemFindByParamAndNameQuery(pLeads, PI_REFERRER));
		ParamItem piSource = this.db.find(new ParamItemFindByParamAndNameQuery(pLeads, PI_SOURCE));
		UserParamItemDataProvider dp = UserParamItem.getDataProvider();

		Lead lead = new Lead(this.builder);

		try {
			this.db.beginTransaction();

			User user = null;

			UserFindByEmailQuery query = new UserFindByEmailQuery(lead.getEmail());
			query.doNotCheckResultSize();

			List<User> users = this.db.find(query);
			if (LangUtils.hasElements(users)) {
				user = users.get(0);

			} else {

				// Create the BackupUser object
				UserCreateCmd.Builder uBuilder = new UserCreateCmd.Builder(crashplan.getOrgId(), lead.getEmail());
				uBuilder.emailPromo(true);
				uBuilder.email(lead.getEmail());

				UserCreateCmd cmd = uBuilder.build();
				user = this.run(cmd, this.auth.getAdminSession());
			}

			try {
				// Create the Referrer UserParamItem
				UserParamItem referrer = new UserParamItem();
				referrer.setUserId(user.getUserId());
				referrer.setParamItem(piReferrer);
				referrer.setUserParamValue(lead.getReferrer());
				dp.save(referrer);

				// Create the Source UserParamItem
				UserParamItem source = new UserParamItem();
				source.setUserId(user.getUserId());
				source.setParamItem(piSource);
				source.setUserParamValue(lead.getSource());
				dp.save(source);

			} catch (HibernateDataProviderException e) {
				Throwable cause = e.getCause();
				if (cause != null && cause instanceof DuplicateExistsException) {
					log.info("LEAD:: Lead already exists; update ignored: {}", lead);
				} else {
					throw e;
				}
			}

			this.db.commit();

			log.info("LEAD:: Lead Created: {}", lead);

			if (LangUtils.hasValue(this.builder.emailTemplate)) {

				IResource resource = this.cs.getServiceInstance().getResourceByName(this.builder.emailTemplate);
				Map<String, Object> context = new HashMap<String, Object>();
				context.put("lead", lead);
				Emailer.enqueueEmail(resource, lead.getEmail(), context);

				log.info("LEAD:: Email Sent: {} to: {}", this.builder.emailTemplate, lead.getEmail());
			}

			return lead;
		} catch (SendFailedException e) {
			throw new CommandException("Unable to send email for Lead: {}", this.builder, e);
		} catch (CommandException ce) {
			throw ce;
		} catch (Exception e) {
			throw new CommandException("Unable to create Lead: {}", this.builder, e);

		} finally {
			this.db.endTransaction();
		}
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("LeadCreateCmd [email=");
		builder.append(this.builder.email);
		builder.append(", referrer=");
		builder.append(this.builder.referrer);
		builder.append(", source=");
		builder.append(this.builder.source);
		builder.append("]");
		return builder.toString();
	}

	public static class Builder {

		public enum Error {
			MISSING_EMAIL, MISSING_REFERRER, MISSING_SOURCE
		}

		protected String email;
		protected String referrer;
		protected String source;
		private String emailTemplate;

		public Builder(String email, String referrer, String source) {
			this.email = email;
			this.referrer = referrer;
			this.source = source;
		}

		public Builder emailTemplate(String emailTemplate) {
			this.emailTemplate = emailTemplate;
			return this;
		}

		private void validate() throws BuilderException {

			if (!LangUtils.hasValue(this.email)) {
				throw new BuilderException(Error.MISSING_EMAIL, "Email Address is required");
			}

			if (!LangUtils.hasValue(this.referrer)) {
				throw new BuilderException(Error.MISSING_REFERRER, "Referrer is required");
			}

			if (!LangUtils.hasValue(this.source)) {
				throw new BuilderException(Error.MISSING_SOURCE, "Source is required");
			}

			return;
		}

		public LeadCreateCmd build() throws BuilderException {
			this.validate();
			return new LeadCreateCmd(this);
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("LeadCreateCmd.Builder [email=");
			builder.append(this.email);
			builder.append(", referrer=");
			builder.append(this.referrer);
			builder.append(", source=");
			builder.append(this.source);
			builder.append("]");
			return builder.toString();
		}
	}

}
