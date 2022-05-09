package com.code42.server.mount;

import com.backup42.common.perm.C42PermissionPro;
import com.backup42.history.CpcHistoryLogger;
import com.code42.balance.admin.MountPublishUpdateCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;

/**
 * Enable/Disable a mount.
 * 
 * Note that enabling/disabling has no effect on balancing. Disabling a mount is a short-term move that prevents backup
 * during maintenance.
 */
public class MountPointEnableCmd extends DBCmd<MountPoint> {

	private final Builder b;

	public MountPointEnableCmd(Builder b) {
		super();
		this.b = b;
	}

	@Override
	public MountPoint exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		final MountPoint mount;
		try {
			this.db.beginTransaction();

			mount = this.db.find(new MountPointFindByIdQuery(this.b.mountId));

			if (!(this.b.enable instanceof None)) {
				if (this.b.enable.get()) {
					mount.setStatusCode(MountPoint.StatusCode.ENABLED);
				} else {
					mount.setStatusCode(MountPoint.StatusCode.DISABLED);
				}
			}

			if (!(this.b.balanceData instanceof None)) {
				mount.setBalanceData(this.b.balanceData.get());
			}

			if (!(this.b.acceptNewComputers instanceof None)) {
				mount.setBalanceNewUsers(this.b.acceptNewComputers.get());
			}

			this.db.update(new MountPointUpdateQuery(mount));
			CpcHistoryLogger.info(session, "Mount updated by " + session + ", " + mount);

			// notify interested parties that we changed something
			this.db.afterTransaction(new MountPublishUpdateCmd(mount), session);

			this.db.commit();
		} finally {
			this.db.endTransaction();
		}

		return mount;
	}

	public static class Builder {

		private final int mountId;

		private Option<Boolean> enable = None.getInstance();
		private Option<Boolean> balanceData = None.getInstance();
		private Option<Boolean> acceptNewComputers = None.getInstance();

		public Builder(int mountId) {
			super();
			this.mountId = mountId;
		}

		public Builder(MountPoint mount) {
			this(mount.getMountPointId());
		}

		public Builder(MountPoint mount, boolean balance, boolean accept) {
			this(mount.getMountPointId(), balance, accept);
		}

		public Builder(int mountId, boolean balance, boolean accept) {
			this(mountId);
			this.balanceData(balance);
			this.acceptNewComputers(accept);
		}

		public Builder enable(boolean enable) {
			this.enable = new Some<Boolean>(enable);
			return this;
		}

		public Builder balanceData(boolean balance) {
			this.balanceData = new Some<Boolean>(balance);
			return this;
		}

		public Builder acceptNewComputers(boolean accept) {
			this.acceptNewComputers = new Some<Boolean>(accept);
			return this;
		}

		public MountPointEnableCmd build() {
			return new MountPointEnableCmd(this);
		}
	}
}
