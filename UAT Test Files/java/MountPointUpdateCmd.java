package com.code42.server.mount;

import com.backup42.app.cpc.clusterpeer.PeerCommunicationException;
import com.backup42.common.perm.C42PermissionPro;
import com.backup42.history.CpcHistoryLogger;
import com.code42.balance.admin.MountPublishUpdateCmd;
import com.code42.balance.engine.DataBalanceWorker;
import com.code42.balance.engine.DataBalancer;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.code42.hibernate.aftertx.AfterTxRunnable;
import com.code42.hibernate.aftertx.IAfterTxRunnable.Priority;
import com.code42.logging.Logger;
import com.code42.messaging.entityupdate.MountPointUpdateMessage;
import com.code42.server.node.Node;
import com.code42.server.node.NodeFindByIdQuery;
import com.code42.server.sync.IEntityNotificationController;
import com.code42.stats.gather.OnlineStatusSpaceCallable;
import com.code42.stats.publisher.PublishOnlineStatus;
import com.code42.utils.LangUtils;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;
import com.google.inject.Inject;

public class MountPointUpdateCmd extends DBCmd<MountPoint> {

	private static Logger log = Logger.getLogger(MountPointUpdateCmd.class);

	private final Builder b;

	@Inject
	IEntityNotificationController entityController;
	@Inject
	IMountController mountController;

	public enum Error {
		// VOLUME_DIR_IS_NOT_PRESENT, // never used!
		NAME_REQUIRED,
		PATH_REQUIRED,
		// PATH_INVALID, // never used !
		MOVE_INVALID,
		SERVER_UNAVAILABLE
	}

	private MountPointUpdateCmd(Builder b) {
		super();
		this.b = b;
	}

	@Override
	public MountPoint exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		MountPoint mount = this.db.find(new MountPointFindByIdQuery(this.b.mountId));

		// name, must be unique
		if (!(this.b.name instanceof None)) {
			MountPoint tmpName = this.db.find(new MountPointFindByNameQuery(this.b.name.get()));
			if (tmpName != null && tmpName.getMountPointId() != this.b.mountId) {
				throw new CommandException(MountPointCreateCmd.Error.NAME_IS_NOT_UNIQUE, "Mounts must have unique names", mount);
			}
			mount.setName(this.b.name.get());
		}

		// path
		// NOTE: this check must be outside the transaction because it sends a message
		if (!(this.b.prefixPath instanceof None)) {
			this.ensureNotCPCMaster();
			final String path = this.run(new MountPointConstructPathCmd(this.b.prefixPath.get()), session);
			mount.setPrefixPath(path);

			// validate path with owner
			final ValidatePathMessage msg = new ValidatePathMessage(path, mount.getVolumeLabel());
			ValidatePathMessageResponse response = null;

			final Node ownerNode = this.db.find(new NodeFindByIdQuery(mount.getServerId()));
			try {
				response = this.mountController.send(ownerNode, msg);
			} catch (PeerCommunicationException e) {
				log.warn("Unable to send message to Node: {}", ownerNode);
				throw new CommandException(MountPointUpdateCmd.Error.SERVER_UNAVAILABLE, "Server Unavailable", mount);
			}
			if (response.isInvalidMove()) {
				throw new CommandException(Error.MOVE_INVALID, response.toString(), mount);
			}
			if (!response.isExists()) {
				throw new CommandException(MountPointCreateCmd.Error.PATH_IS_NOT_ELIGIBLE, response.toString(), mount);
			}
		}

		boolean adjustStatus = false;

		MountPointEnableCmd.Builder bb = new MountPointEnableCmd.Builder(this.b.mountId);

		if (!(this.b.balancingData instanceof None)) {
			bb.balanceData(this.b.balancingData.get());
			adjustStatus = true;
		}

		if (!(this.b.acceptingNewComputers instanceof None)) {
			bb.acceptNewComputers(this.b.acceptingNewComputers.get());
			adjustStatus = true;
		}

		if (!(this.b.acceptingInboundBackup instanceof None)) {
			bb.enable(this.b.acceptingInboundBackup.get());
			adjustStatus = true;
		}

		try {
			this.db.beginTransaction();

			if (adjustStatus) {
				// reassign the mount to ensure the updates are present in subsequent operations
				mount = this.run(new MountPointEnableCmd(bb), session);

				// The validation for fields name and prefixPath were already performed, so only updating the mount object here
				if (!(this.b.name instanceof None)) {
					mount.setName(this.b.name.get());
				}
				if (!(this.b.prefixPath instanceof None)) {
					final String path = this.run(new MountPointConstructPathCmd(this.b.prefixPath.get()), session);
					mount.setPrefixPath(path);
				}
			}

			// note, as long as they entered something into the note, we'll save it
			if (!(this.b.note instanceof None)) {
				mount.setNote(this.b.note.get());
			}

			// UPDATE
			this.db.update(new MountPointUpdateQuery(mount));
			CpcHistoryLogger.info(session, "Updated store point:{}/{} path:{}", mount.getMountPointId(), mount.getName(),
					mount.getPrefixPath());

			this.db.afterTransaction(new MountPublishUpdateCmd(mount), session);

			this.db.afterTransaction(new AfterTxRunnable(Priority.NORMAL) {

				public void run() {
					PublishOnlineStatus.clearCache();
				}
			});

			this.db.commit();
		} finally {
			this.db.endTransaction();
		}

		return null;
	}

	/**
	 * Defines the fields that may be updated.
	 */
	public static class Builder {

		private final int mountId;

		private Option<String> name = None.getInstance();
		private Option<String> prefixPath = None.getInstance();
		private Option<String> note = None.getInstance();
		private Option<Boolean> acceptingInboundBackup = None.getInstance();
		private Option<Boolean> balancingData = None.getInstance();
		private Option<Boolean> acceptingNewComputers = None.getInstance();

		public Builder(int mountId) {
			super();
			this.mountId = mountId;
		}

		public Builder name(String name) {
			if (LangUtils.hasValue(name)) {
				this.name = new Some(name.trim());
			}
			return this;
		}

		public Builder prefixPath(String prefixPath) {
			if (LangUtils.hasValue(prefixPath)) {
				this.prefixPath = new Some(prefixPath.trim());
			}
			return this;
		}

		public Builder note(String note) {
			if (LangUtils.hasValue(note)) {
				this.note = new Some(note.trim());
			}
			return this;
		}

		public Builder acceptingInboundBackup(Boolean bool) {
			if (bool != null) {
				this.acceptingInboundBackup = new Some<Boolean>(bool);
			} else {
				this.acceptingInboundBackup = None.getInstance();
			}
			return this;
		}

		public Builder balancingData(Boolean bool) {
			if (bool != null) {
				this.balancingData = new Some<Boolean>(bool);
			} else {
				this.balancingData = None.getInstance();
			}
			return this;
		}

		public Builder acceptingNewComputers(Boolean bool) {
			if (bool != null) {
				this.acceptingNewComputers = new Some<Boolean>(bool);
			} else {
				this.acceptingNewComputers = None.getInstance();
			}
			return this;
		}

		public MountPointUpdateCmd build() throws CommandException {
			this.validate();
			return new MountPointUpdateCmd(this);
		}

		private void validate() throws CommandException {
			if (!(this.name instanceof None) && !LangUtils.hasValue(this.name.get())) {
				throw new CommandException(Error.NAME_REQUIRED, "Unable to update store point, name required.");
			}
			if (!(this.prefixPath instanceof None) && !LangUtils.hasValue(this.prefixPath.get())) {
				throw new CommandException(Error.PATH_REQUIRED, "Unable to update store point, path required.");
			}
		}
	}

	/**
	 * Handle the after effects of updating a mount point.
	 */
	public static class MountPointUpdateCmdOwnerImpl extends DBCmd<Void> {

		private final MountPoint mount;
		private final boolean pathChanged;

		public MountPointUpdateCmdOwnerImpl(MountPoint mount, boolean pathChanged) {
			super();
			this.mount = mount;
			this.pathChanged = pathChanged;
		}

		@Override
		public Void exec(CoreSession session) throws CommandException {

			try {
				this.db.beginTransaction();

				// we only update the database if we're not an authority; if we ARE an authority, our database has already been
				// updated and we can merely do the notifications
				if (!this.env.isMaster()) {

					// STORAGE

					// verify path if it's been edited
					if (this.pathChanged) {
						boolean exists = false;
						try {
							exists = this.run(new PathValidateCmd(this.mount.getPrefixPath(), this.mount.getVolumeLabel()), session);
						} catch (CommandException e) {
							if (e.getMessage().equals("Invalid Move")) {
								throw new CommandException(Error.MOVE_INVALID, "Invalid Move");
							} else {
								throw e;
							}
						}

						if (!exists) {
							throw new CommandException("Prefix path does not exist; edit rejected.");
						}
					}

					this.db.update(new MountPointUpdateQuery(this.mount));
				}

				final CoreSession finalSession = session;

				// notify interested parties that we changed something
				this.db.afterTransaction(new AfterTxRunnable(Priority.NORMAL) {

					public void run() {

						CpcHistoryLogger.info(finalSession, "Updated MountPoint: {}", MountPointUpdateCmdOwnerImpl.this.mount);

						if (!MountPointUpdateCmdOwnerImpl.this.mount.getBalanceData()) {
							DataBalancer.getInstance().cancelAutoBalance(MountPointUpdateCmdOwnerImpl.this.mount.getMountPointId());
						}

						// tell the clients to stop backing up
						CoreBridge.getCentralService().getBackup().resetBackupServerConnections(
								MountPointUpdateCmdOwnerImpl.this.mount);
						// handle the change (if any); this also tells clients that we are ready again
						CoreBridge.getCentralService().getBackup().resetManifestPaths(MountPointUpdateCmdOwnerImpl.this.mount);
						// the volume watcher wakes up to check for path/availability changes
						CoreBridge.getCentralService().getBackup().getVolumeWatcher().wakeup();
						// the data balancer wakes up to go back to work
						DataBalanceWorker.getInstance().wakeup(true, true);

						OnlineStatusSpaceCallable.clearCache();
					}
				});

				this.db.commit();
			} catch (Exception e) {
				throw new CommandException("Unable to update mountPoint: {}", this.mount, e);
			} finally {
				this.db.endTransaction();
			}
			return null;
		}
	}

	public static class MountPointUpdateCmdAuthorityImpl extends AbstractCmd<Void> {

		public MountPointUpdateCmdAuthorityImpl(MountPointUpdateMessage msg) {

		}

		@Override
		public Void exec(CoreSession session) throws CommandException {
			PublishOnlineStatus.clearCache();
			return null;
		}

	}
}
