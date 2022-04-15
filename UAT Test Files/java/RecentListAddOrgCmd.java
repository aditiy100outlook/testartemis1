package com.code42.recent;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.org.BackupOrg;

public class RecentListAddOrgCmd extends AbstractCmd<Void> {

	private int orgId;
	private String orgName;
	private String regKey;

	public RecentListAddOrgCmd(BackupOrg org) {
		this.orgId = org.getOrgId();
		this.orgName = org.getOrgName();
		this.regKey = org.getRegistrationKey();
	}

	public RecentListAddOrgCmd(int orgId, String orgName, String regKey) {
		this.orgId = orgId;
		this.orgName = orgName;
		this.regKey = regKey;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		RecentOrg ro = new RecentOrg(this.orgId, this.orgName, this.regKey);
		this.runtime.run(new RecentListAddItemCmd(ro), session);
		return null;
	}

}
