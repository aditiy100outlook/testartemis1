package com.code42.db;

import java.util.Date;
import java.util.List;

import org.hibernate.Session;

import com.backup42.common.perm.C42PermissionPro;
import com.backup42.executor.ExecutorSingleton;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.db.impl.UpdateQuery;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.sql.SQLQuery;
import com.code42.sql.SQLUtils;
import com.code42.utils.LangUtils;
import com.code42.utils.Pair;
import com.code42.utils.Stopwatch;
import com.code42.utils.SystemProperties;

/**
 * Truncates the tables in our database that continue to grow over time.
 */
@ExecutorSingleton
public class DbTruncateCmd extends DBCmd<Boolean> {

	private static final Logger log = LoggerFactory.getLogger(DbTruncateCmd.class);

	private static final Object[] monitor = new Object[] {};

	// Note that this is static because we cannot
	private static volatile Stopwatch stopwatch;

	public static boolean stop = false;

	/**
	 * This property only applies to a couple of tables that continue growing over time.
	 */
	static final String DB_TRUNC_KEEP_DAYS = "c42.dbTrunc.keep.days";
	static final String DB_TRUNC_BATCH_SIZE = "c42.dbTrunc.batch.size";
	static final String DB_TRUNC_BATCH_PAUSE_MS = "c42.dbTrunc.batch.pause.ms";
	static final int DEFAULT_KEEP_DAYS = 90;
	static final int DEFAULT_BATCH_SIZE = 100;
	static final int DEFAULT_BATCH_PAUSE_MS = 3000;

	public static void stop() {
		stop = true;
	}

	@Override
	public Boolean exec(CoreSession session) throws CommandException {
		if (!this.env.isPrimary()) {
			return false;
		}

		// We cannot have an umbrella transaction when this is called.
		this.db.ensureNoTransaction();

		// Authorization
		this.auth.isAuthorized(session, C42PermissionPro.System.MANAGE_DATABASE);

		int keepDays = SystemProperties.getOptionalInt(DB_TRUNC_KEEP_DAYS, DEFAULT_KEEP_DAYS);
		int batchSize = SystemProperties.getOptionalInt(DB_TRUNC_BATCH_SIZE, DEFAULT_BATCH_SIZE);
		long pauseMs = SystemProperties.getOptionalLong(DB_TRUNC_BATCH_PAUSE_MS, DEFAULT_BATCH_PAUSE_MS);

		if (batchSize > 1000) {
			log.warn("batchSize is more than 1000.  Exiting prematurely.  batchSize:{}", batchSize);
			return false;
		}

		synchronized (monitor) {
			if (stopwatch == null) {
				stopwatch = new Stopwatch();
			} else {
				// We cannot run because a version of this job already running
				log.info("DbTruncateCmd is already running.  Job has been running for {}", stopwatch);
				return false;
			}
		}

		log.info("STARTED: keepDays:{}, batchSize:{}, pauseMs:{}", keepDays, batchSize, pauseMs);

		// Make sure this is off
		stop = false;

		long totalArchiveRecordsDeleted = 0;
		long totalUserHistoryDeleted = 0;

		try {
			this.db.openSession();

			//
			// Delete old archive record rows
			//
			if (!stop) {
				// Create a "job" object with one method overridden.
				TruncateJobState job = new TruncateJobState() {

					@Override
					public UpdateQuery<Integer> getUpdate(int keepDaysBack, long maxId) {
						return new DbTruncateArchiveRecordQuery(keepDaysBack, maxId);
					}

					@Override
					public FindQuery<Pair<Long, Date>> getQuery() {
						return new FindMinQuery("t_archive_record", "archive_record_id", "creation_date");
					}
				};

				log.debug("Before truncating, the oldest ArchiveRecord row is {}", job.getOldestRowDate());
				int deleted = job.truncateBatch();
				while (deleted > 0) {
					log.debug("Deleted {} ArchiveRecord rows in {}ms. total:{}, elapsed:{}, pauseMs:{}", deleted, job
							.getBatchElapsedMs(), job.getTotalDeleted(), job.getTotalElapsed(), job.getPauseMs());
					LangUtils.sleep(job.getPauseMs()); // Let the server breathe a little before trying again
					if (stop) {
						break;
					}
					deleted = job.truncateBatch();
					if (deleted == 0) {
						// Try again. It is possible the IDs are so sparse that we get a false zero value
						job.reset();
						deleted = job.truncateBatch();
						if (deleted == 0) {
							break;
						}
					}
				}
				log.info("Deleted {} total ArchiveRecord rows in {}", job.getTotalDeleted(), job.getTotalElapsed());
				totalArchiveRecordsDeleted = job.getTotalDeleted();
			}

			//
			// Delete old user history rows
			//
			if (!stop) {
				// Create a "job" object with one method overridden.
				TruncateJobState job = new TruncateJobState() {

					@Override
					public UpdateQuery<Integer> getUpdate(int keepDaysBack, long maxId) {
						return new DbTruncateUserHistoryQuery(keepDaysBack, maxId);
					}

					@Override
					public FindQuery<Pair<Long, Date>> getQuery() {
						return new FindMinQuery("t_user_history", "user_history_id", "login_date");
					}
				};

				log.debug("Before truncating, the oldest UserHistory row is {}", job.getOldestRowDate());
				int deleted = job.truncateBatch();
				while (deleted > 0) {
					log.debug("Deleted {} UserHistory rows in {}ms. total:{}, elapsed:{}, pauseMs:{}", deleted, job
							.getBatchElapsedMs(), job.getTotalDeleted(), job.getTotalElapsed(), job.getPauseMs());
					LangUtils.sleep(job.getPauseMs()); // Let the server breathe a little before trying again
					if (stop) {
						break;
					}
					deleted = job.truncateBatch();
					if (deleted == 0) {
						// Try again. It is possible the IDs are so sparse that we get a false zero value
						job.reset();
						deleted = job.truncateBatch();
						if (deleted == 0) {
							break;
						}
					}
				}
				log.info("Deleted {} total UserHistory rows in {}", job.getTotalDeleted(), job.getTotalElapsed());
				totalUserHistoryDeleted = job.getTotalDeleted();
			}

		} finally {
			this.db.closeSession();
			String status = stop ? "STOPPED" : "COMPLETED";
			log.info("{}: elapsed:{}, totalArchiveRecordsDeleted:{}, totalUserHistoryRowsDeleted:{}", status, stopwatch,
					totalArchiveRecordsDeleted, totalUserHistoryDeleted);
			synchronized (monitor) {
				DbTruncateCmd.stopwatch = null;
				DbTruncateCmd.stop = false;
			}
		}

		return true;
	}

	public abstract class TruncateJobState {

		Pair<Long, Date> minIdPair;
		long maxId = 0;
		int keepDays = 0;
		int batchSize = 0;
		long pauseMs = 0;
		long totalDeleted = 0;
		Stopwatch totalSw = new Stopwatch();
		Stopwatch batchSw = new Stopwatch();

		public TruncateJobState() throws CommandException {
			this.reset();
		}

		public long getTotalDeleted() {
			return this.totalDeleted;
		}

		private void fetchProperties() {
			// re-fetch these values so they can be changed dynamically
			this.keepDays = SystemProperties.getOptionalInt(DB_TRUNC_KEEP_DAYS, DEFAULT_KEEP_DAYS);
			this.batchSize = SystemProperties.getOptionalInt(DB_TRUNC_BATCH_SIZE, DEFAULT_BATCH_SIZE);
			this.pauseMs = SystemProperties.getOptionalLong(DB_TRUNC_BATCH_PAUSE_MS, DEFAULT_BATCH_PAUSE_MS);
		}

		public void reset() throws CommandException {
			this.minIdPair = DbTruncateCmd.this.db.find(this.getQuery());
			this.maxId = this.minIdPair.getOne();
		}

		public long getBatchElapsedMs() {
			return this.batchSw.getElapsed();
		}

		public String getBatchElapsed() {
			return this.batchSw.toString();
		}

		public String getTotalElapsed() {
			return this.totalSw.toString();
		}

		/** This is the oldest row as of the start of this job */
		public Date getOldestRowDate() {
			return this.minIdPair.getTwo();
		}

		public long getPauseMs() {
			return this.pauseMs;
		}

		public int truncateBatch() {
			this.batchSw.reset();
			this.fetchProperties(); // In case someone has changed them recently.
			this.maxId = this.maxId + this.batchSize;
			int deleted = -1;

			// We cannot have an umbrella transaction when this is called.
			DbTruncateCmd.this.db.ensureNoTransaction();

			try {
				DbTruncateCmd.this.db.beginTransaction();
				DbTruncateCmd.this.db.manual();
				// Transaction handling done by the update method.
				// This should be one complete transaction.
				deleted = DbTruncateCmd.this.db.update(this.getUpdate(this.keepDays, this.maxId));
				this.totalDeleted = this.totalDeleted + deleted;
				DbTruncateCmd.this.db.commit();
			} catch (Throwable t) {
				DbTruncateCmd.this.db.rollback();
				log.error("Error deleting old rows", t);
			} finally {
				DbTruncateCmd.this.db.endTransaction();
			}
			this.batchSw.stop();
			return deleted;
		}

		public abstract UpdateQuery<Integer> getUpdate(int keepDaysBack, long maxId);

		public abstract FindQuery<Pair<Long, Date>> getQuery();
	}

	/**
	 * Finds the oldest row for a starting point
	 */
	private static class FindMinQuery extends FindQuery<Pair<Long, Date>> {

		private String tableName;
		private String idColumnName;
		private String dateColumnName;

		FindMinQuery(String tableName, String idColumnName, String dateColumnName) {
			this.tableName = tableName;
			this.idColumnName = idColumnName;
			this.dateColumnName = dateColumnName;
		}

		@Override
		public Pair<Long, Date> query(Session session) throws DBServiceException {
			// String sql = "SELECT COALESCE(MIN(" + this.idColumnName + "),0) FROM " + this.tableName;
			String sql = "SELECT " + this.idColumnName + "," + this.dateColumnName + " FROM " + this.tableName + " WHERE "
					+ this.idColumnName + " = (SELECT MIN(" + this.idColumnName + ") FROM " + this.tableName + ")";
			SQLQuery q = new SQLQuery(session, sql);
			List<Object[]> rows = q.list();
			if (rows.isEmpty()) {
				return new Pair<Long, Date>(0L, null);
			}
			Object[] row = rows.iterator().next();
			long id = SQLUtils.getlong(row[0]);
			Date date = SQLUtils.getDate(row[1]);
			return new Pair<Long, Date>(id, date);
		}
	}

}
