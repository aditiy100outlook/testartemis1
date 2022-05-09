package com.code42.org;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

/**
 * Provides an authorization wrapper around the query. The Query class is currently public so that it can be created
 * without the command (authorization check) if necessary. We may rethink this openness.
 */
public class OrgFindByIdCmd extends DBCmd<BackupOrg> {

	private final int orgId;

	public OrgFindByIdCmd(int orgId) {
		this.orgId = orgId;
	}

	@Override
	public BackupOrg exec(CoreSession session) throws CommandException {

		/*
		 * Try to find the org. It's possible the requested org doesn't even exist, and if that's the case, there's
		 * no point in doing any auth checking.
		 */
		BackupOrg rv = this.db.find(new OrgFindByIdQuery(this.orgId));
		if (rv == null) {
			return null;
		}

		/*
		 * If we're still here we found something to return... now let's make sure the requester is permitted
		 * to view those results.
		 */
		this.auth.isAuthorized(session, C42PermissionApp.Org.READ, this.orgId);

		/* If we made it this far we're safe to return everything */
		return rv;
	}

}