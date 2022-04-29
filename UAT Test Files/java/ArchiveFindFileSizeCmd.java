package com.code42.archive;

import com.code42.archive.IArchiveAuthHandler.ArchivePermission;
import com.code42.backup.BackupServer;
import com.code42.backup.manifest.FileContents;
import com.code42.backup.restore.BackupQueryData;
import com.code42.core.CommandException;
import com.code42.core.archive.IArchiveService;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.io.path.FileId;
import com.google.inject.Inject;

/**
 * Lookup the source length of a file in an archive.
 * 
 * Returns null if the file could not be found.
 * 
 * @author mscorcio
 */
public class ArchiveFindFileSizeCmd extends AbstractCmd<Long> {

	@Inject
	private IArchiveService archiveService;

	private final long archiveGuid;
	private final FileId fileId;

	public ArchiveFindFileSizeCmd(long archiveGuid, FileId fileId) {
		this.archiveGuid = archiveGuid;
		this.fileId = fileId;
	}

	@Override
	public Long exec(CoreSession session) throws CommandException {
		this.run(new IsArchiveAuthorizedCmd(this.archiveGuid, ArchivePermission.READ), session);

		try {
			final BackupServer backupServer = this.archiveService.createBackupServer(this.archiveGuid);
			if (!backupServer.isBackupReady()) {
				throw new CommandException(ArchiveError.ARCHIVE_NOT_READY, "Archive not ready");
			}
			BackupQueryData bqd = new BackupQueryData(backupServer.getSourceId(), backupServer.getTargetId(), this.fileId,
					BackupQueryData.MOST_RECENT_VERSION_TIMESTAMP, true);
			FileContents fileContents = backupServer.getFileContents(bqd);
			// XXX A FileContents object is returned whether the file exists or not
			return (fileContents.getNumDirectories() > 0 || fileContents.getNumFiles() > 0) ? fileContents.getSize() : null;
		} catch (Exception e) {
			throw (e instanceof CommandException ? (CommandException) e : new CommandException(e));
		}
	}

}
