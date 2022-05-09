package com.code42.client;

import com.backup42.common.command.ServiceCommand;
import com.backup42.common.perm.C42PermissionPro;
import com.code42.backup.central.ICentralService;
import com.code42.computer.Computer;
import com.code42.computer.ComputerFindByIdCmd;
import com.code42.core.CommandException;
import com.code42.core.ServiceException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.NotFoundException;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.google.inject.Inject;

/**
 * Allow the computer identified by <code>id</code> to upgrade, regardless of whether auto-upgrade is enabled or not.
 */
public class ComputerUpgradeCmd extends AbstractCmd<Void> {

	private static final Logger log = LoggerFactory.getLogger(ComputerUpgradeCmd.class);

	@Inject
	private IClientUpgradeService client;

	@Inject
	private ICentralService central;

	private final long id;

	public ComputerUpgradeCmd(long id) {
		this.id = id;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		// Must be an admin
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		final Computer computer = this.run(new ComputerFindByIdCmd(this.id), session);
		if (null == computer) {
			log.warn("Unable to allow upgrade for a non-existent computer, id={}", this.id);
			throw new NotFoundException("No computer found for id=" + this.id);
		}

		try {
			this.client.allowComputerToUpgrade(computer.getGuid());
		} catch (ServiceException se) {
			throw new CommandException(se);
		}

		// Force the client to reconnect to authority
		this.central.getPeer().sendServiceCommand(computer.getGuid(), ServiceCommand.RECONNECT_AUTHORITY);

		return null;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (this.id ^ (this.id >>> 32));
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
		ComputerUpgradeCmd other = (ComputerUpgradeCmd) obj;
		if (this.id != other.id) {
			return false;
		}
		return true;
	}
}
