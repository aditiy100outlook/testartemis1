package com.code42.org;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.jdbc.Work;
import org.joda.time.DateTime;

import com.backup42.CpcConstants;
import com.backup42.common.OrgType;
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
import com.google.common.base.Joiner;

/**
 * Find all org SSOs, optionally constraining the results by a creation date. <br>
 * <br>
 * Note that this command <b>ONLY</b> goes to the database. If you just need a collection of SSOs you should almost
 * certainly be injecting and using an IBusinessObjectsService instance rather than making use of this command.
 * 
 * @author bmcguire
 */
public class OrgSsoLoadAllCmd extends DBCmd<Map<Integer, Option<OrgSso>>> {

	private static final Logger log = LoggerFactory.getLogger(OrgSsoLoadAllCmd.class);

	private final EnumSet<OrgType> types;
	private final int windowSize;
	private final DateTime since;

	public OrgSsoLoadAllCmd(EnumSet<OrgType> types, int windowSize) {

		this(types, windowSize, null);
	}

	public OrgSsoLoadAllCmd(EnumSet<OrgType> types, int windowSize, DateTime since) {

		if (types.isEmpty()) {
			throw new IllegalArgumentException("At least one org type must be provided");
		}

		this.types = EnumSet.copyOf(types);
		this.windowSize = windowSize;
		this.since = since;
	}

	@Override
	public Map<Integer, Option<OrgSso>> exec(CoreSession session) throws CommandException {

		return this.db.find(new OrgSsoPopulateQuery());
	}

	/* ========================== Private helper classes ========================== */
	private class OrgSsoPopulateQuery extends FindQuery<Map<Integer, Option<OrgSso>>> {

		@Override
		public Map<Integer, Option<OrgSso>> query(Session session) throws DBServiceException {

			try {

				/* Hibernate Work implementation will populate rv as a side effect */
				Map<Integer, Option<OrgSso>> rv = new HashMap<Integer, Option<OrgSso>>(OrgSsoLoadAllCmd.this.windowSize);
				session.doWork(new OrgSsoPopulateWork(rv));
				return rv;
			} catch (HibernateException he) {
				throw new DBServiceException("Exception doing database ops", he);
			}
		}
	}

	private class OrgSsoPopulateWork implements Work {

		private static final String BASE_SQL = "" + //
				"select org_id, org_uid, org_name, type, active, blocked, discriminator, parent_org_id, master_guid, " + //
				"  max_seats, max_bytes " + //
				"from t_org";

		Map<Integer, Option<OrgSso>> target;

		OrgSsoPopulateWork(Map<Integer, Option<OrgSso>> target) {

			this.target = target;
		}

		private ResultSet getResultSet(Connection conn) throws SQLException {

			/*
			 * We have a set of criteria that are used to determine which orgs to include. We always have one such criteria
			 * since we always want to include the admin org regardless of the types used. We also want to include all orgs of
			 * a type appropriate to this server.
			 * 
			 * Note that orgs matching one or more of these criteria may be filtered out by subsequent constraints, most
			 * notably the constraint on creation_date if we're called as part of an update. Since a constraint _might_ be
			 * applied we specify all criteria in a single clause bounded by parentheses. This allows any subsequent
			 * constraints to be applied to every org included here and avoids any ambiguity re: or/and precedence.
			 */
			List<String> criteria = new LinkedList<String>();
			criteria.add("org_id = ?");
			int typesSize = OrgSsoLoadAllCmd.this.types.size();
			for (int i = 0; i < typesSize; ++i) {
				criteria.add("or type = ?");
			}
			String sql = BASE_SQL + " where (" + Joiner.on(" ").join(criteria) + ")";
			if (OrgSsoLoadAllCmd.this.since != null) {

				sql = sql + " and creation_date > ?";
			}

			/*
			 * The use of a PreparedStatement presents a bit of ugliness here. We're tasked with first creating the SQL string
			 * based on input data and then iterating over that data a second time in order to set the appropriate arguments
			 * to the PreparedStatement. No other obvious way to do this; it's a bit inefficient but it's not immediately
			 * clear to me how we might make it better.
			 */
			PreparedStatement stmt = conn.prepareStatement(sql);

			int idx = 1;
			stmt.setInt(idx++, CpcConstants.Orgs.ADMIN_ID);
			for (OrgType type : OrgSsoLoadAllCmd.this.types) {
				stmt.setString(idx++, type.toString().toUpperCase());
			}
			if (OrgSsoLoadAllCmd.this.since != null) {
				stmt.setTimestamp(idx, new java.sql.Timestamp(OrgSsoLoadAllCmd.this.since.getMillis()));
			}

			stmt.setFetchSize(OrgSsoLoadAllCmd.this.windowSize);
			ResultSet rs = stmt.executeQuery();
			rs.setFetchSize(OrgSsoLoadAllCmd.this.windowSize);
			return rs;
		}

		public void execute(Connection conn) throws SQLException {

			OrgSso.Builder builder = new OrgSso.Builder();

			int cnt = 0;
			final int logInterval = 10000;

			ResultSet rs = this.getResultSet(conn);
			while (rs.next()) {

				int orgId = rs.getInt(1);
				builder.orgId(orgId) //
						.orgUid(rs.getString(2)) //
						.orgName(rs.getString(3)) //
						.orgType(OrgType.valueOf(rs.getString(4))) //
						.active(rs.getBoolean(5)) //
						.blocked(rs.getBoolean(6)) //
						.discriminator(rs.getString(7));

				/* Gather optional vals as well (if present) */
				int parentOrgId = rs.getInt(8);
				if (!rs.wasNull()) {
					builder.parentOrgId(parentOrgId);
				}
				long masterGuid = rs.getLong(9);
				if (!rs.wasNull()) {
					builder.masterGuid(masterGuid);
				}
				int maxSeats = rs.getInt(10);
				if (!rs.wasNull()) {
					builder.maxSeats(maxSeats);
				}
				long maxBytes = rs.getLong(11);
				if (!rs.wasNull()) {
					builder.maxBytes(maxBytes);
				}

				try {

					OrgSso sso = builder.build();
					this.target.put(orgId, new Some<OrgSso>(sso));
				} catch (BuilderException be) {

					log.info(String.format("Exception building SSO for org with ID %s: %s", orgId, be.getMessage()));
				}
				builder.reset();

				++cnt;
				if ((cnt % logInterval) == 0) {
					log.info("Finished processing {} orgs", cnt);
				}
			}
			rs.close();
		}
	}
}
