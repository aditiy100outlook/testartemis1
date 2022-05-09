package com.code42.org;

import java.util.Collection;
import java.util.Collections;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.user.UserSso;
import com.code42.user.UserSsoFindByUserIdCmd;

/**
 * Returns of list of OrgTreeDto parentage instances
 */
public class OrgTreeDtoFindHierarchyByUserCmd extends DBCmd<Collection<OrgTreeDto>> {

	int userId;

	public OrgTreeDtoFindHierarchyByUserCmd(int userId) {
		this.userId = userId;
	}

	@Override
	public Collection<OrgTreeDto> exec(CoreSession session) throws CommandException {
		UserSso userSso = this.runtime.run(new UserSsoFindByUserIdCmd(this.userId), session);
		if (userSso == null) {
			return Collections.emptyList();
		}
		return this.runtime.run(new OrgTreeDtoFindHierarchyByOrgCmd(userSso.getOrgId()), session);
	}
}
