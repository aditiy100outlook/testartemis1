/*
 * Created on March 16, 2011 by Jon Carlson
 * <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.computer;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Query;
import org.hibernate.Session;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;
import com.code42.sql.SQLUtils;
import com.code42.utils.Pair;

/**
 * Command to find all computer names that are backup destinations for the given computerId
 */
public class ComputerNameFindBySourceComputerCmd extends DBCmd<List<Pair<Long, String>>> {

	private long sourceComputerId;

	/**
	 * Finds all computer names that are backup destinations for the given computerId
	 * 
	 * @param orgId
	 * @return a list of Pairs (computerId & computerName... in that order)
	 */
	public ComputerNameFindBySourceComputerCmd(long sourceComputerId) {
		this.sourceComputerId = sourceComputerId;
	}

	@Override
	public List<Pair<Long, String>> exec(CoreSession session) throws CommandException {

		this.runtime.run(new IsComputerManageableCmd(this.sourceComputerId, C42PermissionApp.Computer.READ), session);

		// Find the computer names and return them
		ComputerNameFindBySourceComputerQuery query = new ComputerNameFindBySourceComputerQuery(this.sourceComputerId);
		List<Pair<Long, String>> list = this.db.find(query);

		return list;
	}

	private static class ComputerNameFindBySourceComputerQuery extends FindQuery<List<Pair<Long, String>>> {

		private long sourceComputerId;
		private String sql = "select c.computer_id, c.name from t_computer as c \n"
				+ "inner join t_friend_computer_usage as fcu \n"
				+ "    on (fcu.target_computer_id = c.computer_id and fcu.source_computer_id = :sourceComputerId)";

		public ComputerNameFindBySourceComputerQuery(long sourceComputerId) {
			this.sourceComputerId = sourceComputerId;
		}

		@Override
		public List<Pair<Long, String>> query(Session session) throws DBServiceException {
			Query q = session.createSQLQuery(this.sql);
			q.setLong("sourceComputerId", this.sourceComputerId);
			List<Object[]> rows = q.list();
			List<Pair<Long, String>> list = new ArrayList<Pair<Long, String>>();
			for (Object[] row : rows) {
				Pair pair = new Pair(
						SQLUtils.getlong(row[0]),
						SQLUtils.getString(row[1])
						);
				list.add(pair);
			}
			return list;
		}
	}

}