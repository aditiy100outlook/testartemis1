package com.code42.org;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;

public class OrgFindDuplicateCmd extends DBCmd<BackupOrg> {

	private BackupOrg org;

	public OrgFindDuplicateCmd(BackupOrg org) {
		this.org = org;
	}

	@Override
	public BackupOrg exec(CoreSession session) throws CommandException {
		FindQuery query = new OrgFindByUidQuery(this.org.getOrgUid(), this.org.getMasterGuid(),
				null/* active */, this.org.getOrgId());
		return (BackupOrg) this.db.find(query);
	}
}