package com.code42.server.mount;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.server.node.Node;
import com.code42.server.node.NodeFindByMountQuery;
import com.code42.server.node.ProviderNode;

public class MountPointIsProviderCmd extends DBCmd<Boolean> {

	private final int mountPointId;

	public MountPointIsProviderCmd(int mountPointId) {
		super();
		this.mountPointId = mountPointId;
	}

	@Override
	public Boolean exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		final Node node = this.db.find(new NodeFindByMountQuery(this.mountPointId));
		final boolean provider = node instanceof ProviderNode;
		return provider;
	}
}
