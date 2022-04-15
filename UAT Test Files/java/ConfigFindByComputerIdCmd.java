package com.code42.config;

import com.code42.computer.Config;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.DBCmd;

/**
 * Find a computer's config by computer id.
 */
public class ConfigFindByComputerIdCmd extends DBCmd<Config> {

	private long computerId;

	public ConfigFindByComputerIdCmd(long cid) {
		this.computerId = cid;
	}

	@Override
	public Config exec(CoreSession session) throws CommandException {

		this.runtime.run(new IsComputerManageableCmd(this.computerId, C42PermissionApp.Computer.READ), session);

		// Find the computer
		ConfigFindByComputerIdQuery query = new ConfigFindByComputerIdQuery(this.computerId);
		final Config c = this.db.find(query);
		if (c == null) {
			return null;
		}

		return c;
	}
}
