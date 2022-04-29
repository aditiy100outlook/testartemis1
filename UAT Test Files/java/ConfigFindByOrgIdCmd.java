package com.code42.config;

import com.code42.computer.Config;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

/**
 * Get the org-level default computer config. A config is lazily created from a copy of the parent if none.
 */
public class ConfigFindByOrgIdCmd extends DBCmd<Config> {

	private int orgId;

	public ConfigFindByOrgIdCmd(int orgId) {
		this.orgId = orgId;
	}

	@Override
	public Config exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionApp.Org.READ, this.orgId);

		final ConfigFindByOrgIdQuery query = new ConfigFindByOrgIdQuery(this.orgId);
		Config c = this.db.find(query);
		if (c != null) {
			return c;
		}

		// Lazily create the config if one doesn't exist.
		//
		// Lazily create the config, if necessary. This is here only for legacy support. Configs
		// should now be created when the org is created. If a conversion job is ever written, this
		// code below should return null instead.
		return this.runtime.run(new OrgComputerConfigCreateCmd(this.orgId), this.auth.getSystemSession());
	}
}
