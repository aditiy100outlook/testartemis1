package com.code42.db;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.executor.jsr166.Arrays;

/**
 * Finds all the export file names
 */
public class DbExportFilesFindCmd extends DBCmd<Collection<File>> {

	@Override
	public Collection<File> exec(CoreSession session) throws CommandException {

		// Authorization
		this.auth.isAuthorized(session, C42PermissionPro.System.MANAGE_DATABASE);

		Collection<File> allFiles = new ArrayList<File>(50);

		Collection<File> folders = this.run(new DbExportFoldersFindCmd(), session);
		for (File folder : folders) {
			File[] files = folder.listFiles(new FileFilter() {

				public boolean accept(File file) {
					return file.isFile() && file.getName().endsWith(".sql.gz");
				}
			});

			allFiles.addAll(Arrays.asList(files));
		}

		return allFiles;
	}
}
