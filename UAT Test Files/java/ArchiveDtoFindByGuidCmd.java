package com.code42.archive;

import java.util.List;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.archive.ArchiveDtoQueryBase.ArchiveDtoQueryBuilder;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.utils.Pair;

public class ArchiveDtoFindByGuidCmd extends DBCmd<Pair<List<ArchiveDto>, Integer>> {

	private static final Logger log = Logger.getLogger(ArchiveDtoFindByGuidCmd.class);

	private final long guid;
	private final int destinationId;

	public ArchiveDtoFindByGuidCmd(long guid, int destinationId) {
		super();
		this.guid = guid;
		this.destinationId = destinationId;
	}

	@Override
	public Pair<List<ArchiveDto>, Integer> exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		final ArchiveDtoQueryBuilder b = new ArchiveDtoQueryBase.ArchiveDtoQueryBuilder().guid(this.guid).destination(
				this.destinationId);

		final List<ArchiveDto> dtos = this.db.find(b.buildSelect());
		if (dtos.size() > 1) {
			log.warn("multiple dtos found for guid={}, destinationId={}", this.guid, this.destinationId, dtos);
		}

		Integer count = this.db.find(b.buildCount());
		return new Pair(dtos, count);
	}
}
