package com.code42.radius;

import java.util.ArrayList;
import java.util.List;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.user.RadiusServer;

public class RadiusServerDtoFindAllCmd extends DBCmd<List<RadiusServerDto>> {

	private static final Logger log = LoggerFactory.getLogger(RadiusServerDtoFindAllCmd.class);

	@Override
	public List<RadiusServerDto> exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		List<RadiusServer> radiusServers = this.db.find(new RadiusServerFindAllQuery());
		List<RadiusServerDto> dtos = new ArrayList<RadiusServerDto>();
		for (RadiusServer rs : radiusServers) {
			dtos.add(new RadiusServerDto(rs));
		}

		log.trace("RADIUS:: found {} radius servers", dtos.size());
		return dtos;
	}
}
