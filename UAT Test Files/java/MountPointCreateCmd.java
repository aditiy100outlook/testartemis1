package com.code42.server.mount;

import com.backup42.app.cpc.clusterpeer.IMasterPeerController;
import com.backup42.app.cpc.clusterpeer.PeerCommunicationException;
import com.backup42.common.perm.C42PermissionPro;
import com.backup42.history.CpcHistoryLogger;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.core.platform.IPlatformService;
import com.code42.core.relation.IRelationService;
import com.code42.core.relation.RelationException;
import com.code42.hibernate.aftertx.AfterTxRunnable;
import com.code42.hibernate.aftertx.IAfterTxRunnable.Priority;
import com.code42.logging.Logger;
import com.code42.messaging.entityupdate.MountPointCreateMessage;
import com.code42.server.mount.MountPointWriteSpeedTestCmd.MountPointWriteSpeedTestCmdOwnerImpl;
import com.code42.server.node.Node;
import com.code42.server.node.NodeFindByIdQuery;
import com.code42.server.sync.IEntityNotificationController;
import com.code42.server.sync.SyncMountPoint;
import com.code42.stats.publisher.PublishOnlineStatus;
import com.code42.utils.LangUtils;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;
import com.google.inject.Inject;

/**
 * Create a mount point. Handles all error messages and syncing to remote servers.
 */
public class MountPointCreateCmd extends DBCmd<MountPoint> {

	private final static Logger log = Logger.getLogger(MountPointCreateCmd.class);

	private final Builder b;

	@Inject
	IMasterPeerController mpc;
	@Inject
	IMountController mountController;
	@Inject
	IEntityNotificationController entityController;
	@Inject
	IRelationService relation;

	public enum Error {

		NAME_IS_NOT_UNIQUE,
		ABSOLUTE_PATH_REQUIRED,
		PATH_IS_NOT_ELIGIBLE,
		FAILED_TO_CREATE_MOUNT_DIR,
		MAX_MOUNTS_FOR_SERVER,
		SERVER_ID_MISSING,
		NAME_MISSING,
		PATH_MISSING,
		SERVER_UNAVAILABLE
	}

	private MountPointCreateCmd(Builder b) {
		super();
		this.b = b;
	}

	@Override
	public MountPoint exec(CoreSession session) throws CommandException {

		if (!this.env.isMaster()) {
			throw new CommandException("Only Masters may create mounts.");
		}

		final MountPoint mount = new MountPoint();

		final Node ownerNode = this.db.find(new NodeFindByIdQuery(this.b.nodeId));

		// path
		final String path = this.run(new MountPointConstructPathCmd(this.b.prefixPath), session);
		mount.setPrefixPath(path);

		// validate path with owner
		// messaging cannot be inside of a transaction
		if (this.b.validatePath) {
			final ValidatePathMessage msg = new ValidatePathMessage(path, null);
			ValidatePathMessageResponse response = this.mountController.send(ownerNode, msg);
			if (!response.isExists()) {
				throw new CommandException(Error.PATH_IS_NOT_ELIGIBLE, response.toString(), path);
			}
		}

		try {
			this.db.beginTransaction();

			this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

			mount.setServerId(this.b.nodeId);

			// name, must be unique
			mount.setName(this.b.name);
			MountPoint tmpName = this.db.find(new MountPointFindByNameQuery(mount.getName()));
			if (tmpName != null) {
				throw new CommandException(Error.NAME_IS_NOT_UNIQUE, "Mounts must have unique names", mount);
			}

			// volume
			final String volume;
			if (LangUtils.hasValue(this.b.volumeLabel)) {
				volume = this.b.volumeLabel.get();
			} else {
				volume = this.run(new MountPointConstructVolumeCmd(mount), session);
			}
			mount.setVolumeLabel(volume);

			// note, as long as they entered something into the note, we'll save it
			if (!(this.b.note instanceof None)) {
				mount.setNote(this.b.note.get());
			}

			// persist; we need a mountPointId before a storage node can sync the data
			this.db.create(new MountPointCreateQuery(mount));

			// notify owner that a new mount exists
			final CoreSession fSession = session;
			final boolean sendNotification = this.b.sendNotification;
			this.db.afterTransaction(new AfterTxRunnable(Priority.NORMAL) {

				public void run() {
					CpcHistoryLogger.info(fSession, "created store point:{}/{} path:{}", mount.getMountPointId(),
							mount.getName(), mount.getPrefixPath());

					if (sendNotification) { // Only false when attaching a StorageNode for the first time; let the rest go

						// the owner node will run a speed test; we want to incorporate those results before handing the mount
						// object back to the caller
						SyncMountPoint sMount = null;
						try {
							sMount = MountPointCreateCmd.this.mpc.sendCreateMountPoint(ownerNode, mount);
						} catch (Exception e1) {
							log.warn("Unable to send mount update to node: {}", ownerNode);
						}

						MountPointCreateMessage msg = new MountPointCreateMessage(sMount);
						try {
							MountPointCreateCmd.this.entityController.send(msg);
						} catch (PeerCommunicationException e) {
							log.warn("Unable to send mountpoint create message; msg={}", msg, e);
						}

						try {
							if (sMount != null) {
								mount.setBps(sMount.getBps());
								MountPointCreateCmd.this.db.update(new MountPointUpdateQuery(mount));
							}
						} catch (Exception e) {
							log.warn("Failed to update mount post-creation", e, mount);
						}
					}

					PublishOnlineStatus.clearCache();

					/*
					 * Creating a new mount point is a significant action that shouldn't happen very often. No clean way to
					 * rebuild IRelationService incrementally so force a full synch here.
					 */
					try {

						MountPointCreateCmd.this.relation.synchronize();
					} catch (RelationException re) {
						log.warn("Exception while synchronizing relation service after mount point create", re);
					}
				}
			});

			this.db.commit();
		} finally {
			this.db.endTransaction();
		}

		return mount;
	}

	/**
	 * Defines the required and optional fields for creation.
	 */
	public static class Builder {

		private final Integer nodeId;
		private String name;
		private String prefixPath;
		private Option<String> volumeLabel = None.getInstance();
		private Option<String> note = None.getInstance();

		private boolean validatePath = true;
		private boolean sendNotification = true;

		public Builder(Integer nodeId, String name, String prefixPath) {
			super();
			this.nodeId = nodeId;
			this.name = name;
			this.prefixPath = prefixPath;
		}

		public Builder volumeLabel(String volumeLabel) {
			if (LangUtils.hasValue(volumeLabel)) {
				this.volumeLabel = new Some<String>(volumeLabel);
			}
			return this;
		}

		public Builder note(String note) {
			if (LangUtils.hasValue(note)) {
				this.note = new Some(note.trim());
			}
			return this;
		}

		/**
		 * An override that circumvents the remote node path validation during mount creation; used during testing and when
		 * MountPoints are created during Master -> StorageNode conversion. No other use cases are known.
		 */
		public Builder validatePath(boolean validatePath) {
			this.validatePath = validatePath;
			return this;
		}

		public Builder sendNotification(boolean sendNotification) {
			this.sendNotification = sendNotification;
			return this;
		}

		public MountPointCreateCmd build() throws CommandException {
			this.validate();
			return new MountPointCreateCmd(this);
		}

		private void validate() throws CommandException {
			if (this.nodeId == null) {
				throw new CommandException(Error.SERVER_ID_MISSING, "Unable to create store point, missing nodeId");
			}
			if (!LangUtils.hasValue(this.name)) {
				throw new CommandException(Error.NAME_MISSING, "Unable to create store point, missing name.");
			}
			if (!LangUtils.hasValue(this.prefixPath)) {
				throw new CommandException(Error.PATH_MISSING, "Unable to create store point, missing path.");
			}
			this.name = this.name.trim();
			this.prefixPath = this.prefixPath.trim();
		}
	}

	/**
	 * Locally create the filesystem for a mount point. At this point we do not know, or care, if we're an Authority or
	 * Storage.
	 */
	public static class MountPointCreateCmdOwnerImpl extends DBCmd<Void> {

		private final MountPoint mount;

		@Inject
		IPlatformService platformService;

		public MountPointCreateCmdOwnerImpl(MountPoint mount) {
			super();
			this.mount = mount;
		}

		@Override
		public Void exec(CoreSession session) throws CommandException {

			try {
				this.db.beginTransaction();

				this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

				CpcHistoryLogger.info(session, "filesystem: created store point:{}/{} path:{}", this.mount.getMountPointId(),
						this.mount.getName(), this.mount.getPrefixPath());

				// CREATE FILESYSTEM
				final String error = this.platformService.buildMountPoint(this.mount);
				if (error != null) {
					throw new CommandException(Error.FAILED_TO_CREATE_MOUNT_DIR, error, this.mount);
				}

				// TEST SPEED
				this.run(new MountPointWriteSpeedTestCmdOwnerImpl(this.mount.getMountPointId()), session);

				this.db.commit();
			} finally {
				this.db.endTransaction();
			}

			return null;
		}
	}
}
