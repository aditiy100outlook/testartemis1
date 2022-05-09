package com.code42.archive;

import java.util.List;

import com.code42.archive.FileInfoDto.FileType;
import com.code42.archive.IArchiveAuthHandler.ArchivePermission;
import com.code42.backup.BackupCiphers;
import com.code42.backup.BackupServer;
import com.code42.backup.DataKey;
import com.code42.backup.manifest.FileVersion;
import com.code42.backup.manifest.SecureFileVersion;
import com.code42.backup.manifest.SecureFileVersionSet;
import com.code42.backup.restore.BackupQueryData;
import com.code42.core.CommandException;
import com.code42.core.archive.IArchiveService;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.crypto.CryptoException;
import com.code42.crypto.ICipherPair;
import com.code42.crypto.MD5Value;
import com.code42.io.path.FileId;
import com.code42.io.path.Path;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

/**
 * Lookup info about a file in an archive.
 * 
 * Returns null if the file could not be found.
 * 
 * @author mscorcio
 */
public class ArchiveFindFileInfoDtoCmd extends AbstractCmd<FileInfoDto> {

	@Inject
	private IArchiveService archiveService;

	private final long archiveGuid;
	private final FileId fileId;
	private final DataKey dataKey;
	private final boolean includeChildren;

	public ArchiveFindFileInfoDtoCmd(long archiveGuid, FileId fileId, DataKey dataKey, boolean includeChildren) {
		this.archiveGuid = archiveGuid;
		this.fileId = fileId;
		this.dataKey = dataKey;
		this.includeChildren = includeChildren;
	}

	@Override
	public FileInfoDto exec(CoreSession session) throws CommandException {
		this.run(new IsArchiveAuthorizedCmd(this.archiveGuid, ArchivePermission.READ), session);

		try {
			BackupCiphers ciphers = this.dataKey.getBackupCiphers();
			// XXX: only Blowfish 128 is supported for file paths at this time
			ICipherPair cipherPair = ciphers.getDefaultCipherPair128();
			final BackupServer backupServer = this.archiveService.createBackupServer(this.archiveGuid);
			if (!backupServer.isBackupReady()) {
				throw new CommandException(ArchiveError.ARCHIVE_NOT_READY, "Archive not ready");
			}

			ImmutableList.Builder children = new ImmutableList.Builder();
			if (this.includeChildren) {
				SecureFileVersionSet fileSet = backupServer.getChildrenFileVersions(new BackupQueryData(backupServer
						.getSourceId(), backupServer.getTargetId(), this.fileId, BackupQueryData.MOST_RECENT_VERSION_TIMESTAMP,
						true));
				for (SecureFileVersion childSfv : fileSet.getFileVersions()) {
					children.add(this.buildFileInfoDto(childSfv, cipherPair, null));
				}
			}

			if (this.fileId.equals(FileId.ROOT_ID)) {
				return new FileInfoDto("/", "/", FileType.FOLDER, 0, MD5Value.newMD5Value(MD5Value.NULL), false,
						this.includeChildren ? children.build() : null);
			} else {
				final SecureFileVersion sfv = backupServer.getLatestFileVersion(this.fileId, this.archiveGuid);
				if (sfv == null) {
					return null;
				}
				return this.buildFileInfoDto(sfv, cipherPair, (sfv.isDirectory() && this.includeChildren) ? children.build()
						: null);
			}
		} catch (Exception e) {
			throw (e instanceof CommandException ? (CommandException) e : new CommandException(e));
		}
	}

	private FileInfoDto buildFileInfoDto(SecureFileVersion sfv, ICipherPair cipherPair, List<FileInfoDto> children)
			throws CryptoException {
		FileVersion fv = sfv.toFileVersion(cipherPair);
		Path path = fv.getPath();
		FileType type = fv.isDirectory() ? FileType.FOLDER : FileType.FILE;

		return new FileInfoDto(path.getName(), path.getSafePath(), type, fv.getTimestamp(), fv.getVersion()
				.getSourceChecksum(), fv.isDeleted(), children);
	}
}
