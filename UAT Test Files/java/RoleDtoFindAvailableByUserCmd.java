package com.code42.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.impl.DBCmd;
import com.code42.user.Role;

public class RoleDtoFindAvailableByUserCmd extends DBCmd<List<RoleDto>> {

	@Override
	public List<RoleDto> exec(CoreSession session) throws CommandException {

		ArrayList<RoleDto> processedRoles = new ArrayList<RoleDto>();
		RoleFindAvailableByUserCmd findCmd = new RoleFindAvailableByUserCmd();

		List<Role> roles = this.runtime.run(findCmd, session);
		List<Integer> roleIds = new ArrayList<Integer>();
		for (Role role : roles) {
			roleIds.add(role.getRoleId());
		}

		Map<Integer, Integer> counts = null;
		if (roleIds.size() > 0) {
			try {
				this.db.openSession();
				counts = this.db.find(new RoleFindUserCountByRoleIdQuery(roleIds));

			} catch (DBServiceException e) {
				throw new CommandException("Error finding visible roles by user; user=" + session.getUser(), e);
			} finally {
				this.db.closeSession();
			}
		}

		for (Role role : roles) {
			int count = counts != null && counts.containsKey(role.getRoleId()) ? counts.get(role.getRoleId()) : 0;
			processedRoles.add(new RoleDto(role, count));
		}

		return processedRoles;
	}
}
