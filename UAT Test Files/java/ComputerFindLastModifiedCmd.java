package com.code42.computer;

import java.util.Date;

import org.hibernate.Query;
import org.hibernate.Session;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;

/**
 * Performs a single query to find the most recent modification date for all the data returned for one computer in the
 * REST resource.
 */
public class ComputerFindLastModifiedCmd extends DBCmd<Date> {

	private long computerId;
	private boolean destinations;

	/**
	 * 
	 * @param computerId
	 * @param destinations - true if destinations are being requested
	 */
	public ComputerFindLastModifiedCmd(long computerId, boolean destinations) {
		this.computerId = computerId;
		this.destinations = destinations;
	}

	@Override
	public Date exec(CoreSession session) throws CommandException {

		// This needs to be a very fast operation. No permission checking for just finding the last modified timestamp.

		return this.db.find(new ComputerFindLastModifiedQuery(this.computerId, this.destinations));
	}

	/**
	 * Unions a bunch of queries on the modified dates and returns the latest
	 */
	public static class ComputerFindLastModifiedQuery extends FindQuery<Date> {

		private long computerId;
		private boolean destinations;

		public ComputerFindLastModifiedQuery(long computerId, boolean destinations) {
			this.computerId = computerId;
			this.destinations = destinations;
		}

		@Override
		public Date query(Session session) throws DBServiceException {
			String sql = buildSql(this.destinations);
			// Doing a SQL replace here so we can replace them all with one statement.
			// This is very safe given that we are replacing a numeric value and not a string.
			sql = sql.replaceAll(":computerId", String.valueOf(this.computerId));
			Query q = session.createSQLQuery(sql);
			return (Date) q.uniqueResult();
		}

		public static String buildSql(boolean destinations) {
			if (destinations) {
				return "select max(modification_date) from ( \n"
						+ " select modification_date from t_computer where computer_id = :computerId   \n"
						+ " union select modification_date from t_friend_computer_usage where source_computer_id = :computerId"
						+ ") as mod_dates \n";
			}
			return "select modification_date from t_computer where computer_id = :computerId";
		}
	}

	public static void main(String[] args) {
		System.out.println(ComputerFindLastModifiedQuery.buildSql(true).replaceAll(":computerId", "150"));
		System.out.println("------------");
		System.out.println(ComputerFindLastModifiedQuery.buildSql(false).replaceAll(":computerId", "150"));
	}

}