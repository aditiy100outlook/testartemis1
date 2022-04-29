package com.code42.db;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.backup42.app.CpcFolders;
import com.backup42.app.cpc.CPCBackupProperty;
import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.IEnvironment;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.core.platform.IPlatformService;
import com.code42.io.FileUtility;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.server.mount.MountPoint;
import com.code42.server.mount.MountPointFindByIdQuery;
import com.code42.server.mount.MountPointFindByNodeGuidQuery;
import com.code42.utils.LangUtils;
import com.code42.utils.SystemProperties;
import com.google.inject.Inject;

public class DbExportFoldersFindCmd extends DBCmd<Collection<File>> {

	private static final Logger log = LoggerFactory.getLogger(DbExportFoldersFindCmd.class);

	private IEnvironment env;
	private IPlatformService platform;

	@Inject
	public void setEnv(IEnvironment env) {
		this.env = env;
	}

	@Inject
	public void setPlatform(IPlatformService platform) {
		this.platform = platform;
	}

	@Override
	public Collection<File> exec(CoreSession session) throws CommandException {

		// Authorization
		this.auth.isAuthorized(session, C42PermissionPro.System.MANAGE_DATABASE);

		// our first task is to identify all of the folders that should receive an export file
		final List<File> destinationFolders = new ArrayList<File>();

		// the first possibility is an optional standard location
		final String destinationName = CpcFolders.getOptionalDbDumpDestinationFolder();
		if (LangUtils.hasValue(destinationName)) {
			final File destinationFolder = new File(destinationName);
			if (destinationFolder.exists() && destinationFolder.canWrite()) {
				destinationFolders.add(destinationFolder);
			} else {
				log.warn("Unable to use configured database export destination of " + destinationFolder.getName());
			}
		}

		// the second possibility is a server-configured mount that should also receive db exports
		/*
		 * If the user has configured the use of all mount points then proceed along those lines. Otherwise, if the user
		 * didn't ask for no mount points then we must be dealing with a single value. Process accordingly.
		 */
		final List<MountPoint> mps = this.db.find(new MountPointFindByNodeGuidQuery(this.env.getMyNodeGuid()));
		for (MountPoint mp : mps) {

			File f = this.initMountPoint(mp.getMountPointId());
			if (f != null) {
				destinationFolders.add(f);
			}
		}

		return destinationFolders;
	}

	/*
	 * Helper method to validate and initialize a mount point as a target for a database dump. Returns a File object
	 * describing a target for a DB dump if validation + initialization is successful, null otherwise.
	 */
	private File initMountPoint(int mountPointId) throws CommandException {

		File rv = null;
		final MountPoint mp = this.db.find(new MountPointFindByIdQuery(mountPointId));
		if (mp != null) {

			if (this.platform.isMounted(mp)) {

				final String dbDumpFolder = SystemProperties
						.getRequired(CPCBackupProperty.DatabaseExport.MOUNT_DESTINATION_FOLDER);
				final File mountDestination = new File(mp.getAbsolutePath() + "/" + dbDumpFolder);

				// notice that we can ONLY make the last directory. do NOT create mount directories all over the system
				if (!mountDestination.exists()) {
					FileUtility.mkdir(mountDestination);
				}

				/* We only want to use a mount point if it's writable */
				if (mountDestination.canWrite()) {
					rv = mountDestination;
				} else {
					log.warn("Unable to use configured database export destination of '" + mountDestination.getName() + "'");
				}
			} else {
				log.error("Configured database export destination is not available. Mount " + mp.getName() + " is not mounted.");
			}
		}
		return rv;
	}
}
