package com.code42.ldap;

import java.util.ArrayList;
import java.util.List;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

public class LdapServerDtoFindAllCmd extends DBCmd<List<LdapServerDto>> {

	@Override
	public List<LdapServerDto> exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		List<LdapServer> ldapServers = this.db.find(new LdapServerFindAllQuery());
		List<LdapServerDto> dtos = new ArrayList<LdapServerDto>();
		for (LdapServer ls : ldapServers) {
			dtos.add(new LdapServerDto(ls));
		}
		return dtos;
	}
}
