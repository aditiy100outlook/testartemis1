package com.code42.server.mount;

import java.util.List;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

public class MountPointDtoFindByNodeCmd extends DBCmd<List<MountPointDto>> {

	private final int nodeId;
	private final Integer limit;
	private final Integer offset;

	public MountPointDtoFindByNodeCmd(int nodeId) {
		this(nodeId, null, null);
	}

	public MountPointDtoFindByNodeCmd(int nodeId, Integer limit, Integer offset) {
		super();
		this.nodeId = nodeId;
		this.limit = limit;
		this.offset = offset;
	}

	@Override
	public List<MountPointDto> exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		final MountPointDtoFindByCriteriaQuery.Builder b = new MountPointDtoFindByCriteriaQuery.Builder();
		if (!session.isSystem()) {
			b.excludeProvider(true);
		}
		b.node(this.nodeId);
		b.limit(this.limit).offset(this.offset);
		final List<MountPointDto> dtos = this.db.find(b.build());

		this.run(new MountPointDtoLoadCmd(dtos), session);
		return dtos;
	}
}
