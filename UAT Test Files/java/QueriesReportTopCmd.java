package com.code42.util;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.impl.DBTimer.Stats;
import com.code42.core.impl.DBCmd;
import com.code42.core.space.ISpaceService;
import com.code42.core.space.SpaceException;
import com.google.inject.Inject;

/**
 * Report the most used Queries to the namespace
 * 
 * @author tony
 * 
 */
public class QueriesReportTopCmd extends DBCmd<Void> {

	private int limit = 50;
	public static final String namespace = "/db/topQueries";
	private final TopComparator TOP_SORT = new TopComparator();

	private ISpaceService space;

	@Inject
	public void setSpaceService(ISpaceService space) {
		this.space = space;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		this.auth.isSysadmin(session);

		try {

			List<Stats> stats = this.db.getStats(this.TOP_SORT, this.limit);
			this.space.put(namespace, (Serializable) stats);

			// A useless query put here to ensure that injection is working and we have a valid runtime
			// User user = this.runtime.run(new UserFindById(1));
			return null;
		} catch (SpaceException se) {
			throw new CommandException("Exception while performing space operations", se);
		}
	}

	/**
	 * 
	 * Compare two Stats objects, sorting the highest runcount first. Two nulls are equal, and nulls sort last.
	 * 
	 * @author <a href="mailto:tony@code42.com">Tony Lindquist</a>
	 * 
	 */
	private class TopComparator implements Comparator<Stats> {

		public int compare(Stats o1, Stats o2) {
			if (o1 != null && o2 != null) {
				long r1 = o1.getRunCount();
				long r2 = o2.getRunCount();
				if (r1 == r2) {
					return 0;
				} else {
					return (r1 > r2) ? -1 : 1;
				}
			} else if (o1 == null && o2 == null) {
				return 0;
			} else if (o1 != null) {
				return -1;
			} else {
				return 1;
			}
		}
	}
}
