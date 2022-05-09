package com.code42.client;

import java.util.regex.Pattern;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.computer.Computer;
import com.code42.computer.ComputerFindByGuidCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.google.inject.Inject;

/**
 * Is the computer identified by the provided guid allowed to upgrade?
 */
public class ComputerUpgradeAllowCmd extends AbstractCmd<Boolean> {

	private static final Logger log = LoggerFactory.getLogger(ComputerUpgradeAllowCmd.class);

	@Inject
	private IClientUpgradeService client;

	private static final Pattern TIGER_NAME_PATTERN = Pattern.compile("Mac OS X.*");
	private static final Pattern TIGER_VERSION_PATTERN = Pattern.compile("^10\\.4(\\..*|)");

	private final long guid;

	public ComputerUpgradeAllowCmd(long guid) {
		this.guid = guid;
	}

	/**
	 * Verify that
	 * <ul>
	 * <li>The computer already exists. If it does not, then they cannot upgrade</li>
	 * <li>The computer is not a Mac OS X Tiger machine. If it <i>is</i>, then they cannot upgrade</li>
	 * <li>If automatic client upgrade is disabled, the computers is in the allowed list. If it is not, they cannot
	 * upgrade</li>
	 * </ul>
	 */
	@Override
	public Boolean exec(CoreSession session) throws CommandException {
		// Must be an admin
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		final Computer computer = this.runtime.run(new ComputerFindByGuidCmd(this.guid), session);
		if (computer == null) {
			log.debug("Computer for guid={} does not exist yet.  Cannot upgrade", this.guid);
			return false;
		}
		if (isMacTiger(computer.getOsName(), computer.getOsVersion())) {
			log.warn("Cannot upgrade Mac Tiger client: guid={}, osVersion={}", this.guid, computer.getOsVersion());
			return false;
		}

		return this.client.computerCanUpgrade(this.guid);
	}

	static boolean isMacTiger(String osName, String osVersion) {
		if (osName == null || osVersion == null) {
			return false;
		}

		return TIGER_NAME_PATTERN.matcher(osName).matches() && //
				TIGER_VERSION_PATTERN.matcher(osVersion).matches();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (this.guid ^ (this.guid >>> 32));
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
		ComputerUpgradeAllowCmd other = (ComputerUpgradeAllowCmd) obj;
		if (this.guid != other.guid) {
			return false;
		}
		return true;
	}
}
