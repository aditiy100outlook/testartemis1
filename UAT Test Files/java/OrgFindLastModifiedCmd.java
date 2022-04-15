package com.code42.org;

import java.util.Date;

import org.hibernate.Query;
import org.hibernate.Session;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;

/**
 * Performs a single query to find the most recent modification date for all the data returned
 * for one org in the REST resource.
 */
public class OrgFindLastModifiedCmd extends DBCmd<Date> {

	private int orgId;

	public OrgFindLastModifiedCmd(int orgId) {
		this.orgId = orgId;
	}

	@Override
	public Date exec(CoreSession session) throws CommandException {

		// This needs to be a very fast operation.
		// No permission checking for just finding the last modified timestamp.

		return this.db.find(new OrgFindLastModifiedQuery(this.orgId));
	}

	/**
	 * Unions several modification_date queries and returns the latest
	 */
	public static class OrgFindLastModifiedQuery extends FindQuery<Date> {

		private static final String SQL = "select max(modification_date) from (                          \n"
				+ "select modification_date from t_org where org_id = :orgId           union                 \n"
				+ "select modification_date from t_archive_summary where org_id = :orgId and user_id is null \n"
				+ ") as mod_dates \n";

		private int orgId;

		public OrgFindLastModifiedQuery(int orgId) {
			this.orgId = orgId;
		}

		@Override
		public Date query(Session session) throws DBServiceException {
			// Doing a SQL replace here so we can replace them all with one statement.
			// This is very safe given that we are replacing a numeric value and not a string.
			String sql = SQL.replaceAll(":orgId", String.valueOf(this.orgId));
			Query q = session.createSQLQuery(sql);
			return (Date) q.uniqueResult();
		}
	}

	public static void main(String[] args) {
		System.out.println(OrgFindLastModifiedQuery.SQL.replaceAll(":orgId", "157111"));
	}

}