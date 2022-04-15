package com.code42.ssoauth;

import java.util.ArrayList;
import java.util.List;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

public class SsoAuthDtoFindAllCmd extends DBCmd<List<SsoAuthDto>> {

	@Override
	public List<SsoAuthDto> exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		List<SsoAuth> ssoAuths = this.db.find(new SsoAuthFindAllQuery());
		List<SsoAuthDto> dtos = new ArrayList<SsoAuthDto>();
		for (SsoAuth s : ssoAuths) {
			dtos.add(new SsoAuthDto(s));
		}
		return dtos;
	}
}
