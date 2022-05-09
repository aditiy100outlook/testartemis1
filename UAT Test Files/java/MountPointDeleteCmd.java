package com.code42.server.mount;

import java.io.File;
import java.io.FilenameFilter;
import java.util.List;

import org.hibernate.Session;

import com.backup42.app.cpc.clusterpeer.PeerCommunicationException;
import com.backup42.common.perm.C42PermissionPro;
import com.backup42.history.CpcHistoryLogger;
import com.code42.archiverecord.ArchiveRecordDeleteByMountCmd;
import com.code42.archiverecord.ArchiveSummaryDeleteByMountCmd;
import com.code42.archiverecord.ArchiveSummaryRollupDeleteByMountCmd;
import com.code42.balance.BalanceHistoryDeleteByMountCmd;
import com.code42.balance.DataBalanceCommandDeleteByMountCmd;
import com.code42.computer.Computer;
import com.code42.computer.ComputerFindByMountQuery;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.DeleteQuery;
import com.code42.core.impl.AbstractCmd;
import com.code42.core.impl.DBCmd;
import com.code42.core.relation.IRelationService;
import com.code42.core.relation.RelationException;
import com.code42.hibernate.aftertx.AfterTxRunnable;
import com.code42.hibernate.aftertx.IAfterTxRunnable.Priority;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.messaging.entityupdate.MountPointDeleteMessage;
import com.code42.server.node.Node;
import com.code42.server.node.NodeFindByIdQuery;
import com.code42.server.sync.IEntityNotificationController;
import com.code42.server.sync.SyncMountPoint;
import com.code42.stats.gather.OnlineStatusSpaceCallable;
import com.code42.stats.publisher.PublishOnlineStatus;
import com.google.inject.Inject;

public class MountPointDeleteCmd extends DBCmd<Void> {

	private final static Logger log = LoggerFactory.getLogger(MountPointDeleteCmd.class.getName());

	private final int mountId;
	private final boolean providerMount;
	@Inject
	private IMountController mountController;
	@Inject
	private IEntityNotificationController entityController;
	@Inject
	IRelationService relation;

	private boolean skipFileCheck = false;

	public enum Error {

		MOUNT_HAS_ASSIGNED_COMPUTERS, MOUNT_IS_NOT_EMPTY, COMMUNICATION_ERROR
	}

	public MountPointDeleteCmd(int mountId, boolean providerMount) {
		super();
		this.mountId = mountId;
		this.providerMount = providerMount;
	}

	public MountPointDeleteCmd(int mountId) {
		this(mountId, false);
	}

	public MountPointDeleteCmd(MountPoint mount) {
		this(mount.getMountPointId());
	}

	public MountPointDeleteCmd(MountPoint mount, boolean providerMount) {
		this(mount.getMountPointId(), providerMount);
	}

	/**
	 * Normally, this command will throw an exception if there are still files in the mount point. Call this method before
	 * executing the command to bypass this check.
	 */
	public MountPointDeleteCmd skipFileCheck() {
		this.skipFileCheck = true;
		return this; // chain
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.ensureNotCPCMaster();

		try {
			this.db.beginTransaction();

			this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

			final MountPoint mount = this.db.find(new MountPointFindByIdQuery(this.mountId));
			final Node node = this.db.find(new NodeFindByIdQuery(mount.getServerId()));

			if (!this.providerMount && mount instanceof ProviderMountPoint) {
				throw new CommandException("ProviderMount deletion not specified.");
			}

			this.delete(node, mount, session);

			// notify all destination nodes that the mount point is gone
			if (!(mount instanceof ProviderMountPoint)) {
				this.db.afterTransaction(new AfterTxRunnable(Priority.NORMAL) {

					public void run() {
						SyncMountPoint sMountPoint = new SyncMountPoint(mount, node);
						MountPointDeleteMessage msg = new MountPointDeleteMessage(sMountPoint);
						try {
							MountPointDeleteCmd.this.entityController.send(msg);
						} catch (PeerCommunicationException e) {
							log.warn("Unable to send mount point delete message; msg={}", msg, e);
						}
						PublishOnlineStatus.clearCache();

						/*
						 * Creating a new mount point is a significant action that shouldn't happen very often. No clean way to
						 * rebuild IRelationService incrementally so force a full synch here.
						 */
						try {

							MountPointDeleteCmd.this.relation.synchronize();
						} catch (RelationException re) {
							log.warn("Exception while synchronizing relation service after mount point create", re);
						}
					}
				});
			}

			this.db.commit();

			CpcHistoryLogger.info(session, "deleted store point:{}/{} path:{}", mount.getMountPointId(), mount.getName(),
					mount.getPrefixPath());
		} finally {
			this.db.endTransaction();
		}

		return null;
	}

	/**
	 * Note: The mount cannot be removed if it is "in use". For safety, we have a very liberal definition of in-use.
	 */
	private void delete(final Node node, final MountPoint mount, CoreSession session) throws CommandException {

		// validate that the mount point has no computers using it
		final List<Computer> computers = this.db.find(new ComputerFindByMountQuery(mount, null));
		if (!computers.isEmpty()) {
			throw new CommandException(Error.MOUNT_HAS_ASSIGNED_COMPUTERS,
					"Mount may not be deleted; computers are assigned", mount);
		}

		// attempt to investigate the remote filesystem, but if anything goes wrong we'll be forced to let the operation go
		// through. for all we know the remote server is never coming back
		if (!this.skipFileCheck && !(mount instanceof ProviderMountPoint)) {
			boolean empty = true;
			try {
				empty = this.mountController.send(node, new ValidateDeleteRequest(mount));
			} catch (Exception e) {
				throw new CommandException(Error.COMMUNICATION_ERROR, "Failed to verify mount contents with remote node", e,
						node, mount);
			}

			if (!empty) {
				throw new CommandException(Error.MOUNT_IS_NOT_EMPTY, "Mount may not be deleted; the directory is not empty",
						mount);
			}
		}

		// go ahead and delete from the db
		this.db.delete(new MountPointDeleteQuery(mount));
		// No need to log here. It happens at the end of this command.

		// also remove dependent objects. the key here is that if we leave this data around, there might be an id mismatch
		// in the future. also, without the parent mount object no one has the ability to query this info
		this.run(new ArchiveSummaryDeleteByMountCmd(mount), session);
		this.run(new ArchiveRecordDeleteByMountCmd(mount), session);
		this.run(new ArchiveSummaryRollupDeleteByMountCmd(mount), session);
		this.run(new DataBalanceCommandDeleteByMountCmd(mount), session);
		this.run(new BalanceHistoryDeleteByMountCmd(mount), session);

		// notify the remote node that the path is no longer needed
		if (!this.skipFileCheck && !(mount instanceof ProviderMountPoint)) {
			final IMountController fMountController = this.mountController;
			this.db.afterTransaction(new AfterTxRunnable(Priority.NORMAL) {

				public void run() {
					try {
						fMountController.send(node, new MountDeletedAsync(mount.getAbsolutePath()));
					} catch (Exception e) {
						log.info("Failed to send mount deleted message; mount deletion was not prevented", e, node, mount);
					}
				}
			});
		}
	}

	/**
	 * Check the mount path to see if there are any files still in it. Certain files are filtered out as being irrelevant.
	 */
	public static class MountPointDeleteVerifyCmdOwnerImpl extends DBCmd<Boolean> {

		private final ValidateDeleteRequest request;

		public MountPointDeleteVerifyCmdOwnerImpl(ValidateDeleteRequest request) {
			super();
			this.request = request;
		}

		@Override
		public Boolean exec(CoreSession session) throws CommandException {

			boolean empty = true;
			final MountPoint mount = this.db.find(new MountPointFindByIdQuery(this.request.getMountId()));

			if (mount.getServerId() != this.env.getMyNodeId()) {
				throw new CommandException("Mount is not owned by this node", mount);
			}

			log.info("Evaluating contents of local mount", mount);

			final File mpFile = new File(mount.getAbsolutePath());
			// validate that the mount point has no files in it
			final String[] mpContents = mpFile.list(new RemoveFilenameFilter());
			if ((mpContents != null) && (mpContents.length > 0)) {
				empty = false;
			}

			return empty;
		}
	}

	/**
	 * Filter out any filenames we allow to be present when deleting a mount directory.
	 */
	static class RemoveFilenameFilter implements FilenameFilter {

		// a set of file names which we allow to be present within a mount point at removal time
		private final String[] allowedAtRemoval = new String[] { "lost+found", "dbDumps" };

		public boolean accept(File file, String name) {
			// by default we want to accept everything, returning false only if we match on of our permitted names.
			boolean rv = true;
			for (String p : this.allowedAtRemoval) {
				rv = rv && (!name.equals(p));
			}
			return rv;
		}
	}

	/**
	 * Delete the mount volume folder. Do not throw an Exception if this fails; we're merely trying our best to clean up.
	 */
	public static class MountPointDeletedCmdOwnerImpl extends AbstractCmd<Void> {

		@Inject
		IRelationService relation;

		private final MountDeletedAsync request;

		public MountPointDeletedCmdOwnerImpl(MountDeletedAsync request) {
			super();
			this.request = request;
		}

		@Override
		public Void exec(CoreSession session) throws CommandException {
			// try to delete, but don't bother checking the result. we do not wish to do a forceful delete
			try {
				log.info("Attempting to delete local volume dir", this.request.getAbsolutePath());
				final File mpFile = new File(this.request.getAbsolutePath());
				mpFile.delete();
			} catch (Exception e) {
				log.info("Failed to deleted mount; will continue.", e, this.request);
			}
			OnlineStatusSpaceCallable.clearCache();

			/*
			 * Creating a new mount point is a significant action that shouldn't happen very often. No clean way to rebuild
			 * IRelationService incrementally so force a full synch here.
			 */
			try {

				this.relation.synchronize();
			} catch (RelationException re) {
				log.warn("Exception while synchronizing relation service after mount point create", re);
			}
			return null;
		}
	}

	/**
	 * Hidden Mount delete.
	 */
	private static class MountPointDeleteQuery extends DeleteQuery<MountPoint> {

		private final MountPoint mount;

		public MountPointDeleteQuery(MountPoint mount) {
			super();
			this.mount = mount;
		}

		@Override
		public void query(Session session) throws DBServiceException {
			try {
				this.db.beginTransaction();
				session.delete(this.mount);
				this.db.commit();
			} finally {
				this.db.endTransaction();
			}
		}
	}
}
