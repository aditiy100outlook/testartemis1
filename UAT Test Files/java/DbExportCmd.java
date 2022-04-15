package com.code42.db;

import com.backup42.common.perm.C42PermissionPro;
import com.backup42.server.manage.db.DbExportManager;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;

/**
 * Export the database to our local storage location and each store point.
 */
public class DbExportCmd extends AbstractCmd<Void> {

	@Override
	public Void exec(CoreSession session) throws CommandException {

		// Authorization
		this.auth.isAuthorized(session, C42PermissionPro.System.MANAGE_DATABASE);

		try {
			// History logging happens inside of DbExportManager
			DbExportManager.getInstance().doWork();

		} catch (Throwable t) {
			throw new CommandException("Unable to dump database", t);
		}

		return null;
	}

}
