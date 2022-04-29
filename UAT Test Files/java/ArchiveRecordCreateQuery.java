package com.code42.archiverecord;

import org.hibernate.Session;

import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.CreateQuery;

public class ArchiveRecordCreateQuery extends CreateQuery<ArchiveRecord> {

	private final ArchiveRecord archiveRecord;

	public ArchiveRecordCreateQuery(ArchiveRecord archiveRecord) {
		this.archiveRecord = archiveRecord;
	}

	@Override
	public ArchiveRecord query(Session session) throws DBServiceException {
		session.save(this.archiveRecord);
		return this.archiveRecord;
	}
}