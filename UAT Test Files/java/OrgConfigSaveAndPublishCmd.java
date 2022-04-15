package com.code42.org;

import com.backup42.CpcConstants;
import com.backup42.common.config.ServiceConfig;
import com.backup42.computer.ConfigServices;
import com.code42.computer.Config;
import com.code42.config.ConfigFindByIdQuery;
import com.code42.config.ConfigUpdateCmd;
import com.code42.core.CommandException;
import com.code42.core.OrgDef;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.code42.exception.DebugRuntimeException;
import com.code42.hibernate.Persistence;

/**
 * Save the org and publish to children. Publishing creates a publish config instruction record which is applied by the
 * publish config worker.
 * 
 * This method is called by the manage config web UI.
 * 
 * @param org
 * @param serviceConfig
 * @param revertingCustomConfig
 * @param publish
 * @param adminUserId
 * @return
 */
public class OrgConfigSaveAndPublishCmd extends DBCmd<Config> {

	private final BackupOrg org;
	private final ServiceConfig serviceConfig;
	private final boolean revertingCustomConfig;
	private final boolean publishToAllOrgs;
	private final boolean publishToOrgs;
	private final boolean publishToComputers;
	private final int adminUserId;

	/**
	 * Save the org and publish to children. Publishing creates a publish config instruction record which is applied by
	 * the publish config worker.
	 * 
	 * This method is called by the manage config web UI.
	 * 
	 * @param org
	 * @param serviceConfig
	 * @param revertingCustomConfig
	 * @param publish
	 * @param adminUserId
	 * @return
	 */
	public OrgConfigSaveAndPublishCmd(BackupOrg org, ServiceConfig serviceConfig, boolean revertingCustomConfig,
			boolean publishToAllOrgs, boolean publishToOrgs, boolean publishToComputers, int adminUserId) {
		this.org = org;
		this.serviceConfig = serviceConfig;
		this.revertingCustomConfig = revertingCustomConfig;
		this.publishToAllOrgs = publishToAllOrgs;
		this.publishToOrgs = publishToOrgs;
		this.publishToComputers = publishToComputers;
		this.adminUserId = adminUserId;
	}

	@Override
	public Config exec(CoreSession session) throws CommandException {
		ServiceConfig svcConfig = this.serviceConfig;
		try {
			Persistence.beginTransaction();
			Config config = null;
			if (this.org.getConfigId() != null) {
				config = this.db.find(new ConfigFindByIdQuery(this.org.getConfigId()));
			}
			if (config == null) {
				// new
				config = new Config();
			}

			// if we're reverting a custom config, then we want to apply our parent's config instead of whatever was submitted
			if (this.revertingCustomConfig) {
				Config parentConfig = this.getConfigFromParent(this.org);
				svcConfig = parentConfig.toServiceConfig();
			}

			CoreBridge.run(new ConfigUpdateCmd(config, svcConfig));

			// Save the org; Only do this if the config id changed
			boolean orgChanged = false;
			OrgUpdateCmd.Builder updateBuilder = new OrgUpdateCmd.Builder(this.org.getOrgId()).allowUpdateCpOrg();
			if (this.org.getOrgId().intValue() == CpcConstants.Orgs.ADMIN_ID) {
				// the admin org ALWAYS supports a custom config
				updateBuilder.allowCustomConfig();
				updateBuilder.allowUpdateAdminOrg();
				orgChanged = true;
			}
			if (!config.getConfigId().equals(this.org.getConfigId())) {
				updateBuilder.configId(config.getConfigId());
				orgChanged = true;
			}
			if (orgChanged) {
				this.run(updateBuilder.build(), session);
			}

			if (this.publishToOrgs || this.publishToComputers) {
				// and finally, optionally publish this config to orgs that depend on us. NOTE: we send the serviceConfig as the
				// PublishedConfig will get its own config entry that is not dependent on the orgs object.
				ConfigServices.getInstance().publishConfig(this.org.getOrgId(), svcConfig, this.publishToAllOrgs,
						this.publishToOrgs, this.publishToComputers, this.adminUserId);
			}
			Persistence.commit();

			return config;

		} catch (final Throwable t) {
			throw new RuntimeException("Exception saving service config for org=" + this.org, t);
		} finally {
			Persistence.endTransaction();
		}

	}

	/**
	 * A method for pulling a config from your parent- which is responsible for providing an accurate config to you. This
	 * will build out and missing configs up the org tree.
	 * 
	 * @param org
	 * @return the config from my parent
	 */
	private Config getConfigFromParent(Org org) throws Exception {
		if (OrgDef.ADMIN.getOrgUid().equals(org.getOrgUid())) {
			throw new DebugRuntimeException("The admin org has no authoritative parent");
		}

		int parentOrgId = org.getParentOrgId() != null ? org.getParentOrgId() : CpcConstants.Orgs.ADMIN_ID;
		BackupOrg parentOrg = CoreBridge.runNoException(new OrgFindByIdCmd(parentOrgId));
		Config config = null;
		if (parentOrg.getConfigId() != null) {
			config = this.db.find(new ConfigFindByIdQuery(parentOrg.getConfigId()));
			return config;
		}

		// we didn't find a config, *RECURSE* up the tree
		config = ConfigServices.getInstance().getConfigForOrg(parentOrg);
		return config;
	}

}
