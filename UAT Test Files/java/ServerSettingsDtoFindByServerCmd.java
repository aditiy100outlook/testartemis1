package com.code42.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.backup42.CpcConstants;
import com.backup42.common.perm.C42PermissionPro;
import com.backup42.server.command.RepositoryDiskVacuumer;
import com.code42.app.AppPermission;
import com.code42.client.AutoUpgradeSetStatusCmd;
import com.code42.client.ClientUpgradeAvailabilitySetStatusCmd;
import com.code42.core.CommandException;
import com.code42.core.IEnvironment;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.email.EmailAddress;
import com.code42.org.OrgSettingsInfo;
import com.code42.org.OrgSettingsInfoFindByOrgCmd;
import com.code42.settings.BrandFindByIdCmd;
import com.code42.user.User;
import com.code42.user.UserFindByRoleQuery;
import com.code42.utils.LangUtils;
import com.code42.utils.SystemProperties;
import com.google.inject.Inject;

public class ServerSettingsDtoFindByServerCmd extends DBCmd<ServerSettingsDto> {

	public enum ServerAlias {
		MY_SERVER, MY_CLUSTER_SERVER
	}

	@Inject
	private IEnvironment environment;

	private int serverId = -1;
	private ServerAlias serverAlias = null;

	/**
	 * Find the settings for a particular serverId
	 */
	public ServerSettingsDtoFindByServerCmd(int serverId) {
		this.serverId = serverId;
	}

	/**
	 * Find the settings for a server from my environment (my server, or my cluster server). This is a convenience method
	 * to help REST resources find server settings in the absence of an environment context.
	 */
	public ServerSettingsDtoFindByServerCmd(ServerAlias serverAlias) {
		this.serverAlias = serverAlias;
	}

	@Override
	public ServerSettingsDto exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		// configure an id, if necessary
		if (this.serverAlias == ServerAlias.MY_SERVER) {
			this.serverId = this.environment.getMyNodeId();
		} else if (this.serverAlias == ServerAlias.MY_CLUSTER_SERVER) {
			this.serverId = this.environment.getMyClusterId();
		}
		if (this.serverId < 0) {
			throw new CommandException("Misconfigured command, invalid serverId. (serverId=" + this.serverId
					+ ", serverAlias=" + this.serverAlias + ")");
		}

		// Get server computer
		BaseServer server = this.db.find(new BaseServerFindByIdQuery(this.serverId));

		// Get server settings info
		final ServerSettingsInfo ssi = this.runtime.run(new ServerSettingsInfoFindByServerCmd(this.serverId), session);

		// Get Brand of the default org
		Brand brand;
		{
			// Get default org settings
			final OrgSettingsInfoFindByOrgCmd cmd = new OrgSettingsInfoFindByOrgCmd.Builder().orgId(
					CpcConstants.Orgs.ADMIN_ID).build();
			final OrgSettingsInfo osi = this.runtime.run(cmd, session);

			// Get the brand for the mail sender address
			brand = this.runtime.run(new BrandFindByIdCmd(osi.getBrandId()), session);
		}

		ServerNotifySettingsFindByServerIdCmd findCmd = new ServerNotifySettingsFindByServerIdCmd(server.getServerId());
		findCmd.createIfNotFound(); // we never want a null object
		final ServerNotifySettings sns = this.runtime.run(findCmd, session);

		Collection<String> sysadminEmailAddresses = new ArrayList<String>();
		if (this.serverId == this.env.getMyClusterId()) {

			// Find the sysadmin email addresses so we can display them too
			List<User> sysAdmins = this.db.find(new UserFindByRoleQuery(AppPermission.SYSADMIN));
			for (User u : sysAdmins) {
				if (LangUtils.hasValue(u.getEmail()) && EmailAddress.isValid(u.getEmail())) {
					sysadminEmailAddresses.add(u.getEmail());
				}
			}
		}

		final boolean deviceAutoUpgradeEnabled = SystemProperties.getOptionalBoolean(
				AutoUpgradeSetStatusCmd.DEVICE_AUTO_UPGRADE_ENABLED_PROPERTY, true);

		final boolean serverUpgraded = SystemProperties.getOptionalBoolean(
				ServerUpgradedSetStatusCmd.SERVER_UPGRADED_PROPERTY, false);

		final boolean clientUpgradeAvailable = SystemProperties.getOptionalBoolean(
				ClientUpgradeAvailabilitySetStatusCmd.CLIENT_UPGRADE_AVAILABLE_PROPERTY, false);

		final int expiredArchiveCleanupInDays = RepositoryDiskVacuumer.getExpireCleanupAgeInDays();

		return new ServerSettingsDto(server, ssi, sns, brand, sysadminEmailAddresses, deviceAutoUpgradeEnabled,
				serverUpgraded, clientUpgradeAvailable, expiredArchiveCleanupInDays);
	}
}
