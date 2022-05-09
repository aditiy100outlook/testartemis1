package com.code42.archiverecord;

import java.util.Date;
import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.DBCmd;

public class ArchiveRecordFindHistoryBySourceAndTargetCmd extends DBCmd<List<ArchiveRecord>> {

	private long sourceComputerId;
	private long targetComputerId;
	private Date startDate;
	private int maxDays;

	public ArchiveRecordFindHistoryBySourceAndTargetCmd(long sourceComputerId, long targetComputerId, Date startDate,
			int maxDays) {
		this.sourceComputerId = sourceComputerId;
		this.targetComputerId = targetComputerId;
		this.startDate = startDate;
		this.maxDays = maxDays;
	}

	@Override
	public List<ArchiveRecord> exec(CoreSession session) throws CommandException {

		this.runtime.run(new IsComputerManageableCmd(this.sourceComputerId, C42PermissionApp.Computer.READ), session);

		return this.db.find(new ArchiveRecordFindHistoryBySourceAndTargetQuery(this.sourceComputerId, this.targetComputerId,
				this.startDate, this.maxDays));
	}

}
