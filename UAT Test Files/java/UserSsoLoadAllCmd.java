package com.code42.user;

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
 * Find all org SSOs, optionally constraining the results by a creation date <br>
 * <br>
 * Note that this command <b>ONLY</b> goes to the database. If you just need a collection of SSOs you should almost
 * certainly be injecting and using an IBusinessObjectsService instance rather than making use of this command.
 * 
 * @author bmcguire
 */
public class UserSsoLoadAllCmd extends DBCmd<Map<Integer, Option<UserSso>>> {

	private static final Logger log = LoggerFactory.getLogger(UserSsoLoadAllCmd.class);

	private final int windowSize;
	private final DateTime since;
	private final Set<Integer> orgs;

	public UserSsoLoadAllCmd(int windowSize) {

		this(windowSize, null, null);
	}

	public UserSsoLoadAllCmd(int windowSize, DateTime since) {

		this(windowSize, null, since);
	}

	public UserSsoLoadAllCmd(int windowSize, Set<Integer> orgs) {

		this(windowSize, orgs, null);
	}

	public UserSsoLoadAllCmd(int windowSize, Set<Integer> orgs, DateTime since) {

		this.windowSize = windowSize;
		this.since = since;
		this.orgs = orgs != null ? ImmutableSet.copyOf(orgs) : null;
	}

	@Override
	public Map<Integer, Option<UserSso>> exec(CoreSession session) throws CommandException {

		return this.db.find(new UserSsoPopulateQuery());
	}

	/* ========================== Private helper classes ========================== */
	private class UserSsoPopulateQuery extends FindQuery<Map<Integer, Option<UserSso>>> {

		@Override
		public Map<Integer, Option<UserSso>> query(Session session) throws DBServiceException {

			try {

				/* Hibernate Work implementation will populate rv as a side effect */
				Map<Integer, Option<UserSso>> rv = new HashMap<Integer, Option<UserSso>>(UserSsoLoadAllCmd.this.windowSize);
				session.doWork(new UserSsoPopulateWork(rv));
				return rv;
			} catch (HibernateException he) {
				throw new DBServiceException("Exception doing database ops", he);
			}
		}
	}

	private class UserSsoPopulateWork implements Work {

		private static final String SQL = "select user_id, user_uid, username, email, org_id, active, blocked, password, max_bytes, creation_date from t_user";

		Map<Integer, Option<UserSso>> target;

		UserSsoPopulateWork(Map<Integer, Option<UserSso>> target) {

			this.target = target;
		}

		private ResultSet getResultSet(Connection conn) throws SQLException {

			if (UserSsoLoadAllCmd.this.since == null) {

				Statement stmt = conn.createStatement();
				stmt.setFetchSize(UserSsoLoadAllCmd.this.windowSize);
				ResultSet rs = stmt.executeQuery(SQL);
				rs.setFetchSize(UserSsoLoadAllCmd.this.windowSize);
				return rs;
			}

			PreparedStatement stmt = conn.prepareStatement(String.format("%s where creation_date > ?", SQL));
			stmt.setTimestamp(1, new java.sql.Timestamp(UserSsoLoadAllCmd.this.since.getMillis()));
			stmt.setFetchSize(UserSsoLoadAllCmd.this.windowSize);
			ResultSet rs = stmt.executeQuery();
			rs.setFetchSize(UserSsoLoadAllCmd.this.windowSize);
			return rs;
		}

		public void execute(Connection conn) throws SQLException {

			int cnt = 0;
			final int logInterval = 50000;

			ResultSet rs = this.getResultSet(conn);
			while (rs.next()) {

				UserSso.Builder builder = new UserSso.Builder();

				int userId = rs.getInt(1);
				int orgId = rs.getInt(5);

				/*
				 * To prevent users from blue orgs being added to a green cluster (and vice versa). The SQL used to do this
				 * inline but at some point in the process it was decided that joining against t_org was too expensive and that
				 * it would be more efficient to do this check in Java code.
				 * 
				 * Note that the same op occurs in ComputerSsoLoadAllCmd.
				 */
				if (UserSsoLoadAllCmd.this.orgs != null && (!UserSsoLoadAllCmd.this.orgs.contains(orgId))) {
					continue;
				}

				builder.userId(userId) //
						.userUid(rs.getString(2)) //
						.username(rs.getString(3)) //
						.email(rs.getString(4)) //
						.orgId(orgId) //
						.active(rs.getBoolean(6)) //
						.blocked(rs.getBoolean(7)) //
						.password(rs.getString(8)) //
						.maxBytes(rs.getLong(9)) //
						.creationDate(rs.getTimestamp(10));

				try {

					UserSso sso = builder.build();
					this.target.put(userId, new Some<UserSso>(sso));
				} catch (BuilderException be) {

					log.info(String.format("Exception building SSO for user with user ID %s: %s", userId, be.getMessage()));
				}

				++cnt;
				if ((cnt % logInterval) == 0) {
					log.info("Finished processing {} users", cnt);
				}
			}
			rs.close();
		}
	}
}
