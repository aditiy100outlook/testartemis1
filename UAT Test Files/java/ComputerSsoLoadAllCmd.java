package com.code42.computer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.jdbc.Work;
import org.joda.time.DateTime;

import com.backup42.common.ComputerType;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;
import com.google.common.collect.ImmutableSet;

/**
 * Find all computer SSOs, optionally constraining the results by a creation date <br>
 * <br>
 * Note that this command <b>ONLY</b> goes to the database. If you just need a collection of SSOs you should almost
 * certainly be injecting and using an IBusinessObjectsService instance rather than making use of this command.
 * 
 * @author bmcguire
 */
public class ComputerSsoLoadAllCmd extends DBCmd<Map<Long, Option<ComputerSso>>> {

	private static final Logger log = LoggerFactory.getLogger(ComputerSsoLoadAllCmd.class);

	private final int windowSize;
	private final DateTime since;
	private final Set<Integer> users;

	public ComputerSsoLoadAllCmd(int windowSize) {

		this(windowSize, null, null);
	}

	public ComputerSsoLoadAllCmd(int windowSize, DateTime since) {

		this(windowSize, null, since);
	}

	public ComputerSsoLoadAllCmd(int windowSize, Set<Integer> users) {

		this(windowSize, users, null);
	}

	public ComputerSsoLoadAllCmd(int windowSize, Set<Integer> users, DateTime since) {

		this.windowSize = windowSize;
		this.since = since;
		this.users = (users == null) ? ImmutableSet.<Integer> of() : ImmutableSet.copyOf(users);
	}

	@Override
	public Map<Long, Option<ComputerSso>> exec(CoreSession session) throws CommandException {

		return this.db.find(new ComputerSsoPopualteQuery());
	}

	/* ========================== Private helper classes ========================== */
	private class ComputerSsoPopualteQuery extends FindQuery<Map<Long, Option<ComputerSso>>> {

		@Override
		public Map<Long, Option<ComputerSso>> query(Session session) throws DBServiceException {

			try {

				/* Hibernate Work implementation will populate rv as a side effect */
				Map<Long, Option<ComputerSso>> rv = new HashMap<Long, Option<ComputerSso>>(
						ComputerSsoLoadAllCmd.this.windowSize);
				session.doWork(new ComputerSsoPopulateWork(rv));
				return rv;
			} catch (HibernateException he) {
				throw new DBServiceException("Exception doing database ops", he);
			}
		}
	}

	private class ComputerSsoPopulateWork implements Work {

		/* Make sure to exclude child computers from our processing */
		private static final String SQL = "select computer_id, guid, user_id, active, blocked, type from t_computer where parent_computer_id is null";

		Map<Long, Option<ComputerSso>> target;

		ComputerSsoPopulateWork(Map<Long, Option<ComputerSso>> target) {

			this.target = target;
		}

		private ResultSet getResultSet(Connection conn) throws SQLException {

			if (ComputerSsoLoadAllCmd.this.since == null) {

				Statement stmt = conn.createStatement();
				stmt.setFetchSize(ComputerSsoLoadAllCmd.this.windowSize);
				ResultSet rs = stmt.executeQuery(SQL);
				rs.setFetchSize(ComputerSsoLoadAllCmd.this.windowSize);
				return rs;
			}

			PreparedStatement stmt = conn.prepareStatement(String.format("%s and creation_date > ?", SQL));
			stmt.setTimestamp(1, new java.sql.Timestamp(ComputerSsoLoadAllCmd.this.since.getMillis()));
			stmt.setFetchSize(ComputerSsoLoadAllCmd.this.windowSize);
			ResultSet rs = stmt.executeQuery();
			rs.setFetchSize(ComputerSsoLoadAllCmd.this.windowSize);
			return rs;
		}

		public void execute(Connection conn) throws SQLException {

			ComputerSso.Builder builder = new ComputerSso.Builder();

			int cnt = 0;
			final int logInterval = 50000;

			ResultSet rs = this.getResultSet(conn);
			while (rs.next()) {

				long computerId = rs.getLong(1);
				int userId = rs.getInt(3);
				if (ComputerSsoLoadAllCmd.this.users != null && (!ComputerSsoLoadAllCmd.this.users.contains(userId))) {
					continue;
				}

				long guid = rs.getLong(2);
				builder.computerId(computerId).guid(guid).userId(userId);
				builder.active(rs.getBoolean(4)).blocked(rs.getBoolean(5)).type(ComputerType.valueOf(rs.getString(6)));

				try {

					this.target.put(computerId, new Some<ComputerSso>(builder.build()));
				} catch (BuilderException be) {

					log.info(String.format("Exception building SSO for computer with GUID %s: %s", guid, be.getMessage()));
				}
				builder.reset();

				++cnt;
				if ((cnt % logInterval) == 0) {
					log.info("Finished processing {} computers", cnt);
				}
			}
			rs.close();
		}
	}
}
