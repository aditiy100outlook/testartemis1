package com.code42.ssoauth;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

public class SsoAuthDtoFindByIdCmd extends DBCmd<SsoAuthDto> {

	private int ssoAuthId;

	public SsoAuthDtoFindByIdCmd(int ssoAuthId) {
		this.ssoAuthId = ssoAuthId;
	}

	@Override
	public SsoAuthDto exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		SsoAuth ssoAuth = this.db.find(new SsoAuthFindByIdQuery(this.ssoAuthId));
		if (ssoAuth == null) {
			return null;
		}
		SsoAuthDto dto = new SsoAuthDto(ssoAuth);
		return dto;
	}
}
