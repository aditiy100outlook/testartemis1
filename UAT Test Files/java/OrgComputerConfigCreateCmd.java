package com.code42.config;

import com.backup42.CpcConstants;
import com.backup42.common.config.ServiceConfig;
import com.backup42.computer.ConfigServices;
import com.code42.computer.Config;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.BackupOrg;
import com.code42.org.OrgConfigSaveAndPublishCmd;
import com.code42.org.OrgFindByIdCmd;

/**
 * Create the default computer config for an Org.
 */
public class OrgComputerConfigCreateCmd extends DBCmd<Config> {

	private static final Logger log = LoggerFactory.getLogger(OrgComputerConfigCreateCmd.class);

	public enum Error {
		ORG_NOT_FOUND, ALREADY_EXISTS
	}

	private int orgId;

	public OrgComputerConfigCreateCmd(int orgId) {
		this.orgId = orgId;
	}

	@Override
	public Config exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionApp.Org.READ, this.orgId);

		final BackupOrg org = this.runtime.run(new OrgFindByIdCmd(this.orgId), session);
		if (org == null) {
			throw new CommandException(Error.ORG_NOT_FOUND, "Unable to create config for orgId={}, org not found.",
					this.orgId);
		}
		if (org.getConfigId() != null) {
			throw new CommandException(Error.ALREADY_EXISTS,
					"Unable to create config for orgId={}, config already exists for org.", this.orgId);
		}

		String parent;
		ServiceConfig sc = null;
		if (this.orgId != CpcConstants.Orgs.ADMIN_ID) {
			// Get parent config
			try {
				final int parentOrgId = (org.getParentOrgId() != null) ? org.getParentOrgId() : CpcConstants.Orgs.ADMIN_ID;
				parent = "" + parentOrgId;

				// Uses elevated admin privs since the current user won't have access to parent org.
				final Config c = this.runtime.run(new ConfigFindByOrgIdCmd(parentOrgId), this.auth.getAdminSession());
				sc = c.toServiceConfig();
			} catch (Exception e) {
				throw new CommandException("Unable to create config for orgId={}.", this.orgId, e);
			}
		} else {
			// Get from disk
			sc = ConfigServices.getInstance().loadDefaultServiceConfigFromDisk();
			parent = "DISK";
		}

		final Config c = this.run(new OrgConfigSaveAndPublishCmd(org, sc, false, false, false, false, 0), session);

		if (c == null) {
			throw new CommandException("Unable to create config for orgId={}, failed to save config.", this.orgId);
		}
		log.info("Config created for orgId={} from parent={}", this.orgId, parent, c);
		return c;
	}
}
