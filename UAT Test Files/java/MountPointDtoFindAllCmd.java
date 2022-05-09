package com.code42.server.mount;

import java.util.List;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.IEnvironment;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.google.inject.Inject;

/**
 * Find the mount point dtos owned by "my" cluster.
 * 
 * Note that we are not really returning all dtos.
 */
public class MountPointDtoFindAllCmd extends DBCmd<List<MountPointDto>> {

	private final Integer limit;
	private final Integer offset;

	@Inject
	private IEnvironment environment;

	public MountPointDtoFindAllCmd() {
		this(null, null);
	}

	public MountPointDtoFindAllCmd(Integer limit, Integer offset) {
		super();
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
		b.cluster(this.environment.getMyClusterId());
		b.limit(this.limit).offset(this.offset);
		final List<MountPointDto> dtos = this.db.find(b.build());

		this.run(new MountPointDtoLoadCmd(dtos), session);
		return dtos;
	}

}
