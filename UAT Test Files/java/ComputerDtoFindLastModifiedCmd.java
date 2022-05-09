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
 * Performs a single query to find the last modification date for all the data used in the ComputerDto REST resource.
 */
public class ComputerDtoFindLastModifiedCmd extends DBCmd<Date> {

	private long computerId;

	public ComputerDtoFindLastModifiedCmd(long computerId) {
		this.computerId = computerId;
	}

	@Override
	public Date exec(CoreSession session) throws CommandException {

		// This needs to be a very fast operation. No permission checking for just finding the last modified timestamp.

		return this.db.find(new ComputerDtoFindLastModifiedQuery(this.computerId));
	}

	/**
	 * Unions a bunch of queries on the modified dates and returns the latest
	 */
	public static class ComputerDtoFindLastModifiedQuery extends FindQuery<Date> {

		private static final String SQL = "select max(modification_date) from  \n"
				+ "(select u.modification_date from t_user as u                                           \n"
				+ " inner join t_computer as c on (c.user_id = u.user_id and c.computer_id = :computerId) \n"
				+ "union select o.modification_date from t_org as o                                       \n"
				+ " inner join t_user as u on (u.org_id = o.org_id)                                       \n"
				+ " inner join t_computer as c on (c.user_id = u.user_id and c.computer_id = :computerId) \n"
				+ "union select modification_date from t_computer where computer_id = :computerId         \n"
				+ "union select modification_date from t_friend_computer_usage where source_computer_id = :computerId \n"
				+ ") as mod_dates \n";

		private long computerId;

		public ComputerDtoFindLastModifiedQuery(long computerId) {
			this.computerId = computerId;
		}

		@Override
		public Date query(Session session) throws DBServiceException {
			String sql = SQL.replaceAll(":computerId", String.valueOf(this.computerId));
			Query q = session.createSQLQuery(sql);
			return (Date) q.uniqueResult();
		}
	}

	public static void main(String[] args) {
		System.out.println(ComputerDtoFindLastModifiedQuery.SQL.replaceAll(":computerId", "243945"));
	}

}