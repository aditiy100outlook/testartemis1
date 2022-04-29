package com.code42.archive;

import java.io.EOFException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;

import com.code42.archive.IArchiveAuthHandler.ArchivePermission;
import com.code42.backup.BackupServer;
import com.code42.backup.BackupServer.BackupServerNotReadyException;
import com.code42.backup.DataKey;
import com.code42.backup.handler.BlockRestoreTool;
import com.code42.backup.handler.RestoreTool;
import com.code42.backup.handler.RestoreTool.RestoreBlockHandler;
import com.code42.backup.manifest.FileHistory;
import com.code42.backup.manifest.VersionData;
import com.code42.backup.save.BackupData;
import com.code42.core.CommandException;
import com.code42.core.archive.IArchiveService;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.crypto.MD5Value;
import com.code42.io.path.FileId;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.transport.TransportUtil;
import com.google.inject.Inject;

/**
 * Stream a file out of an archive.
 * 
 * @author mscorcio
 */
public class ArchiveStreamFileCmd extends AbstractCmd<Void> {

	private static Logger log = LoggerFactory.getLogger(ArchiveStreamFileCmd.class);

	@Inject
	private IArchiveService archiveService;

	private final OutputStream output;
	private final long archiveGuid;
	private final FileId fileId;
	private final DataKey dataKey;

	public ArchiveStreamFileCmd(OutputStream output, long archiveGuid, FileId fileId, DataKey dataKey) {
		this.output = output;
		this.archiveGuid = archiveGuid;
		this.fileId = fileId;
		this.dataKey = dataKey;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		this.run(new IsArchiveAuthorizedCmd(this.archiveGuid, ArchivePermission.READ), session);

		// TODO: check licensing before allowing download

		RestoreTool restoreTool = new RestoreTool("Restore-" + this.archiveGuid, new BlockRestoreTool(), this.dataKey
				.getBackupCiphers());

		RestoreBlockHandler restoreBlockHandler = new RestoreBlockHandler(true, true);

		try {
			final BackupServer backupServer = this.archiveService.createBackupServer(this.archiveGuid);
			List<VersionData> versionDatas = backupServer.getAllVersionDatas(this.fileId);
			VersionData versionData = TransportUtil.getLatestNonDeletedVersionData(versionDatas);

			if (versionData == null) {
				throw new CommandException("Could not find file; guid={}, fileId={}", this.archiveGuid, this.fileId);
			}

			if (!versionData.isFile()) {
				throw new CommandException("Only download of files is supported; guid={}, fileId={}", this.archiveGuid,
						this.fileId);
			}

			long[] blockList = FileHistory.getBlockList(versionDatas, versionData.getTimestamp());
			if (blockList == null) {
				throw new CommandException("Could not find block numbers; guid={}, fileId={}, versionData={}",
						this.archiveGuid, this.fileId, versionData);
			}

			for (long blockNumber : blockList) {
				BackupData data = backupServer.getBackupData(blockNumber);
				ByteBuffer restoredData = restoreTool.restoreBackupData(data, restoreBlockHandler, true);

				try {
					assert restoredData.hasArray();
					this.output.write(restoredData.array(), restoredData.arrayOffset(), restoredData.limit());
				} catch (EOFException e) {
					// All this means is that the receiver went away
					log.info("Client is gone; broken pipe; fileId=" + this.fileId + "; error=" + e.toString());
					return null;
				}
			}

			MD5Value restoreChecksum = restoreTool.getFileMd5();
			MD5Value sourceChecksum = versionData.getSourceChecksum();
			if (!restoreChecksum.equals(sourceChecksum)) {
				throw new CommandException("File checksum failed; guid={}, fileId={}, expected={}, actual={}",
						this.archiveGuid, this.fileId, sourceChecksum, restoreChecksum);
			}
		} catch (BackupServerNotReadyException e) {
			throw new CommandException(ArchiveError.ARCHIVE_NOT_READY, "Archive not ready");
		} catch (Exception e) {
			throw (e instanceof CommandException ? (CommandException) e : new CommandException(e));
		}
		return null;
	}
}
