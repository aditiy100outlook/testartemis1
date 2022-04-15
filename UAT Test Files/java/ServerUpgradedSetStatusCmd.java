package com.code42.server;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.property.PropertySetCmd;

/**
 * Set the server upgraded status for the server/cluster. The <code>upgraded</code> property passed in on the
 * constructor determines whether the server is considered to be upgraded or not
 */
public class ServerUpgradedSetStatusCmd extends AbstractCmd<Boolean> {

	private static final Logger log = LoggerFactory.getLogger(ServerUpgradedSetStatusCmd.class);

	public static final String SERVER_UPGRADED_PROPERTY = "c42.server.upgraded";

	private final boolean upgraded;

	public ServerUpgradedSetStatusCmd(boolean upgraded) {
		this.upgraded = upgraded;
	}

	@Override
	public Boolean exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		final PropertySetCmd cmd = new PropertySetCmd(SERVER_UPGRADED_PROPERTY, Boolean.valueOf(this.upgraded).toString(),
				true);
		this.runtime.run(cmd, session);
		log.info("Server upgraded property set to {}", this.upgraded);

		return this.upgraded;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (this.upgraded ? 1231 : 1237);
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
		ServerUpgradedSetStatusCmd other = (ServerUpgradedSetStatusCmd) obj;
		if (this.upgraded != other.upgraded) {
			return false;
		}
		return true;
	}

}
