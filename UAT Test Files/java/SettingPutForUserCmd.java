package com.code42.setting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.businessobjects.BusinessObjectsException;
import com.code42.core.businessobjects.IBusinessObjectsService;
import com.code42.core.hierarchy.IHierarchyService;
import com.code42.core.impl.DBCmd;
import com.code42.user.UserSso;
import com.code42.utils.ArrayUtils;
import com.google.inject.Inject;

/**
 * Set these settings for the requesting user ID whether they already exist or not.
 * 
 */
public class SettingPutForUserCmd extends DBCmd<List<CoreSetting>> {

	/* TODO: Might want to convert this to user UID at some point... */
	private final int userId;
	private final Collection<SettingPacket> settings;
	@Inject
	private IBusinessObjectsService bos;
	@Inject
	private IHierarchyService hierarchyService;

	public SettingPutForUserCmd(int userId, Collection<SettingPacket> settings) {
		super();
		this.userId = userId;
		this.settings = settings;
	}

	public SettingPutForUserCmd(int userId, SettingPacket... settings) {
		this(userId, ArrayUtils.asList(settings));
	}

	@Override
	public List<CoreSetting> exec(CoreSession session) throws CommandException {

		if (!this.env.isMaster()) {
			throw new CommandException("Settings may only be written on an authority node");
		}

		UserSso sso = null;
		try {
			sso = this.bos.getUser(this.userId);
		} catch (BusinessObjectsException e) {
			// Leave sso as null and throw exception below
		}

		if (sso == null) {
			throw new CommandException("Failed to identify a user from the user ID", this.userId);
		}

		this.run(new IsUserManageableCmd(sso.getUserId(), C42PermissionApp.User.UPDATE), session);

		final List<CoreSetting> settings = new ArrayList();
		try {
			this.db.beginTransaction();

			boolean authedForOrg = false;

			for (SettingPacket packet : this.settings) {

				CoreSetting setting = SettingFactory.buildForUser(sso, packet, this.hierarchyService);

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
