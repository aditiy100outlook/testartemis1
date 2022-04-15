package com.code42.server.mount;

import java.util.List;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

public class MountPointDtoFindByIdCmd extends DBCmd<MountPointDto> {

	private final int mountId;

	public MountPointDtoFindByIdCmd(int mountId) {
		super();
		this.mountId = mountId;
	}

	@Override
	public MountPointDto exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		final MountPointDtoFindByCriteriaQuery.Builder b = new MountPointDtoFindByCriteriaQuery.Builder();
		if (!session.isSystem()) {
			b.excludeProvider(true);
		}
		b.myCluster(); // always limit results to this cluster
		b.mount(this.mountId);
		if (!session.isSystem()) {
			b.excludeProvider(true);
		}
		final List<MountPointDto> dtos = this.db.find(b.build());

		final MountPointDto dto = dtos.isEmpty() ? null : dtos.get(0);
		if (dto != null) {
			this.run(new MountPointDtoLoadCmd(dto), session);
		}

		return dto;
	}
}
