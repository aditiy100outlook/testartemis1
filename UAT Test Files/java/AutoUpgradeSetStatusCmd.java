package com.code42.client;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.ServiceException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.exec.RestartCoreExecuteCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.property.PropertySetCmd;
import com.code42.utils.SystemProperties;
import com.google.inject.Inject;

/**
 * Set the auto-upgrade state for clients connecting to this server.
 */
public class AutoUpgradeSetStatusCmd extends AbstractCmd<Boolean> {

	private static final Logger log = LoggerFactory.getLogger(AutoUpgradeSetStatusCmd.class);

	public static final String DEVICE_AUTO_UPGRADE_ENABLED_PROPERTY = "c42.device.auto.upgrade.enabled";

	@Inject
	private IClientUpgradeService client;

	private final boolean autoUpgradeEnabled;
	private final boolean resetUpgradeList;
	private final boolean restartIfEnabling;

	/**
	 * @param autoUpgradeEnabled After executing this command, that client auto-upgrade state of the server should be
	 *          equal to this passed in value
	 * @param resetUpgradeList Whether or not the "allowed to upgrade" list should be cleared
	 */
	public AutoUpgradeSetStatusCmd(boolean autoUpgradeEnabled, boolean resetUpgradeList) {
		this(autoUpgradeEnabled, resetUpgradeList, false);
	}

	public AutoUpgradeSetStatusCmd(boolean autoUpgradeEnabled, boolean resetUpgradeList, boolean restartIfEnabling) {
		this.autoUpgradeEnabled = autoUpgradeEnabled;
		this.resetUpgradeList = resetUpgradeList;
		this.restartIfEnabling = restartIfEnabling;
	}

	@Override
	public Boolean exec(CoreSession session) throws CommandException {
		// Must be an admin
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		final boolean enablingAutoUpgrade = this.autoUpgradeEnabled && //
				!SystemProperties.getOptionalBoolean(DEVICE_AUTO_UPGRADE_ENABLED_PROPERTY, true);

		final PropertySetCmd cmd = new PropertySetCmd(DEVICE_AUTO_UPGRADE_ENABLED_PROPERTY, Boolean.valueOf(
				this.autoUpgradeEnabled).toString(), true);
		this.runtime.run(cmd, session);
		log.info("Set client auto-upgrade property to {}", this.autoUpgradeEnabled);

		// Reset existing configuration that would allow clients to upgrade during an auto-upgrade disabled period
		if (this.resetUpgradeList) {
			try {
				this.client.reset();
				log.info("Reset upgrade allowed list");
			} catch (ServiceException se) {
				throw new CommandException(se);
			}
		}

		if (enablingAutoUpgrade && this.restartIfEnabling) {
			if (!this.env.isCpCentral()) {
				this.runtime.run(new RestartCoreExecuteCmd("Client auto-upgrade enabled"), session);
			}
		}

		return this.autoUpgradeEnabled;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (this.autoUpgradeEnabled ? 1231 : 1237);
		result = prime * result + (this.resetUpgradeList ? 1231 : 1237);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (this.getClass() != obj.getClass()) {
			return false;
		}
		AutoUpgradeSetStatusCmd other = (AutoUpgradeSetStatusCmd) obj;
		if (this.autoUpgradeEnabled != other.autoUpgradeEnabled) {
			return false;
		}
		if (this.resetUpgradeList != other.resetUpgradeList) {
			return false;
		}
		return true;
	}

}
