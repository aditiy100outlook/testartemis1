package com.code42.setting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.code42.computer.ComputerSso;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.businessobjects.BusinessObjectsException;
import com.code42.core.businessobjects.IBusinessObjectsService;
import com.code42.core.hierarchy.IHierarchyService;
import com.code42.core.impl.DBCmd;
import com.code42.utils.ArrayUtils;
import com.google.inject.Inject;

/**
 * Set these settings for the requesting guid whether they already exist or not.
 * 
 */
public class SettingPutForGuidCmd extends DBCmd<List<CoreSetting>> {

	private final long guid;
	private final Collection<SettingPacket> settings;
	@Inject
	private IBusinessObjectsService bos;
	@Inject
	private IHierarchyService hierarchyService;

	public SettingPutForGuidCmd(long guid, Collection<SettingPacket> settings) {
		super();
		this.guid = guid;
		this.settings = settings;
	}

	public SettingPutForGuidCmd(long guid, SettingPacket... settings) {
		this(guid, ArrayUtils.asList(settings));
	}

	@Override
	public List<CoreSetting> exec(CoreSession session) throws CommandException {

		if (!this.env.isMaster()) {
			throw new CommandException("Settings may only be written on an authority node");
		}

		ComputerSso cSso = null;
		try {
			cSso = this.bos.getComputerByGuid(this.guid);
		} catch (BusinessObjectsException e) {
			// Throw exception below since SSO will remain null
		}

		if (cSso == null) {
			throw new CommandException("Failed to identify a computer from the source guid", this.guid);
		}

		this.run(new IsComputerManageableCmd(cSso.getComputerId(), C42PermissionApp.Computer.UPDATE), session);
		this.run(new IsUserManageableCmd(cSso.getUserId(), C42PermissionApp.User.UPDATE), session);

		final List<CoreSetting> settings = new ArrayList();
		try {
			this.db.beginTransaction();

			boolean authedForOrg = false;

			for (SettingPacket packet : this.settings) {
				CoreSetting setting = SettingFactory.buildForComputer(cSso, packet, this.hierarchyService);

				// if they try for an org setting, make sure they have permission to do that
				if (CoreSetting.Scope.ORG.equals(setting.getScope()) && !authedForOrg) {
					this.run(new IsOrgManageableCmd(setting.getOrgId(), C42PermissionApp.Org.UPDATE_BASIC), session);
					authedForOrg = true;
				}

				setting = this.run(new SettingForceUpdateCmd(setting), session);
				settings.add(setting);
			}

			this.db.commit();
		} finally {
			this.db.endTransaction();
		}

		this.run(new SettingUpdateNotificationCmd(settings), this.auth.getAdminSession());

		return settings;
	}
}
