package com.code42.server;

import com.backup42.CpcConstants;
import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.impl.DBCmd;

public class ServerSettingsInfoFindByServerCmd extends DBCmd<ServerSettingsInfo> {

	private final int serverId;

	public ServerSettingsInfoFindByServerCmd(Integer serverId) {
		if (serverId == null) {
			this.serverId = CpcConstants.ServerSettings.DEFAULT_SERVER_SETTINGS_ID;
		} else {
			this.serverId = serverId;
		}
	}

	@Override
	public ServerSettingsInfo exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		final ServerSettingsInfo info = new ServerSettingsInfo();
		try {
			ServerSettings ss = this.db.find(new ServerSettingsFindByIdQuery(this.serverId));
			if (ss == null) {
				ss = new ServerSettings();
				ss.setServerId(this.serverId);
			}
			info.setNullFields(ss, false);

			if (this.serverId > 1) {
				ss = this.db.find(new ServerSettingsFindByIdQuery(CpcConstants.ServerSettings.DEFAULT_SERVER_SETTINGS_ID));
				info.setNullFields(ss, true);
			}
		} catch (DBServiceException e) {
			throw new CommandException("Unable to find server settings for server " + this.serverId, e);
		}
		return info;
	}
}
