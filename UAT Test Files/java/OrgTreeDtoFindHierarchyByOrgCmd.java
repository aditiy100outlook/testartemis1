package com.code42.org;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

/**
 * Returns of list of OrgTreeDto parentage instances
 */
public class OrgTreeDtoFindHierarchyByOrgCmd extends DBCmd<Collection<OrgTreeDto>> {

	int orgId;

	public OrgTreeDtoFindHierarchyByOrgCmd(int orgId) {
		this.orgId = orgId;
	}

	@Override
	public Collection<OrgTreeDto> exec(CoreSession session) throws CommandException {
		List<OrgTreeDto> dtos = new ArrayList<OrgTreeDto>();

		Integer orgId = this.orgId;
		do {
			OrgSso sso = this.runtime.run(new OrgSsoFindByOrgIdCmd(orgId), session);
			if (sso == null) {
				orgId = null;
			} else {
				dtos.add(new OrgTreeDto(sso));
				orgId = sso.getParentOrgId();
			}
		} while (orgId != null);

		return dtos;
	}
}
