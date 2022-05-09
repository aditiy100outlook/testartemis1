package com.code42.server.mount;

import java.util.List;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

public class MountPointFindByServerCmd extends DBCmd<List<MountPoint>> {

	private final int serverId;

	public MountPointFindByServerCmd(Integer serverId) {
		super();
		this.serverId = serverId;
	}

	@Override
	public List<MountPoint> exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		List<MountPoint> result = this.db.find(new MountPointFindByServerQuery(this.serverId));
		return result;
	}

}
