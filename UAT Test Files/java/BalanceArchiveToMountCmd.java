package com.code42.balance;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.balance.admin.ArchiveMoveToMountCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

/**
 * Move an archive to the specified mount.
 */
public class BalanceArchiveToMountCmd extends DBCmd<Void> {

	private final long guid;
	private final int srcMountId;
	private final int dstMountId;

	public BalanceArchiveToMountCmd(long guid, int srcMountId, int dstMountId) {
		super();
		this.guid = guid;
		this.srcMountId = srcMountId;
		this.dstMountId = dstMountId;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		this.run(new ArchiveMoveToMountCmd(this.guid, this.srcMountId, this.dstMountId), session);

		return null;
	}
}
