package com.code42.server;

import java.util.Collection;

import com.backup42.CpcConstants;
import com.code42.core.impl.CoreBridge;
import com.code42.email.EmailAddress;
import com.code42.email.Emailer;
import com.code42.org.OrgNotifySettings;
import com.code42.org.OrgNotifySettingsFindByOrgCmd;
import com.code42.server.cluster.MyCluster;
import com.code42.server.node.ServerNode;
import com.code42.utils.LangUtils;
import com.code42.utils.SystemProperties;
import com.code42.utils.SystemProperty;

public class ServerSettingsDto {

	private final BaseServer server;
	private final ServerSettingsInfo ssi;
	private final Brand brand;
	private final ServerNotifySettings sns;
	private final OrgNotifySettings ons;
	private final Collection<String> sysadminEmailAddresses;
	private final boolean deviceAutoUpgrade;
	private final boolean serverUpgraded;
	private final boolean clientUpgradeAvailable;
	private final int expiredArchiveCleanupInDays;

	/**
	 * 
	 * @param server - This is a physical server or a cluster server.
	 * @param ssi
	 * @param sns
	 * @param brand
	 * @param clusterServer - This is the ClusterServer for a physical server
	 */
	public ServerSettingsDto(BaseServer server, ServerSettingsInfo ssi, ServerNotifySettings sns, Brand brand,
			Collection<String> sysadminEmailAddresses, boolean deviceAutoUpgradeEnabled, boolean serverUpgraded,
			boolean clientUpgradeAvailable, int expiredArchiveCleanupInDays) {
		super();
		this.server = server;
		this.ssi = ssi;
		this.sns = sns;
		this.brand = brand;
		this.sysadminEmailAddresses = sysadminEmailAddresses;
		this.deviceAutoUpgrade = deviceAutoUpgradeEnabled;
		this.serverUpgraded = serverUpgraded;
		this.clientUpgradeAvailable = clientUpgradeAvailable;
		this.expiredArchiveCleanupInDays = expiredArchiveCleanupInDays;
		// This is the default (system) org notify settings that each org inherits from.
		this.ons = CoreBridge.runNoException(new OrgNotifySettingsFindByOrgCmd.Builder(CpcConstants.Orgs.ADMIN_ID).build());
	}

	/**
	 * @return the type of the server object, as a string. E.g., "ServerNode".
	 */
	public String getServerDiscriminator() {
		return this.server.getClass().getSimpleName();
	}

	public String getName() {
		return this.server.getComputer().getName();
	}

	public Integer getServerId() {
		return this.server.getServerId();
	}

	public Integer getArchiveMaintenanceIntervalInDays() {
		return this.ssi.getMaintenanceIntervalDays();
	}

	public boolean isMailServerInternal() {
		return !LangUtils.hasValue(this.ssi.getMailHost()) || this.ssi.getMailHost().equalsIgnoreCase(Emailer.JMTA_HOST);
	}

	public boolean isMailServerInternalInheritedValue() {
		return !LangUtils.hasValue(this.ssi.getMailHostInheritedValue())
				|| this.ssi.getMailHostInheritedValue().equalsIgnoreCase(Emailer.JMTA_HOST);
	}

	public boolean isMailServerInternalInherited() {
		return this.ssi.isMailHostInherited();
	}

	public String getMailHost() {
		if (!this.isMailServerInternal()) {
			return this.ssi.getMailHost();
		} else {
			return null;
		}
	}

	public String getMailHostInheritedValue() {
		if (!this.isMailServerInternalInheritedValue()) {
			return this.ssi.getMailHostInheritedValue();
		} else {
			return null;
		}
	}

	public boolean isMailHostInherited() {
		return this.ssi.isMailHostInherited();
	}

	public boolean isTextOnlySystemAlerts() {
		return this.ssi.getTextOnlySystemAlerts();
	}

	public boolean isAutoUpgrade() {
		return this.ssi.getAutoUpgrade();
	}

	public boolean isMailSsl() {
		return this.ssi.isMailSsl();
	}

	public boolean isMailSslInheritedValue() {
		return this.ssi.isMailSslInheritedValue();
	}

	public boolean isMailSslInherited() {
		return this.ssi.isMailSslInherited();
	}

	public String getMailUsername() {
		return this.ssi.getMailUsername();
	}

	public String getMailUsernameInheritedValue() {
		return this.ssi.getMailUsernameInheritedValue();
	}

	public boolean isMailUsernameInherited() {
		return this.ssi.isMailUsernameInherited();
	}

	public String getMailPassword() {
		return this.ssi.getMailPassword();
	}

	public String getMailPasswordInheritedValue() {
		return this.ssi.getMailPasswordInheritedValue();
	}

	public boolean isMailPasswordInherited() {
		return this.ssi.isMailPasswordInherited();
	}

	public String getMailSenderAddress() {
		return this.brand.getSenderEmail();
	}

	public boolean getUseHttps() {
		return this.ssi.getUseHttps();
	}

	public Long getKeystoreId() {
		return this.ssi.getKeystoreId();
	}

	public Integer getDirSyncIntervalHours() {
		return this.ssi.getDirSyncIntervalHours();
	}

	public Integer getNonLdapOrgId() {
		return this.ssi.getNonLdapOrgId();
	}

	public String getNetworkWebsite() {
		return this.server.getWebsiteHost();
	}

	public String getDailyAccountingTime() {
		String time = this.sns.getNightlyReportingTime();
		if (!LangUtils.hasValue(time)) {
			/* If nothing defined in the DB fall back on the system-wide default (if there is one) */
			time = SystemProperties.getOptional(SystemProperty.DAILY_DEFAULT_RUNTIME);
		}
		return time;
	}

	public String getNetworkPrimaryAddress() {
		return this.server.getPrimaryAddress();
	}

	public String getNetworkSecondaryAddress() {
		return this.server.getSecondaryAddress();
	}

	/**
	 * Private address means both super_peer_address and space_address, which have been consolidated.
	 */
	public String getPrivateAddress() {
		if (this.server instanceof ServerNode) {
			// Private address is only a property of server nodes.
			ServerNode sn = (ServerNode) this.server;
			// the two fields may differ; super peer address takes priority
			return sn.getSuperPeerAddress();
		}
		return "";
	}

	/**
	 * Private address means both super_peer_address and space_address, which have been consolidated.
	 */
	public void setPrivateAddress(String privateAddress) {
		if (this.server instanceof ServerNode) {
			// Private address is only a property of server nodes.
			ServerNode sn = (ServerNode) this.server;
			sn.setSuperPeerAddress(privateAddress);
			sn.setSpaceAddress(privateAddress);
		}
	}

	/**
	 * How few GB of free space until a store point warning alert?
	 */
	public int getAlertFreeSpaceWarning() {
		return this.sns.getWarningGigabytes();
	}

	/**
	 * How few GB of free space until a store point critical alert?
	 */
	public int getAlertFreeSpaceCritical() {
		return this.sns.getCriticalGigabytes();
	}

	/**
	 * How few licenses until warning alert?
	 */
	public int getAlertLicenseWarning() {
		return this.sns.getFreeLicenseWarningThreshold();
	}

	/**
	 * How few licenses until critical alert?
	 */
	public int getAlertLicenseCritical() {
		return this.sns.getFreeLicenseCriticalThreshold();
	}

	/**
	 * How many days of no backup activity until warning alert?
	 */
	public int getAlertBackupWarning() {
		return this.ons.getBackupWarningDays();
	}

	/**
	 * How many days of no backup activity until critical?
	 */
	public int getAlertBackupCritical() {
		return this.ons.getBackupCriticalDays();
	}

	public Collection<String> getSysadminEmailAddresses() {
		return this.sysadminEmailAddresses;
	}

	/**
	 * These are additional addresses (beyond the system admins) who get alert emails.
	 */
	public Collection<String> getAdditionalServerAlertEmailRecipients() {
		return EmailAddress.splitAddresses(this.sns.getAdditionalServerAlertEmailAddresses());
	}

	public BaseServer getServer() {
		return this.server;
	}

	public ServerSettingsInfo getServerSettingsInfo() {
		return this.ssi;
	}

	public Brand getBrand() {
		return this.brand;
	}

	public ServerNotifySettings getServerNotifySettings() {
		return this.sns;
	}

	public OrgNotifySettings getOrgNotifySettings() {
		return this.ons;
	}

	public boolean isDeviceAutoUpgrade() {
		return this.deviceAutoUpgrade;
	}

	public boolean isServerUpgraded() {
		return this.serverUpgraded;
	}

	public boolean isClientUpgradeAvailable() {
		return this.clientUpgradeAvailable;
	}

	public int getExpiredArchiveCleanupInDays() {
		return this.expiredArchiveCleanupInDays;
	}

	public boolean isMyCluster() {
		return this.server instanceof MyCluster;
	}
}
