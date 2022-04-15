package com.code42.server;

import java.net.URL;
import java.text.ParseException;
import java.util.Collection;

import com.backup42.common.perm.C42PermissionPro;
import com.backup42.server.command.RepositoryDiskVacuumer;
import com.code42.client.AutoUpgradeSetStatusCmd;
import com.code42.computer.Computer;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.IConfiguration;
import com.code42.core.alert.ISystemAlertService;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.email.EmailAddress;
import com.code42.email.Emailer;
import com.code42.exception.InvalidParamException;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.OrgNotifySettings;
import com.code42.property.PropertySetCmd;
import com.code42.server.node.Node;
import com.code42.settings.BrandUpdateCmd;
import com.code42.settings.NotificationUtils;
import com.code42.utils.LangUtils;
import com.code42.utils.Time;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;
import com.code42.utils.option.SomeNotNull;
import com.code42.validation.rules.InetAddressRule;
import com.google.common.base.Joiner;
import com.google.inject.Inject;

/**
 * The serverId passed in can represent one of several things:
 * 
 * <ol>
 * <li>a clusterId representing the MyCluster row</li>
 * <li>a null value which is to be seen as representing the MyCluster row</li>
 * <li>a non-null value representing a ServerNode or a StorageNode</li>
 * <li></li>
 * </ol>
 */
public class ServerSettingsDtoUpdateCmd extends DBCmd<ServerSettingsDto> {

	private static final Logger log = LoggerFactory.getLogger(ServerSettingsDtoUpdateCmd.class);

	@Inject
	ISystemAlertService alertSvc;

	public enum Error {
		PRIMARY_REQUIRED_ON_STORAGE
	}

	private final Builder builder;

	private static Joiner portJoiner = Joiner.on(":");

	public ServerSettingsDtoUpdateCmd(Builder builder) {
		this.builder = builder;
	}

	@Override
	public ServerSettingsDto exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);
		this.ensureNotCPCentral();

		final boolean isMaster = this.env.isMaster();

		int serverId = (this.builder.serverId == null) ? this.env.getMyClusterId() : this.builder.serverId;
		ServerSettingsDto dto = this.runtime.run(new ServerSettingsDtoFindByServerCmd(serverId), session);

		// Server Computer
		boolean nameOrHostChanged = false;
		String newServerName = null;
		if (!(this.builder.name instanceof None)) {
			final Computer c = dto.getServer().getComputer();
			newServerName = this.builder.name.get();
			if (!c.getName().equals(newServerName)) {
				nameOrHostChanged = true;
				c.setName(newServerName);
			}
		}

		// Server
		{
			final BaseServer server = dto.getServer();
			boolean networkAddressChanged = false;
			if (!(this.builder.networkPrimaryAddress instanceof None)) {

				String primaryAddress = this.builder.networkPrimaryAddress.get();

				// if this server is a storage node then a primary address is required
				if (!LangUtils.hasValue(primaryAddress) && !isMaster) {
					throw new CommandException(Error.PRIMARY_REQUIRED_ON_STORAGE, "Unable to update server "
							+ this.builder.serverId + ". Primary address required on storage server.");
				}

				if (LangUtils.compare(primaryAddress, server.getPrimaryAddress(), true) != 0) {
					server.setPrimaryAddress(primaryAddress);
					networkAddressChanged = true;
				}

				if (LangUtils.hasValue(this.builder.networkSecondaryAddress)) {
					String secondaryAddress = this.builder.networkSecondaryAddress.get();
					if (LangUtils.compare(secondaryAddress, server.getSecondaryAddress(), true) != 0) {
						networkAddressChanged = true;
						server.setSecondaryAddress(secondaryAddress);
					}
				}
			}

			String newWebsiteHost = null;
			if (LangUtils.hasValue(this.builder.networkWebsiteHost)) {
				newWebsiteHost = this.builder.networkWebsiteHost.get();
				if (!server.getWebsiteHost().equals(newWebsiteHost)) {
					nameOrHostChanged = true;
				}
				server.setWebsiteHost(newWebsiteHost);
			}

			if (server instanceof Node && LangUtils.hasValue(this.builder.privateAddress)) {
				// append port numbers before storing in db
				String privateAddress = this.builder.privateAddress.get();
				int superPeerPort = this.config.get(IConfiguration.Keys.Messaging.superPeerPort, Integer.class);
				String superPeerAddr = portJoiner.join(privateAddress, superPeerPort);
				int spacePort = this.config.get(IConfiguration.Keys.Space.port, Integer.class);
				String spaceAddr = portJoiner.join(privateAddress, spacePort);

				Node node = (Node) server;
				node.setSuperPeerAddress(superPeerAddr);
				node.setSpaceAddress(spaceAddr);
				if (!LangUtils.equals(node.getSuperPeerAddress(), superPeerAddr)
						|| !LangUtils.equals(node.getSpaceAddress(), spaceAddr)) {
					nameOrHostChanged = true;
				}
			}

			ServerUpdateCmd cmd;
			if (networkAddressChanged) {
				cmd = ServerUpdateCmd.withFullBroadcast(server);
			} else if (nameOrHostChanged) {
				cmd = ServerUpdateCmd.withNameAndHostBroadcast(server);
			} else {
				cmd = ServerUpdateCmd.withNoBroadcast(server);
			}
			this.run(cmd, session);
		}

		this.updateSettings(dto, isMaster, session);

		if (!(this.builder.deviceAutoUpgrade instanceof None)) {
			final boolean autoUpgradeEnabled = this.builder.deviceAutoUpgrade.get();
			// If turning auto-upgrade on, then also reset the upgrade list and restart the server
			this.runtime
					.run(new AutoUpgradeSetStatusCmd(autoUpgradeEnabled, autoUpgradeEnabled, autoUpgradeEnabled), session);
		}

		if (!(this.builder.serverUpgraded instanceof None)) {
			final Boolean serverUpgraded = this.builder.serverUpgraded.get();
			this.runtime.run(new ServerUpgradedSetStatusCmd(serverUpgraded), session);
		}

		if (!(this.builder.expiredArchiveCleanupInDays instanceof None)) {
			PropertySetCmd cmd = new PropertySetCmd(RepositoryDiskVacuumer.Property.CLEANUP_EXP_AGE_IN_DAYS,
					this.builder.expiredArchiveCleanupInDays.get().toString(), true);
			this.run(cmd, session);
		}

		// Get updated DTO now that everything has changed.
		dto = this.runtime.run(new ServerSettingsDtoFindByServerCmd(this.builder.serverId), session);
		return dto;
	}

	private void updateSettings(ServerSettingsDto dto, boolean isMaster, CoreSession session) throws CommandException {
		// Server Settings Info
		{
			boolean changed = false;
			ServerSettingsInfo ssi = dto.getServerSettingsInfo();

			if (!(this.builder.archiveMaintenanceIntervalInDays instanceof None)) {
				ssi.setMaintenanceIntervalDays(this.builder.archiveMaintenanceIntervalInDays.get());
				changed = true;
			}

			if (!(this.builder.mailHost instanceof None)) {
				String host = this.builder.mailHost.get();
				ssi.setMailHost(host);
				changed = true;
			}
			if (!(this.builder.mailServerInternal instanceof None)) {
				if (this.builder.mailServerInternal.get()) {
					ssi.setMailHost(Emailer.JMTA_HOST);
					changed = true;
				}
			}

			if (!(this.builder.mailFlags instanceof None)) {
				ssi.setMailFlags(this.builder.mailFlags.get());
				changed = true;
			}

			if (!(this.builder.mailSsl instanceof None)) {
				ssi.setMailSsl(this.builder.mailSsl.get());
				changed = true;
			}

			if (!(this.builder.mailUsername instanceof None)) {
				ssi.setMailUsername(this.builder.mailUsername.get());
				changed = true;
			}

			if (!(this.builder.mailPassword instanceof None)) {
				ssi.setMailPassword(this.builder.mailPassword.get());
				changed = true;
			}

			if (!(this.builder.useHttps instanceof None)) {
				ssi.setUseHttps(this.builder.useHttps.get());
				changed = true;
			}

			if (!(this.builder.keystoreId instanceof None)) {
				ssi.setKeystoreId(this.builder.keystoreId.get());
				changed = true;
			}

			if (!(this.builder.nonLdapOrgId instanceof None)) {
				ssi.setNonLdapOrgId(this.builder.nonLdapOrgId.get());
				changed = true;
			}

			if (!(this.builder.dirSyncIntervalHours instanceof None)) {
				ssi.setDirSyncIntervalHours(this.builder.dirSyncIntervalHours.get());
				changed = true;
			}

			if (!(this.builder.autoUpgrade instanceof None)) {
				ssi.setAutoUpgrade(this.builder.autoUpgrade.get());
				changed = true;
			}

			if (changed) {
				this.run(new ServerSettingsUpdateCmd(ssi.getServerSettings()), session);
			}
		}

		// Brand
		if (!(this.builder.mailSender instanceof None)) {
			final Brand b = dto.getBrand();
			b.setSenderEmail(this.builder.mailSender.get());
			this.run(new BrandUpdateCmd(b), session);
		}

		// Server Notify Settings
		{
			boolean changed = false;
			final ServerNotifySettings sns = dto.getServerNotifySettings();

			if (!(this.builder.alertLicenseWarning instanceof None) && isMaster) {
				sns.setFreeLicenseWarningThreshold(this.builder.alertLicenseWarning.get());
				changed = true;
			}

			if (!(this.builder.alertLicenseCritical instanceof None) && isMaster) {
				sns.setFreeLicenseCriticalThreshold(this.builder.alertLicenseCritical.get());
				changed = true;
			}

			if (!(this.builder.alertFreeSpaceWarning instanceof None) && isMaster) {
				sns.setWarningGigabytes(this.builder.alertFreeSpaceWarning.get());
				changed = true;
			}

			if (!(this.builder.alertFreeSpaceCritical instanceof None) && isMaster) {
				sns.setCriticalGigabytes(this.builder.alertFreeSpaceCritical.get());
				changed = true;
			}

			if (!(this.builder.dailyAccountingTime instanceof None)) {
				sns.setNightlyReportingTime(this.builder.dailyAccountingTime.get());
				changed = true;
			}

			if (!(this.builder.additionalSystemAlertEmailAddresses instanceof None)) {
				Collection<String> emails = this.builder.additionalSystemAlertEmailAddresses.get();
				String emailListString = LangUtils.toString(emails);
				sns.setAdditionalServerAlertEmailAddresses(emailListString);
				if (LangUtils.hasValue(emailListString)) {
					this.alertSvc.clearSystemAlertRecipientsMissingAlert();
				}
				changed = true;
			}

			if (changed) {
				NotificationUtils.updateServerNotifySettings(sns);
				// Trigger or clear "system alert recipients missing" alert
				this.run(new SystemAlertRecipientsFindCmd(), this.auth.getAdminSession());
			}
		}

		/*
		 * System OrgNotifySettings - This is here so we can do the update in one big transaction. Not sure this is that
		 * critical, however.
		 */
		{
			boolean changed = false;
			final OrgNotifySettings ons = dto.getOrgNotifySettings();

			if (!(this.builder.backupWarningDays instanceof None) && isMaster) {
				ons.setBackupWarningDays(this.builder.backupWarningDays.get());
				changed = true;
			}

			if (!(this.builder.backupCriticalDays instanceof None) && isMaster) {
				ons.setBackupCriticalDays(this.builder.backupCriticalDays.get());
				changed = true;
			}

			if (changed) {
				NotificationUtils.updateOrgNotifySettings(ons);
			}
		}
	}

	/**
	 * Builder class
	 */
	public static class Builder {

		public Builder(Integer serverId) {
			super();
			this.serverId = serverId;
		}

		public void validate() throws BuilderException {
			if ((this.serverId != null) && (this.serverId < 1)) {
				throw new BuilderException("Illegal serverId: " + this.serverId);
			}

			// Server
			{
				if (!(this.name instanceof None) && !LangUtils.hasValue(this.name.get())) {
					throw new BuilderException("Name required.");
				}

				if (!LangUtils.hasValue(this.networkPrimaryAddress) && LangUtils.hasValue(this.networkSecondaryAddress)) {
					throw new BuilderException("Unable to set secondary address without primary.");
				}
				if (!(this.networkPrimaryAddress instanceof None)) {
					final boolean portRequired = true;// must ALWAYS include a port
					final boolean allIpsAllowed = false;// must be a valid connectable address
					final boolean localhostAllowed = false;// must be a valid remote address
					String address = this.networkPrimaryAddress.get();
					if (LangUtils.hasValue(address)) {
						boolean valid = InetAddressRule.isValidAddress(address, portRequired, allIpsAllowed, localhostAllowed);
						if (!valid) {
							throw new BuilderException("Invalid network address: " + address
									+ ". Loopback and 0.0.0.0 are not allowed.");
						}
						if (!(this.networkSecondaryAddress instanceof None)) {
							address = this.networkSecondaryAddress.get();
							// The secondary address can be null.
							if (LangUtils.hasValue(address)) {
								valid = InetAddressRule.isValidAddress(address, portRequired, allIpsAllowed, localhostAllowed);
								if (!valid) {
									throw new BuilderException("Invalid network address: " + address
											+ ". Loopback and 0.0.0.0 are not allowed.");
								}
							}
						}
					}
				}
				if (!(this.privateAddress instanceof None)) {
					final boolean portRequired = false;
					final boolean allIpsAllowed = false;
					final boolean localhostAllowed = false;
					String address = this.privateAddress.get();
					if (LangUtils.hasValue(address)) {
						boolean valid = InetAddressRule.isValidAddress(address, portRequired, allIpsAllowed, localhostAllowed);
						if (!valid) {
							throw new BuilderException("Invalid value for privateAddress: " + address);
						}
					}
				}
			}

			// Server Settings Info
			{
				if (!(this.archiveMaintenanceIntervalInDays instanceof None) && this.archiveMaintenanceIntervalInDays.get() < 1) {
					throw new BuilderException("Invalid archiveMaintenanceIntervalInDays: "
							+ this.archiveMaintenanceIntervalInDays.get());
				}
				if (!(this.expiredArchiveCleanupInDays instanceof None) && this.expiredArchiveCleanupInDays.get() < 0) {
					throw new BuilderException("Invalid expiredArchiveCleanupInDays: " + this.expiredArchiveCleanupInDays.get());
				}
				// Can have a null host if we're using the internal mail server. Otherwise, validate.
				boolean mailHostIsRequired = this.mailIsInheriting == false
						&& ((this.mailServerInternal instanceof None) || this.mailServerInternal.get() == false);
				if (mailHostIsRequired && !(this.mailHost instanceof None) && !LangUtils.hasValue(this.mailHost.get())) {
					throw new BuilderException("Missing mail host.");
				}
			}

			// Brand
			if (!(this.mailSender instanceof None)) {
				final String email = this.mailSender.get();
				if (!EmailAddress.isValid(email)) {
					throw new BuilderException("Invalid sender email: " + email);
				}
			}

			// Cluster server
			if (!(this.networkWebsiteHost instanceof None)) {
				try {
					new URL(this.networkWebsiteHost.get());
				} catch (Exception e) {
					throw new BuilderException("Invalid website host: " + this.networkWebsiteHost.get());
				}
			}

			// Server Notify Settings
			{
				if (!(this.alertLicenseWarning instanceof None)) {
					Integer v = this.alertLicenseWarning.get();
					if (v == null || v < 0) {
						throw new BuilderException("Invalid alert license warning: " + v);
					}
				}

				if (!(this.alertLicenseCritical instanceof None)) {
					Integer v = this.alertLicenseCritical.get();
					if (v == null || v < 0) {
						throw new BuilderException("Invalid alert license critical: " + v);
					}
					if (!(this.alertLicenseWarning instanceof None) && v > this.alertLicenseWarning.get()) {
						throw new BuilderException("Invalid alert license critical: " + v + ". Must be less than warning.");
					}
				}

				if (!(this.backupWarningDays instanceof None)) {
					Integer v = this.backupWarningDays.get();
					if (v == null || v < 1) {
						throw new BuilderException("Invalid alert backup warning: " + v);
					}
				}

				if (!(this.backupCriticalDays instanceof None)) {
					Integer v = this.backupCriticalDays.get();
					if (v == null || v < 1) {
						throw new BuilderException("Invalid alert backup critical: " + v);
					}
					if (!(this.backupWarningDays instanceof None) && v <= this.backupWarningDays.get()) {
						throw new BuilderException("Invalid alert backup: critical (" + v + ") must be larger than warning ("
								+ this.backupWarningDays.get() + ")");
					}
				}

				if (!(this.alertFreeSpaceWarning instanceof None)) {
					Integer v = this.alertFreeSpaceWarning.get();
					if (v == null || v < 1) {
						throw new BuilderException("Invalid alert free space warning: " + v);
					}
				}

				if (!(this.alertFreeSpaceCritical instanceof None)) {
					Integer v = this.alertFreeSpaceCritical.get();
					if (v == null || v < 1) {
						throw new BuilderException("Invalid alert free space critical: " + v);
					}
					if (!(this.alertFreeSpaceWarning instanceof None) && v >= this.alertFreeSpaceWarning.get()) {
						throw new BuilderException("Invalid alert free space: critical (" + v + ") must be smaller than warning ("
								+ this.alertFreeSpaceWarning.get() + ")");
					}
				}

				if (!(this.dailyAccountingTime instanceof None)) {
					String v = this.dailyAccountingTime.get();
					try {
						Time.parseDateFromString("HH:mm", v);
					} catch (ParseException e) {
						throw new BuilderException("Invalid daily accounting time: " + v);
					}
				}
			}
		}

		public ServerSettingsDtoUpdateCmd build() throws CommandException {
			this.validate();
			return new ServerSettingsDtoUpdateCmd(this);
		}

		public Builder name(String name) {
			this.name = new Some<String>(name);
			return this;
		}

		public Builder archiveMaintenanceIntervalInDays(Integer archiveMaintenanceIntervalInDays) {
			this.archiveMaintenanceIntervalInDays = new Some<Integer>(archiveMaintenanceIntervalInDays);
			return this;
		}

		public Builder expiredArchiveCleanupInDays(Integer expiredArchiveCleanupInDays) {
			this.expiredArchiveCleanupInDays = new Some<Integer>(expiredArchiveCleanupInDays);
			return this;
		}

		public Builder mailServerInternal(Boolean mailServerInternal) {
			this.mailServerInternal = new Some<Boolean>(mailServerInternal);
			return this;
		}

		public Builder mailHost(String mailHost) {
			this.mailHost = new Some<String>(mailHost);
			return this;
		}

		public Builder mailFlags(Integer mailFlags) {
			this.mailFlags = new Some<Integer>(mailFlags);
			return this;
		}

		public Builder mailSsl(Boolean mailSsl) {
			this.mailSsl = new Some<Boolean>(mailSsl);
			return this;
		}

		public Builder mailUsername(String mailUsername) {
			this.mailUsername = new Some<String>(mailUsername);
			return this;
		}

		public Builder mailPassword(String mailPassword) {
			this.mailPassword = new Some<String>(mailPassword);
			return this;
		}

		public Builder mailSender(String mailSender) {
			this.mailSender = new Some<String>(mailSender);
			return this;
		}

		public Builder setMailIsInheriting(boolean mailIsInheriting) {
			this.mailIsInheriting = mailIsInheriting;
			return this;
		}

		public Builder useHttps(Boolean useHttps) {
			this.useHttps = new Some<Boolean>(useHttps);
			return this;
		}

		public Builder keystoreId(Long keystoreId) {
			this.keystoreId = new Some<Long>(keystoreId);
			return this;
		}

		public Builder networkWebsiteHost(String websiteHost) {
			this.networkWebsiteHost = new Some<String>(websiteHost);
			return this;
		}

		public Builder dailyAccountingTime(String dailyAccountingTime) {
			this.dailyAccountingTime = new Some<String>(dailyAccountingTime);
			return this;
		}

		public Builder networkPrimaryAddress(String networkPrimaryAddress) {
			if (networkPrimaryAddress != null && !LangUtils.hasValue(networkPrimaryAddress)) {
				networkPrimaryAddress = null;
			}
			this.networkPrimaryAddress = new Some<String>(networkPrimaryAddress);
			return this;
		}

		public Builder networkSecondaryAddress(String networkSecondaryAddress) {
			if (this.networkSecondaryAddress != null && !LangUtils.hasValue(this.networkSecondaryAddress)) {
				this.networkSecondaryAddress = null;
			}
			this.networkSecondaryAddress = new Some<String>(networkSecondaryAddress);
			return this;
		}

		public Builder privateAddress(String privateAddress) {
			this.privateAddress = new Some<String>(privateAddress);
			return this;
		}

		public Builder alertFreeSpaceWarning(Integer alertFreeSpaceWarning) {
			try {
				this.alertFreeSpaceWarning = new SomeNotNull<Integer>(alertFreeSpaceWarning);
			} catch (InvalidParamException e) {
				log.warn("Got an illegal null value for alertFreeSpaceWarning.");
			}
			return this;
		}

		public Builder alertFreeSpaceCritical(Integer alertFreeSpaceCritical) {
			try {
				this.alertFreeSpaceCritical = new SomeNotNull<Integer>(alertFreeSpaceCritical);
			} catch (InvalidParamException e) {
				log.warn("Got an illegal null value for alertFreeSpaceCritical.");
			}
			return this;
		}

		public Builder alertLicenseWarning(Integer alertLicenseWarning) {
			try {
				this.alertLicenseWarning = new SomeNotNull<Integer>(alertLicenseWarning);
			} catch (InvalidParamException e) {
				log.warn("Got an illegal null value for alertLicenseWarning.");
			}
			return this;
		}

		public Builder alertLicenseCritical(Integer alertLicenseCritical) {
			try {
				this.alertLicenseCritical = new SomeNotNull<Integer>(alertLicenseCritical);
			} catch (InvalidParamException e) {
				log.warn("Got an illegal null value for alertLicenseCritical.");
			}
			return this;
		}

		public Builder backupWarningDays(Integer alertBackupWarning) {
			this.backupWarningDays = new Some<Integer>(alertBackupWarning);
			return this;
		}

		public Builder backupCriticalDays(Integer alertBackupCritical) {
			this.backupCriticalDays = new Some<Integer>(alertBackupCritical);
			return this;
		}

		public Builder additionalSystemAlertEmailAddresses(Collection<String> additionalEmails) {
			this.additionalSystemAlertEmailAddresses = new Some<Collection<String>>(additionalEmails);
			return this;
		}

		/**
		 * For use with serverId 1 only
		 */
		public Builder dirSyncIntervalHours(Integer value) {
			this.dirSyncIntervalHours = new Some<Integer>(value);
			return this;
		}

		/**
		 * For use with serverId 1 only
		 */
		public Builder nonLdapOrgId(Integer value) {
			this.nonLdapOrgId = new Some<Integer>(value);
			return this;
		}

		public Builder autoUpgrade(Boolean upgrade) {
			this.autoUpgrade = new Some<Boolean>(upgrade);
			return this;
		}

		public Builder deviceAutoUpgrade(Boolean upgrade) {
			this.deviceAutoUpgrade = new Some<Boolean>(upgrade);
			return this;
		}

		public Builder serverUpgraded(Boolean upgraded) {
			this.serverUpgraded = new Some<Boolean>(upgraded);
			return this;
		}

		private Option<Integer> backupCriticalDays = None.getInstance();
		private Option<Integer> backupWarningDays = None.getInstance();
		private Option<Integer> alertLicenseCritical = None.getInstance();
		private Option<Integer> alertLicenseWarning = None.getInstance();
		private Option<Integer> alertFreeSpaceCritical = None.getInstance();
		private Option<Integer> alertFreeSpaceWarning = None.getInstance();
		private Option<String> networkSecondaryAddress = None.getInstance();
		private Option<String> privateAddress = None.getInstance();
		private Option<String> networkPrimaryAddress = None.getInstance();
		private Option<String> dailyAccountingTime = None.getInstance();
		private Option<String> networkWebsiteHost = None.getInstance();
		private Option<String> mailSender = None.getInstance();
		private Option<String> mailPassword = None.getInstance();
		private Option<String> mailUsername = None.getInstance();
		private Option<Integer> mailFlags = None.getInstance();
		private Option<Boolean> mailSsl = None.getInstance();
		private Option<String> mailHost = None.getInstance();
		private Option<Boolean> mailServerInternal = None.getInstance();
		private boolean mailIsInheriting = false; // needed for validation logic
		private Option<Boolean> useHttps = None.getInstance();
		private Option<Long> keystoreId = None.getInstance();
		private Option<Integer> archiveMaintenanceIntervalInDays = None.getInstance();
		private Option<Integer> expiredArchiveCleanupInDays = None.getInstance();
		private Option<String> name = None.getInstance();
		private Option<Collection<String>> additionalSystemAlertEmailAddresses = None.getInstance();
		private Option<Boolean> autoUpgrade = None.getInstance();
		private Option<Boolean> deviceAutoUpgrade = None.getInstance();
		private Option<Boolean> serverUpgraded = None.getInstance();
		private final Integer serverId;

		// These are really system properties that will probably never be overridden by individual servers
		private Option<Integer> dirSyncIntervalHours = None.getInstance();
		private Option<Integer> nonLdapOrgId = None.getInstance();
	}
}
