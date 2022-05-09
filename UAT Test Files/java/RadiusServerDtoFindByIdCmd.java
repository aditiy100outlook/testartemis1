package com.code42.radius;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.user.RadiusServer;

public class RadiusServerDtoFindByIdCmd extends DBCmd<RadiusServerDto> {

	private int radiusServerId;

	public RadiusServerDtoFindByIdCmd(int ldapServerId) {
		this.radiusServerId = ldapServerId;
	}

	@Override
	public RadiusServerDto exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		RadiusServer ldapServer = this.db.find(new RadiusServerFindByIdQuery(this.radiusServerId));
		RadiusServerDto dto = new RadiusServerDto(ldapServer);
		return dto;
	}
}
