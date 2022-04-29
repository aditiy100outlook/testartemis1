package com.code42.balance;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.balance.admin.MountEmptyCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.server.mount.MountPoint;

/**
 * Empty this mount to the destination as a whole.
 */
public class BalanceMountToDestinationCmd extends DBCmd<Void> {

	private final int mountId;

	public BalanceMountToDestinationCmd(int mountId) {
		super();
		this.mountId = mountId;
	}

	public BalanceMountToDestinationCmd(MountPoint mount) {
		this(mount.getMountPointId());
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		this.run(new MountEmptyCmd(this.mountId, null), session);

		return null;
	}
}
