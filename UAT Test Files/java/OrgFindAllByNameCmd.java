package com.code42.org;

import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

/**
 * Provides an authorization wrapper around the query. The Query class is currently public so that it can be created
 * without the command (authorization check) if necessary. We may rethink this openness.
 */
public class OrgFindAllByNameCmd extends DBCmd<List<BackupOrg>> {

	private final String orgName;

	public OrgFindAllByNameCmd(String orgName) {
		this.orgName = orgName;
	}

	@Override
	public List<BackupOrg> exec(final CoreSession session) throws CommandException {

		/*
		 * Try to find the org. It's possible the requested org doesn't even exist, and if that's the case, there's
		 * no point in doing any auth checking.
		 */
		List<BackupOrg> rv = this.db.find(new OrgFindByNameQuery(this.orgName));

		/* This shouldn't ever give us a null value back... */
		assert (rv != null);

		/* Return only those orgs you're allowed to see */
		return ImmutableList.copyOf(Collections2.filter(rv, new Predicate<BackupOrg>() {

			public boolean apply(BackupOrg arg) {

				try {
					OrgFindAllByNameCmd.this.auth.isAuthorized(session, C42PermissionApp.Org.READ, arg.getOrgId());
					return true;
				}
				catch (UnauthorizedException ue) {
					return false;
				}
			}
		}));
	}
}