package com.code42.archive;

import java.util.List;

import com.code42.archive.ArchiveDtoQueryBase.ArchiveDtoQueryBuilder;
import com.code42.archive.ArchiveDtoQueryBase.OrderBy;
import com.code42.archive.ArchiveDtoQueryBase.OrderDir;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp.User;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.utils.Pair;

public class ArchiveDtoFindByUserCmd extends DBCmd<Pair<List<ArchiveDto>, Integer>> {

	private final int userId;
	private final Integer limit;
	private final Integer offset;
	private final boolean exportAll;
	private final OrderBy orderBy;
	private final OrderDir orderDir;

	public ArchiveDtoFindByUserCmd(int userId, Integer limit, Integer offset) {
		this(userId, limit, offset, false, null, null);
	}

	public ArchiveDtoFindByUserCmd(int userId, Integer limit, Integer offset, boolean exportAll, OrderBy orderBy,
			OrderDir orderDir) {
		super();
		this.userId = userId;
		this.limit = limit;
		this.offset = offset;
		this.exportAll = exportAll;
		this.orderBy = orderBy;
		this.orderDir = orderDir;
	}

	@Override
	public Pair<List<ArchiveDto>, Integer> exec(CoreSession session) throws CommandException {
		this.run(new IsUserManageableCmd(this.userId, User.UPDATE), session);

		final ArchiveDtoQueryBuilder b = new ArchiveDtoQueryBase.ArchiveDtoQueryBuilder().user(this.userId).limit(
				this.limit).offset(this.offset).exportAll(this.exportAll);
		if (this.orderBy != null && this.orderDir != null) {
			b.orderBy(this.orderBy);
			b.orderDir(this.orderDir);
		}
		List<ArchiveDto> list = this.db.find(b.buildSelect());
		Integer count = this.db.find(b.buildCount());
		return new Pair(list, count);
	}
}
