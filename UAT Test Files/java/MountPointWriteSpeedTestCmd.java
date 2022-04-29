package com.code42.server.mount;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.core.platform.IPlatformService;
import com.google.inject.Inject;

/**
 * Enable/Disable a mount.
 * 
 * Note that enabling/disabling has no effect on balancing. Disabling a mount is a short-term move that prevents backup
 * during maintenance.
 */
public class MountPointWriteSpeedTestCmd extends DBCmd<Void> {

	private final int mountPointId;

	@Inject
	IMountController mountController;

	public MountPointWriteSpeedTestCmd(int mountPointId) {
		super();
		this.mountPointId = mountPointId;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		MountPoint mount = this.db.find(new MountPointFindByIdQuery(this.mountPointId));
		final long bps = this.mountController.preformWriteTest(this.mountPointId);

		if (bps != MountPoint.bpsCode.TEST_FAILED) {
			mount.setBps(bps);
			this.db.update(new MountPointUpdateQuery(mount));
		}

		return null;
	}

	public static class MountPointWriteSpeedTestCmdOwnerImpl extends DBCmd<Long> {

		private final int mountPointId;

		@Inject
		IPlatformService platformService;

		public MountPointWriteSpeedTestCmdOwnerImpl(int mountPointId) {
			super();
			this.mountPointId = mountPointId;
		}

		@Override
		public Long exec(CoreSession session) throws CommandException {

			this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

			MountPoint mount = this.db.find(new MountPointFindByIdQuery(this.mountPointId));
			final long bps = this.platformService.performWriteSpeedTest(mount);
			if (bps != MountPoint.bpsCode.TEST_FAILED) {
				mount.setBps(bps);
				this.db.update(new MountPointUpdateQuery(mount));
			} else {
				throw new CommandException("TEST_FAILED");
			}

			return bps;
		}
	}
}