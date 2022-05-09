package com.code42.archive;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.backup42.common.command.ServiceCommand;
import com.backup42.common.perm.C42PermissionPro;
import com.backup42.history.CpcHistoryLogger;
import com.code42.computer.Computer;
import com.code42.computer.FriendComputerUsageUpdateMountCmd;
import com.code42.computer.FriendComputerUsageUpdateMountCmd.Increment;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.code42.hibernate.aftertx.AfterTxRunnable;
import com.code42.hibernate.aftertx.IAfterTxRunnable.Priority;
import com.code42.server.destination.Destination;
import com.code42.server.destination.DestinationFindByMountCmd;
import com.code42.server.mount.MountPoint;
import com.code42.server.mount.MountPointFindByDestinationQuery;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;

/**
 * Redistribute the provided guids from the source mount to other mounts in the same destination. The source guids will
 * be assigned to the available mounts in round-robin fashion.
 * 
 * Mounts that are not balancing are not valid targets. No override is provided.
 * 
 * Mounts that are not accepting new users are only valid targets if accepting or if overriding that setting.
 * 
 * WARNING: This is a REASSIGNMENT! The source archive, if one exists, will be LOST!
 * 
 * When would you use this? When the source mount is no longer available due to hardware failure and the source archives
 * are lost. The balancer cannot migrate away because there is nothing to copy; we are forced to simply assign the guids
 * to another mount and abandon whatever data may have been on the failed hardware.
 */
public class ArchiveRedistributeToDestinationCmd extends DBCmd<Void> {

	private final int srcMountId;
	private final Collection<Long> guids;
	private final boolean overrideNewUsers;

	public enum Error {

		NO_MOUNTS_AVAILABLE
	}

	/**
	 * Respects the mount.acceptNewUsers state.
	 */
	public ArchiveRedistributeToDestinationCmd(int srcMountId, Collection<Long> guids) {
		this(srcMountId, guids, false);
	}

	/**
	 * Redistribute with an optional override of the acceptNewUsers state.
	 * 
	 * @param srcMountId
	 * @param guids
	 * @param overrideNewUsers
	 */
	public ArchiveRedistributeToDestinationCmd(int srcMountId, Collection<Long> guids, boolean overrideNewUsers) {
		super();
		this.srcMountId = srcMountId;
		this.guids = guids;
		this.overrideNewUsers = overrideNewUsers;
	}

	public ArchiveRedistributeToDestinationCmd(MountPoint mount, Collection<Computer> computers, boolean overrideNewUsers) {
		super();
		this.srcMountId = mount.getMountPointId();
		this.overrideNewUsers = overrideNewUsers;
		this.guids = Collections2.transform(computers, new Function<Computer, Long>() {

			public Long apply(Computer c) {
				return c.getGuid();
			}
		});
	}

	@Override
	public Void exec(final CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		try {
			this.db.beginTransaction();

			final Destination dest = this.run(new DestinationFindByMountCmd(this.srcMountId), session);
			final List<MountPoint> allMounts = this.db.find(new MountPointFindByDestinationQuery(dest.getDestinationId()));
			final Collection<MountPoint> mounts = Collections2.filter(allMounts, new MountPredicate());

			// do we have any mounts left?
			if (mounts.isEmpty()) {
				throw new CommandException(Error.NO_MOUNTS_AVAILABLE, "No mounts are available as redistribute targets",
						this.srcMountId, dest, this.guids);
			}

			// reassign the guids to the provided list of mounts using round robin
			final Iterator<MountPoint> targets = Iterables.cycle(mounts).iterator();
			for (final long guid : this.guids) {
				final MountPoint tgt = targets.next();

				// update the fcu
				this.run(new FriendComputerUsageUpdateMountCmd(guid, dest.getDestinationGuid(), this.srcMountId, tgt
						.getMountPointId(), Increment.OVERRIDE), session);

				// notify the client that something has changed wherever they may be; also tell them to reconnect
				this.db.afterTransaction(new AfterTxRunnable(Priority.NORMAL) {

					public void run() {
						CpcHistoryLogger.info(session, "Redistributed guid {}", guid,
								ArchiveRedistributeToDestinationCmd.this.srcMountId, tgt);
						CoreBridge.getCentralService().getBackup().notifyBackupServerOfUpdate(guid, true);
						CoreBridge.getCentralService().runCommand(ServiceCommand.RECONNECT_AUTHORITY, guid, "Reassigned");
					}
				});
			}

			this.db.commit();
		} finally {
			this.db.endTransaction();
		}

		return null;
	}

	/**
	 * Filter mounts that may not receive redistributed guids.
	 */
	private class MountPredicate implements Predicate<MountPoint> {

		public boolean apply(MountPoint mount) {

			// obviously we cannot assign to our source
			if (mount.getMountPointId().intValue() == ArchiveRedistributeToDestinationCmd.this.srcMountId) {
				return false;
			}

			// if the mount isn't accepting balance data, then it's out
			if (!mount.getBalanceData()) {
				return false;
			}

			// if the mount isn't accepting new users, UNLESS we're overriding that setting
			if (!mount.getBalanceNewUsers() && !ArchiveRedistributeToDestinationCmd.this.overrideNewUsers) {
				return false;
			}

			return true;
		}
	}
}
