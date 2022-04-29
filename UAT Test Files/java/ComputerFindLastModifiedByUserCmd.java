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
 * Performs a single query to find the most recent modification date for all the data
 * returned for computers of one user.
 */
public class ComputerFindLastModifiedByUserCmd extends DBCmd<Date> {

	private int userId;

	public ComputerFindLastModifiedByUserCmd(int userId) {
		this.userId = userId;
	}

	@Override
	public Date exec(CoreSession session) throws CommandException {

		// This needs to be a very fast operation.
		// No permission checking for just finding the last modified timestamp.

		return this.db.find(new ComputerFindLastModifiedByUserQuery(this.userId));
	}

	/**
	 * Unions several modification_date queries and returns the latest
	 */
	public static class ComputerFindLastModifiedByUserQuery extends FindQuery<Date> {

		private static final String SQL = "select max(modification_date) from (           \n"
				+ "select modification_date from t_computer where user_id = :userId    union  \n"
				+ "select fcu.modification_date from t_friend_computer_usage as fcu           \n"
				+ "inner join t_computer as c on (c.computer_id = fcu.source_computer_id and c.user_id = :userId)  \n"
				+ ") as mod_dates \n";

		private int userId;

		public ComputerFindLastModifiedByUserQuery(int userId) {
			this.userId = userId;
		}

		@Override
		public Date query(Session session) throws DBServiceException {
			// Doing a SQL replace here so we can replace them all with one statement.
			// This is very safe given that we are replacing a numeric value and not a string.
			String sql = SQL.replaceAll(":userId", String.valueOf(this.userId));
			Query q = session.createSQLQuery(sql);
			return (Date) q.uniqueResult();
		}
	}

	public static void main(String[] args) {
		System.out.println(ComputerFindLastModifiedByUserQuery.SQL.replaceAll(":userId", "258"));
	}

}