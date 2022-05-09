package com.code42.license;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;

import com.backup42.CpcConstants;
import com.backup42.EmailPaths;
import com.backup42.history.CpcHistoryLogger;
import com.backup42.server.CpcEmailContext;
import com.backup42.service.SettingsServices;
import com.code42.commerce.util.CommerceTime;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.content.IResource;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.code42.email.Emailer;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.property.PropertyDeleteCmd;
import com.code42.property.PropertySetCmd;
import com.code42.server.ServerSettingsInfo;
import com.code42.server.SystemAlertRecipientsFindCmd;
import com.code42.server.license.ISeatUsageService;
import com.code42.server.license.ProductLicense;
import com.code42.server.license.ProductLicenseUpdateCmd;
import com.code42.server.license.SeatUsage;
import com.code42.server.license.ServerLicense;
import com.code42.utils.ArrayUtils;
import com.code42.utils.LangUtils;
import com.code42.utils.SystemProperties;
import com.code42.utils.SystemProperty;
import com.code42.utils.Time;
import com.google.inject.Inject;

public class LicenseAlerterCmd extends DBCmd<Boolean> {

	@Inject
	private ISeatUsageService seatUsageService;

	private final static Logger log = LoggerFactory.getLogger(LicenseAlerterCmd.class.getName());

	private final static String LICENSE_ALERTER_JOB_ENABLED = "c42.license.alerter.job.enabled";
	private final static Boolean LICENSE_ALERTER_JOB_ENABLED_DEFAULT = Boolean.TRUE;

	private final static String PROPERTY_DEMO_ALERT_SENT_WARNING = "b42.demo-alert-sent.warning";
	private final static String PROPERTY_DEMO_ALERT_SENT_EXPIRED = "b42.demo-alert-sent.expired";
	private final static int DEMO_END_WARNING_ALERT_DAYS = 7;

	private final static String PROPERTY_PERPETUAL_NOTIFICATION1_SENT = "b42.perpetual-notification-sent.one";
	private final static String PROPERTY_PERPETUAL_NOTIFICATION2_SENT = "b42.perpetual-notification-sent.two";
	private final static String PROPERTY_PERPETUAL_NOTIFICATION3_SENT = "b42.perpetual-notification-sent.three";

	final static int FIRST_WARNING_DAYS = SystemProperties.getOptionalInt(
			SystemProperty.SEAT_USAGE_EVALUATION_PERIOD_IN_DAYS, SystemProperty.SEAT_USAGE_EVALUATION_PERIOD_DEFAULT);
	final static int SECOND_WARNING_DAYS = 30;
	final static int THIRD_WARNING_DAYS = 15;

	@Override
	public Boolean exec(CoreSession session) throws CommandException {

		if (this.env.isCpCentral()) {
			log.info(this.getClass().getSimpleName() + " is invalid for CPC.");
			return false;
		}

		if (!this.env.isPrimaryMaster()) {
			log.info(this.getClass().getSimpleName() + " only runs on the primary master node");
			return false;
		}

		final boolean jobEnabled = SystemProperties.getOptionalBoolean(LICENSE_ALERTER_JOB_ENABLED,
				LICENSE_ALERTER_JOB_ENABLED_DEFAULT);
		if (!jobEnabled) {
			log.info(this.getClass().getSimpleName() + " is disabled");
			return false;
		}

		// Disabled while in demo mode.
		boolean emailSent = false;
		final ServerLicense licenseStatus = ServerLicense.getInstance();
		if (licenseStatus.isDemo()) {
			emailSent = this.demoAlertCheck(licenseStatus);
		} else {
			emailSent = this.seatCountAlertCheck(licenseStatus, session);
		}

		return emailSent;
	}

	private boolean seatCountAlertCheck(ServerLicense licenseStatus, CoreSession session) throws CommandException {
		this.clearDemoAlertFlags();

		ServerLicense sl = ServerLicense.getInstance();
		int maxUsersAllowed = sl.getMaxUsers();
		SeatUsage seatUsage = this.seatUsageService.getSeatUsage(maxUsersAllowed);

		final long nowInMillis = Time.getNowInMillis();
		final Date now = new Date(nowInMillis);
		final EmailStatus status = new EmailStatus();

		// Check for free trials
		final Date nextFreeTrialExpiration = seatUsage.getNextFreeTrialExpiration();

		if (nextFreeTrialExpiration != null
				&& new DateTime(nowInMillis).plusDays(15).isAfter(nextFreeTrialExpiration.getTime())) {

			final int daysUntilExpiration = CommerceTime.getLengthInDays(now, nextFreeTrialExpiration);
			status.freeTrialAlert(daysUntilExpiration, seatUsage.getFreeTrialCount());
		}

		// Check SaaS licenses
		List<ProductLicense> activeSaasLicenses = sl.getActiveSaasLicenses();
		for (ProductLicense pl : activeSaasLicenses) {

			// Check first notification period
			if (!pl.isNotificationOneSent()
					&& (seatUsage.getSeatsInUse() > maxUsersAllowed - pl.getQuantity())
					&& new DateTime(pl.getExpirationDate().getTime()).isBefore(new DateTime(nowInMillis)
							.plusDays(FIRST_WARNING_DAYS))) {
				status.firstWarningSaasAlert(pl.getQuantity());

				pl.setNotificationOneSent(true);
				pl = this.runtime.run(new ProductLicenseUpdateCmd(pl), session);
				continue;
			}

			// Check second notification period
			if (!pl.isNotificationTwoSent()
					&& (seatUsage.getSeatsInUse() > maxUsersAllowed - pl.getQuantity())
					&& new DateTime(pl.getExpirationDate().getTime()).isBefore(new DateTime(nowInMillis)
							.plusDays(SECOND_WARNING_DAYS))) {
				status.secondWarningSaasAlert(pl.getQuantity());
				pl.setNotificationTwoSent(true);
				pl = this.runtime.run(new ProductLicenseUpdateCmd(pl), session);
				continue;
			}

			// Check third notification period
			if (!pl.isNotificationThreeSent()
					&& (seatUsage.getSeatsInUse() > maxUsersAllowed - pl.getQuantity())
					&& new DateTime(pl.getExpirationDate().getTime()).isBefore(new DateTime(nowInMillis)
							.plusDays(THIRD_WARNING_DAYS))) {
				status.thirdWarningSaasAlert(pl.getQuantity());
				pl.setNotificationThreeSent(true);
				pl = this.runtime.run(new ProductLicenseUpdateCmd(pl), session);
				continue;
			}
		}

		// Check perpetual licenses
		if (sl.isPerpetualPresent()) {
			if (new DateTime(nowInMillis).plusDays(sl.getPerpetualSupportDaysRemaining()).isAfter(
					new DateTime(nowInMillis).plusDays(FIRST_WARNING_DAYS))) {
				SystemProperties.setProperty(PROPERTY_PERPETUAL_NOTIFICATION1_SENT, "false");
				SystemProperties.setProperty(PROPERTY_PERPETUAL_NOTIFICATION2_SENT, "false");
				SystemProperties.setProperty(PROPERTY_PERPETUAL_NOTIFICATION3_SENT, "false");
			}

			// check to send first notification
			final boolean notificationOneSent = SystemProperties.getOptionalBoolean(PROPERTY_PERPETUAL_NOTIFICATION1_SENT,
					false);
			if (!notificationOneSent
					&& new DateTime(nowInMillis).plusDays(sl.getPerpetualSupportDaysRemaining()).isBefore(
							new DateTime(nowInMillis).plusDays(FIRST_WARNING_DAYS))) {
				status.perpetualAlert(sl.getPerpetualSupportDaysRemaining());
				SystemProperties.setProperty(PROPERTY_PERPETUAL_NOTIFICATION1_SENT, "true");
			}

			if (!status.isPerpetualExpiring()) {
				// Was a first notification to send, so check to send second notification
				final boolean notificationTwoSent = SystemProperties.getOptionalBoolean(PROPERTY_PERPETUAL_NOTIFICATION2_SENT,
						false);
				if (!notificationTwoSent
						&& new DateTime(nowInMillis).plusDays(sl.getPerpetualSupportDaysRemaining()).isBefore(
								new DateTime(nowInMillis).plusDays(SECOND_WARNING_DAYS))) {
					status.perpetualAlert(sl.getPerpetualSupportDaysRemaining());
					SystemProperties.setProperty(PROPERTY_PERPETUAL_NOTIFICATION2_SENT, "true");
				}
			}

			if (!status.isPerpetualExpiring()) {
				// Was neither a first nor second notification to send, so check to send third notification
				final boolean notificationThreeSent = SystemProperties.getOptionalBoolean(
						PROPERTY_PERPETUAL_NOTIFICATION3_SENT, false);
				if (!notificationThreeSent
						&& new DateTime(nowInMillis).plusDays(sl.getPerpetualSupportDaysRemaining()).isBefore(
								new DateTime(nowInMillis).plusDays(THIRD_WARNING_DAYS))) {
					status.perpetualAlert(sl.getPerpetualSupportDaysRemaining());
					SystemProperties.setProperty(PROPERTY_PERPETUAL_NOTIFICATION3_SENT, "true");
				}
			}
		}

		if (status.shouldSendEmail()) {
			this.sendSeatAlertEmail(sl, seatUsage, status);
			return true;
		}

		return false;
	}

	private void sendSeatAlertEmail(ServerLicense sl, SeatUsage su, EmailStatus status) {

		Collection<String> alertRecipientList = CoreBridge.runNoException(new SystemAlertRecipientsFindCmd());
		String[] alertRecipients = alertRecipientList.toArray(new String[alertRecipientList.size()]);

		String recipientString = ArrayUtils.toString(alertRecipients);
		if ((alertRecipients == null) || (alertRecipients.length == 0)) {
			return; // We can't really send an email to nobody, can we?
		}

		Map<String, Object> context = new CpcEmailContext(CpcConstants.Orgs.ADMIN_ID/* This is a system alert */);

		context.put("shortestEvaluationPeriod", status.getShortestEvaluationPeriod());
		final boolean isSaasExpiring = status.isSaasExpiring();
		context.put("isSaasExpiring", isSaasExpiring);
		if (isSaasExpiring) {
			context.put("saasExpiration1", status.getFirstWarningSaasExpiration());
			context.put("saasExpiration2", status.getSecondWarningSaasExpiration());
			context.put("saasExpiration3", status.getThirdWarningSaasExpiration());
		}
		final boolean isPerpetualExpiring = status.isPerpetualExpiring();
		context.put("isPerpetualExpiring", isPerpetualExpiring);
		if (isPerpetualExpiring) {
			context.put("perpetualExpirationDays", status.perpetualExpirationDays);
		}
		final boolean isFreeTrial = status.isFreeTrial();
		context.put("isFreeTrial", isFreeTrial);
		if (isFreeTrial) {
			context.put("numberInFreeTrial", status.freeTrialCount);
		}

		// Log the issue and send the e-mail
		String template = EmailPaths.EMAIL_LICENSE_ALERT_ADMIN;
		try {
			IResource resource = CoreBridge.getContentService().getServiceInstance().getResourceByName(template);
			Emailer.enqueueEmail(resource, alertRecipients, context, false);
		} catch (Exception e) {
			log.error("Unable to send license alert email to: " + recipientString + " template: " + template + " context: "
					+ context, e);
		}

	}

	private boolean demoAlertCheck(ServerLicense licenseStatus) {
		log.info("PROe Server is in DEMO mode");
		boolean warningAlertSent = SystemProperties.getOptionalBoolean(PROPERTY_DEMO_ALERT_SENT_WARNING, false);
		boolean expiredAlertSent = SystemProperties.getOptionalBoolean(PROPERTY_DEMO_ALERT_SENT_EXPIRED, false);
		int daysRemaining = licenseStatus.getDemoDaysRemaining();
		boolean emailSent = false;

		if (daysRemaining > DEMO_END_WARNING_ALERT_DAYS) {
			this.clearDemoAlertFlags();
		} else if (licenseStatus.getDemoEndDate().getTime() < System.currentTimeMillis()) {
			if (!expiredAlertSent) {
				this.sendDemoAlertEmail(licenseStatus.getDemoEndDate(), true);
				emailSent = true;
				CoreBridge.runNoException(new PropertySetCmd(PROPERTY_DEMO_ALERT_SENT_EXPIRED, "true", true));
				CpcHistoryLogger.info(null, "Demo has expired.");
				CoreBridge.getSystemAlertService().triggerDemoExpiredAlert();
			}
		} else if (daysRemaining <= DEMO_END_WARNING_ALERT_DAYS) {
			if (!warningAlertSent) {
				this.sendDemoAlertEmail(licenseStatus.getDemoEndDate(), false);
				emailSent = true;
				CpcHistoryLogger.info(null, "Demo warning.  Demo will expire in " + daysRemaining + " days.");
				CoreBridge.runNoException(new PropertySetCmd(PROPERTY_DEMO_ALERT_SENT_WARNING, "true", true));
			}
		}

		return emailSent;
	}

	private void clearDemoAlertFlags() {
		boolean warningAlertSent = SystemProperties.getOptionalBoolean(PROPERTY_DEMO_ALERT_SENT_WARNING, false);
		boolean expiredAlertSent = SystemProperties.getOptionalBoolean(PROPERTY_DEMO_ALERT_SENT_EXPIRED, false);
		if (warningAlertSent) {
			CoreBridge.runNoException(new PropertyDeleteCmd(PROPERTY_DEMO_ALERT_SENT_WARNING));
		}
		if (expiredAlertSent) {
			CoreBridge.runNoException(new PropertyDeleteCmd(PROPERTY_DEMO_ALERT_SENT_EXPIRED));
		}
	}

	private void sendDemoAlertEmail(Date expireDate, boolean expired) {
		ServerSettingsInfo ssi = SettingsServices.getInstance().getMyServerSettingsInfo();
		boolean textOnlyEmail = ssi.getTextOnlySystemAlerts();

		Collection<String> recipients = CoreBridge.runNoException(new SystemAlertRecipientsFindCmd());
		String recipientString = LangUtils.toString(recipients);
		log.warn("ALERT - Sent demo alert to: " + recipientString);

		if (recipients.isEmpty()) {
			log.warn("Could not email demo expiration alert.  No server alert recipients found.");
			return; // We can't really send an email to nobody, can we?
		}

		// NOTE: If you add context items, also add them to CpcEmailContext.previewValues()
		// NOTE: Do not remove context items because clients have customized
		// their emails and we try to let them continue with those versions.
		Map<String, Object> context = new CpcEmailContext(CpcConstants.Orgs.ADMIN_ID/* This is a system alert */);
		context.put("expireDate", expireDate);
		context.put("expired", expired);

		// Log the issue and send the e-mail
		String template = EmailPaths.EMAIL_DEMO_ALERT_ADMIN;
		try {
			IResource resource = CoreBridge.getContentService().getServiceInstance().getResourceByName(template);
			Emailer.enqueueEmail(resource, recipients.toArray(new String[recipients.size()]), context, textOnlyEmail);
		} catch (Exception e) {
			log.error("Unable to send demo alert email to: " + recipientString + " template: " + template + " context: "
					+ context, e);
		}
	}

	static class EmailStatus {

		private static final int DAYS_DEFAULT = Integer.MAX_VALUE;

		private boolean sendEmail = false;

		// This is public to make it available to Velocity
		public class SaasExpiration {

			private int expirationCount = 0;
			private final int rangeBegin;
			private final int rangeEnd;

			public SaasExpiration(int rangeBegin, int rangeEnd) {
				this.rangeBegin = rangeBegin;
				this.rangeEnd = rangeEnd;
			}

			public int getExpirationCount() {
				return this.expirationCount;
			}

			public void addToExpirationCount(int expirationCount) {
				this.expirationCount += expirationCount;
			}

			public int getRangeBegin() {
				return this.rangeBegin;
			}

			public int getRangeEnd() {
				return this.rangeEnd;
			}

			public boolean hasAnExpiringLicense() {
				return this.expirationCount > 0;
			}
		}

		private boolean saasExpiring = false;
		private final Map<Integer, SaasExpiration> saasExpirations;

		private int perpetualExpirationDays = DAYS_DEFAULT;

		private int freeTrialCount = -1;
		private int freeTrialExpirationDays = DAYS_DEFAULT;

		EmailStatus() {
			this.saasExpirations = new HashMap<Integer, SaasExpiration>();

			this.saasExpirations.put(FIRST_WARNING_DAYS, new SaasExpiration(SECOND_WARNING_DAYS + 1, FIRST_WARNING_DAYS));
			this.saasExpirations.put(SECOND_WARNING_DAYS, new SaasExpiration(THIRD_WARNING_DAYS + 1, SECOND_WARNING_DAYS));
			this.saasExpirations.put(THIRD_WARNING_DAYS, new SaasExpiration(1, THIRD_WARNING_DAYS));
		}

		private int getMinSaasExpirationDays() {
			Integer value = Integer.MAX_VALUE;

			for (SaasExpiration saasExpiration : this.saasExpirations.values()) {
				if (saasExpiration.hasAnExpiringLicense()) {
					value = Math.min(value, saasExpiration.getRangeEnd());
				}
			}

			return value;
		}

		private int getShortestEvaluationPeriod() {
			if (this.sendEmail) {
				// One of these three has to be set
				int value = Math.min(Math.min( //
						this.getMinSaasExpirationDays(), //
						this.perpetualExpirationDays), //
						this.freeTrialExpirationDays);

				if (value != DAYS_DEFAULT) {
					return value;
				}
			}

			throw new UnsupportedOperationException("Only need to calculate this value if you are sending an email");
		}

		private boolean shouldSendEmail() {
			return this.sendEmail;
		}

		private void firstWarningSaasAlert(Integer saasExpirationCount) {
			this.saasAlert(FIRST_WARNING_DAYS, saasExpirationCount);
		}

		private void secondWarningSaasAlert(Integer saasExpirationCount) {
			this.saasAlert(SECOND_WARNING_DAYS, saasExpirationCount);
		}

		private void thirdWarningSaasAlert(Integer saasExpirationCount) {
			this.saasAlert(THIRD_WARNING_DAYS, saasExpirationCount);
		}

		private SaasExpiration getFirstWarningSaasExpiration() {
			return this.saasExpirations.get(FIRST_WARNING_DAYS);
		}

		private SaasExpiration getSecondWarningSaasExpiration() {
			return this.saasExpirations.get(SECOND_WARNING_DAYS);
		}

		private SaasExpiration getThirdWarningSaasExpiration() {
			return this.saasExpirations.get(THIRD_WARNING_DAYS);
		}

		private void saasAlert(int saasExpirationDays, int saasExpirationCount) {
			Integer key = Integer.valueOf(saasExpirationDays);
			SaasExpiration saasExpiration = this.saasExpirations.get(key);
			saasExpiration.addToExpirationCount(saasExpirationCount);

			this.sendEmail = true;
			this.saasExpiring = true;
		}

		private boolean isSaasExpiring() {
			return this.saasExpiring;
		}

		private void perpetualAlert(int perpetualExpirationDays) {
			this.perpetualExpirationDays = perpetualExpirationDays;
			this.sendEmail = true;
		}

		private boolean isPerpetualExpiring() {
			return this.perpetualExpirationDays != DAYS_DEFAULT;
		}

		private void freeTrialAlert(int freeTrialExpirationDays, int freeTrialCount) {
			this.freeTrialExpirationDays = freeTrialExpirationDays;
			this.freeTrialCount = freeTrialCount;
			this.sendEmail = true;
		}

		private boolean isFreeTrial() {
			return this.freeTrialExpirationDays != DAYS_DEFAULT;
		}

	}
}