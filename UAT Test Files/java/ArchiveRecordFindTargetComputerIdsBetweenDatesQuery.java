package com.code42.archiverecord;

import java.util.Date;
import java.util.List;

import org.hibernate.Query;
import org.hibernate.Session;

import com.code42.core.annotation.CoreNamedQuery;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;

@CoreNamedQuery(name = "findTargetComputerIdsBetweenDates", query = "" //
		+ "select distinct ar.targetComputerId from ArchiveRecord ar "
		+ "where ar.sourceComputerId = :sourceComputerId and "
		+ "ar.creationDate >= :startDate and ar.creationDate < :endDate")
public class ArchiveRecordFindTargetComputerIdsBetweenDatesQuery extends FindQuery<List<Long>> {

	private final long sourceComputerId;
	private final Date startDate;
	private final Date endDate;

	public ArchiveRecordFindTargetComputerIdsBetweenDatesQuery(long sourceComputerId, Date startDate, Date endDate) {
		this.sourceComputerId = sourceComputerId;
		this.startDate = startDate;
		this.endDate = endDate;
	}

	@Override
	public List<Long> query(Session session) throws DBServiceException {
		Query query = this.getNamedQuery(session);
		query.setLong("sourceComputerId", this.sourceComputerId);
		query.setDate("startDate", this.startDate);
		query.setDate("endDate", this.endDate);
		return query.list();
	}
}