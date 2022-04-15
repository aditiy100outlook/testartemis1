package com.code42.archive.maintenance;

import com.code42.backup.config.BackupConfig;
import com.code42.backup.manifest.SoftDeleteCode;
import com.code42.balance.engine.util.MountContentManager;
import com.code42.computer.FriendComputerUsageDeleteCmd;
import com.code42.computer.FriendComputerUsageFindByIdCmd;
import com.code42.core.CommandException;
import com.code42.core.archive.IArchiveService;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.exception.InvalidParamException;
import com.code42.hibernate.aftertx.IAfterTxRunnable;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.OrgSso;
import com.code42.org.OrgSsoFindByGuidCmd;
import com.code42.social.FriendComputerUsage;
import com.google.inject.Inject;

/**
 * Delete an archive and the corresponding t_friend_computer_usage row.
 * 
 * This deletion is only for cold-storage archives.
 * 
 * @author mharper
 */
public class ArchiveDeleteColdStorageCmd extends DBCmd<Void> {

	private final static Logger log = LoggerFactory.getLogger(ArchiveDeleteColdStorageCmd.class);

	private static final String PREFIX = "ADCSC:: ";

	private final long fcuId;

	@Inject
	private IArchiveService archiveService;

	public ArchiveDeleteColdStorageCmd(long fcuId) {
		super();
		this.fcuId = fcuId;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		log.info(PREFIX + "Purging an archive: friend_computer_usage_id={}", this.fcuId);

		FriendComputerUsage fcu = this.getFcu();
		this.authorize(session, fcu);
		this.validateFCU(fcu);
		this.purgeArchive(fcu);
		return null;
	}

	/**
	 * Deletes the FCU row from the database and sets an after transaction command to delete the archive from disk.
	 * 
	 * @param fcu the FCU to delete
	 * @throws CommandException
	 */
	private void purgeArchive(FriendComputerUsage fcu) throws CommandException {
		this.run(new FriendComputerUsageDeleteCmd(fcu), this.auth.getAdminSession());
		this.db.afterTransaction(new DeleteColdStorageArchive(fcu));
	}

	/**
	 * Validates that the FCU is in the appropriate state (not currently being used).
	 * 
	 * @param fcu the FCU to be deleted
	 * @throws CommandException
	 */
	private void validateFCU(FriendComputerUsage fcu) throws CommandException {
		if (fcu.isUsing()) {
			throw new CommandException(PREFIX + "Cannot delete archives from an active FCU; friend_computer_usage_id={}",
					this.fcuId);
		}
	}

	/**
	 * Ensures the user can manage the computer and org
	 * 
	 * @param session the user's session
	 * @param fcu the FCU to be deleted
	 * @throws UnauthorizedException
	 * @throws CommandException
	 */
	private void authorize(CoreSession session, FriendComputerUsage fcu) throws UnauthorizedException, CommandException {

		// Make sure user is at least an org admin
		OrgSso orgSso = this.run(new OrgSsoFindByGuidCmd(fcu.getSourceComputerGuid()), session);
		this.run(new IsOrgManageableCmd(orgSso, C42PermissionApp.Org.UPDATE_BASIC), session);

		// Even an ordinary user will pass this test for their own computers
		this.run(new IsComputerManageableCmd(fcu.getSourceComputerId(), C42PermissionApp.Computer.ALL), session);
	}

	/**
	 * Returns the FCU to be deleted.
	 * 
	 * @return the FCU to be deleted
	 * @throws CommandException
	 */
	private FriendComputerUsage getFcu() throws CommandException {
		// Using system session because org admin's don't have permission to find FCU
		FriendComputerUsage fcu = this.run(new FriendComputerUsageFindByIdCmd(this.fcuId), this.auth.getAdminSession());
		if (fcu == null) {
			log.warn("ArchiveDeleteColdStorageCmd: FCU not found, already deleted?; friend_computer_usage_id={}", this.fcuId);
		}
		return fcu;
	}

	/**
	 * Deletes an archive from disk after the FCU has been deleted from the database.
	 */
	private class DeleteColdStorageArchive implements IAfterTxRunnable {

		private FriendComputerUsage fcu;

		public DeleteColdStorageArchive(FriendComputerUsage fcu) {
			this.fcu = fcu;
		}

		public Priority getPriority() {
			return Priority.NORMAL;
		}

		public void run() {
			Integer mountPointId = this.fcu.getMountPointId();
			MountContentManager mcmgr = MountContentManager.getInstance(mountPointId);
			BackupConfig backupConfig = ArchiveDeleteColdStorageCmd.this.archiveService.getConfig();
			boolean softDeleteOfFiles = backupConfig.softDeleteOfFiles.getValue();
			long sourceComputerGuid = this.fcu.getSourceComputerGuid();
			try {
				mcmgr.deleteArchive(sourceComputerGuid, softDeleteOfFiles, SoftDeleteCode.DELETED, false);
			} catch (InvalidParamException e) {
				log.error("Failed to delete an archive", e.getMessage());
			}

		}

	}

}
