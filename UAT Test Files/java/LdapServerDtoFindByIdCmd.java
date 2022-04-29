package com.code42.ldap;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

public class LdapServerDtoFindByIdCmd extends DBCmd<LdapServerDto> {

	private int ldapServerId;

	public LdapServerDtoFindByIdCmd(int ldapServerId) {
		this.ldapServerId = ldapServerId;
	}

	@Override
	public LdapServerDto exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		LdapServer ldapServer = this.db.find(new LdapServerFindByIdQuery(this.ldapServerId));
		LdapServerDto dto = new LdapServerDto(ldapServer);
		return dto;
	}
}
