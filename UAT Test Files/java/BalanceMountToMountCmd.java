package com.code42.balance;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.balance.admin.MountEmptyCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

/**
 * Empty one mount to another.
 */
public class BalanceMountToMountCmd extends DBCmd<Void> {

	private final int srcMountId;
	private final int tgtMountId;

	public BalanceMountToMountCmd(int srcMountId, int tgtMountId) {
		super();
		this.srcMountId = srcMountId;
		this.tgtMountId = tgtMountId;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		this.run(new MountEmptyCmd(this.srcMountId, this.tgtMountId), session);

		return null;
	}
}
