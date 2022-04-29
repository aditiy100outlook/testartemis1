package com.code42.user;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

/**
 * Returns the current count of
 * 
 * Deprecated: use HierarchyService
 * 
 * @author bmcguire
 */
@Deprecated
public class UserCountByOrgIdCmd extends DBCmd<Integer> {

	private int orgId;
	private final Boolean active;
	private final boolean excludeInvited;

	public UserCountByOrgIdCmd(int orgId) {
		this.orgId = orgId;
		this.active = null;
		this.excludeInvited = false;
	}

	public UserCountByOrgIdCmd(int orgId, Boolean active) {
		this.orgId = orgId;
		this.active = active;
		this.excludeInvited = false;
	}

	public UserCountByOrgIdCmd(int orgId, Boolean active, boolean excludeInvited) {
		this.orgId = orgId;
		this.active = active;
		this.excludeInvited = excludeInvited;
	}

	@Override
	public Integer exec(CoreSession session) throws CommandException {

		return this.db.find(new UserCountByOrgIdQuery(this.orgId, this.active, this.excludeInvited));
	}
}
