/*
 * Created on Apr 19, 2011 by Tony Lindquist <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.settings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import com.backup42.alerts.BackupReporter;
import com.backup42.executor.ExecutorServices;
import com.backup42.scheduler.SchedulerJob.ServerType;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.utils.LangUtils;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;
import com.code42.validation.rules.EmailRule;

/**
 * 
 * Send the BackupReport to the list of configured recipients (assuming they have permission to manage the org).
 */
public class BackupReportSendNowCmd extends DBCmd<Void> {

	private final Builder builder;

	private BackupReportSendNowCmd(Builder builder) {
		this.builder = builder;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		// There is no need to check for existence on these builder values; the defaults
		// are all set to true values, so will either exist or be overridden.
		int orgId = this.builder.orgId;

		this.runtime.run(new IsOrgManageableCmd(orgId, C42PermissionApp.Org.READ), session);

		Boolean includeChildOrgs = this.builder.includeChildOrgs.get();
		Date periodStartDate = this.builder.periodStartDate;
		Date periodEndDate = this.builder.periodEndDate;
		Boolean includeOrgManagers = this.builder.includeOrgManagers.get();
		Boolean includeMyself = this.builder.includeMyself.get();

		Collection<String> additionalRecipients = this.builder.additionalRecipients.get();

		if (includeMyself) {
			String myEmail = session.getUser().getEmail();
			if (EmailRule.isValidEmail(myEmail)) {
				if (additionalRecipients == null) {
					additionalRecipients = new ArrayList<String>();
				}
				additionalRecipients.add(myEmail);
			}
		}

		// null means send to orgManagers and empty list means send to no one
		Collection<String> recipients = includeOrgManagers ? null : Collections.EMPTY_LIST;

		BackupReporter reporter = new BackupReporter(orgId, includeChildOrgs, periodStartDate, periodEndDate, recipients,
				additionalRecipients);
		reporter.setServerType(ServerType.ALL); // This must be able to run on any server in our cluster.
		ExecutorServices.getInstance().submit(reporter, new HashMap());

		return null;
	}

	// //////////////////////////
	// BUILDER CLASS
	// //////////////////////////

	/**
	 * Builds the input data and the WebRestoreGetChildren command.
	 * 
	 * Required value (guid)
	 * 
	 * <ol>
	 * Default values if not set:
	 * <li>includeChildOrgs : true</li>
	 * <li>periodStartDate : date report was last sent or, if null the default is one week ago</li>
	 * <li>periodEndDate : now</li>
	 * <li>recipients : configured recipients</li>
	 * <li>additionalRecipients : null</li>
	 * <ol>
	 */
	public static class Builder {

		/* These values must always be present; it's the only way to get a builder */
		private final int orgId;

		// Required options (none)

		// Defaulted options; can be overridden
		// Note that there is NO need for Option here when Option.NONE is the same as null
		// TODO: Replace these Option/Some values with plain old booleans, dates, and collections!!!
		private Option<Boolean> includeChildOrgs = new Some<Boolean>(true);
		private Date periodStartDate = null; // None is equivalent to null here = normal report default date
		private Date periodEndDate = null; // None is equivalent to null here = normal report default date
		private Option<Boolean> includeOrgManagers = new Some<Boolean>(false);
		private Option<Boolean> includeMyself = new Some<Boolean>(true);
		private Option<Collection<String>> additionalRecipients = new Some<Collection<String>>(new ArrayList());

		public Builder(int orgId) {
			this.orgId = orgId;
		}

		public Builder includeChildOrgs(Boolean includeChildOrgs) {
			this.includeChildOrgs = new Some<Boolean>(includeChildOrgs);
			return this;
		}

		public Builder periodStartDate(Date periodStartDate) throws BuilderException {
			this.periodStartDate = periodStartDate;
			return this;
		}

		public Builder periodEndDate(Date periodEndDate) throws BuilderException {
			this.periodEndDate = periodEndDate;
			return this;
		}

		public Builder includeOrgManagers(Boolean includeOrgManagers) {
			this.includeOrgManagers = new Some<Boolean>(includeOrgManagers);
			return this;
		}

		public Builder includeMyself(Boolean includeMyself) {
			this.includeMyself = new Some<Boolean>(includeMyself);
			return this;
		}

		/**
		 * Assumed to be a comma-delimited string
		 */
		public Builder additionalRecipients(String sAdditionalRecipients) {
			List<String> additionalRecipients = LangUtils.toList(sAdditionalRecipients);
			this.additionalRecipients = new Some<Collection<String>>(additionalRecipients);
			return this;
		}

		public void validate() throws BuilderException {
			String message = null;
			message = (this.orgId <= 1) ? "Invalid orgId provided" : message;
			if (message != null) {
				throw new BuilderException(message);
			}
			if (!(this.additionalRecipients instanceof None) && this.additionalRecipients != null) {
				for (String email : this.additionalRecipients.get()) {
					if (!EmailRule.isValidEmail(email)) {
						throw new BuilderException("Invalid email address: " + email);
					}
				}
			}

			if (this.periodStartDate != null && this.periodEndDate != null) {
				if (this.periodStartDate.after(this.periodEndDate)) {
					throw new BuilderException("Start date must be before end date; startDate={}, endDate={}",
							this.periodStartDate, this.periodEndDate);
				}
			}
		}

		public BackupReportSendNowCmd build() throws BuilderException {

			this.validate();
			return new BackupReportSendNowCmd(this);
		}

		@Override
		public String toString() {
			StringBuilder buffer = new StringBuilder();
			buffer.append("BackupReportSendNow.Builder[\n");
			buffer.append("orgId=").append(this.orgId).append("\n");
			buffer.append(", includeChildOrgs=").append(this.includeChildOrgs).append("\n");
			buffer.append(", periodStartDate=").append(this.periodStartDate).append("\n");
			buffer.append(", periodEndDate=").append(this.periodEndDate).append("\n");
			buffer.append(", includeOrgManagers=").append(this.includeOrgManagers).append("\n");
			buffer.append(", includeMyself=").append(this.includeMyself).append("\n");
			buffer.append(", additionalRecipients=").append(this.additionalRecipients).append("\n");
			buffer.append("]");
			return buffer.toString();
		}

	}

}
