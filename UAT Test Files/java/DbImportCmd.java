package com.code42.db;

import java.io.File;

import com.backup42.common.SystemUpgradeUtils.AlreadyInProgressException;
import com.backup42.common.perm.C42PermissionPro;
import com.backup42.history.CpcHistoryLogger;
import com.backup42.server.manage.db.DbImportManager;
import com.backup42.server.manage.db.DbImportManager.InvalidImportFileException;
import com.backup42.server.manage.db.InvalidDumpFileException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;

/**
 * Import a db from a file.
 */
public class DbImportCmd extends AbstractCmd<Void> {

	public static enum Error {
		ALREADY_IN_PROGRESS, INVALID_VERSION, INVALID_FILE
	}

	private static final Logger log = Logger.getLogger(DbImportCmd.class);

	private final File file;

	public DbImportCmd(File file) {
		this.file = file;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.MANAGE_DATABASE);
		log.info("Importing DB, file={}", this.file);

		if (!this.file.exists()) {
			throw new CommandException("File does not exist: " + this.file.getAbsolutePath());
		}

		try {
			DbImportManager.importDb(this.file);
			CpcHistoryLogger.info(session, "Imported database: {}", this.file.getAbsolutePath());
		} catch (AlreadyInProgressException e) {
			throw new CommandException(Error.ALREADY_IN_PROGRESS, "An import job is already in progress");
		} catch (InvalidDumpFileException e) {
			throw new CommandException(Error.INVALID_VERSION,
					"This database export was created from a different version of PROe Server");
		} catch (InvalidImportFileException e) {
			throw new CommandException(Error.INVALID_FILE, "Invalid import file");
		}
		return null;
	}

}
