package com.code42.computer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.Query;
import org.hibernate.Session;

import com.code42.core.CommandException;
import com.code42.core.annotation.CoreNamedQuery;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBRequestTooLargeException;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;
import com.code42.utils.SystemProperties;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;
import com.google.common.collect.ImmutableSet;

/**
 * Load a set of computer SSOs directly from the database if their computer IDs can be found in the input set. <br>
 * <br>
 * Note that this is an expensive operation! If you have an IBusinessObjectsService instance available you're almost
 * certainly better off using that. You might even want something like {@link ComputerSsoFindMultipleByGuidCmd} keyed by
 * computer ID rather than GUID.
 * 
 * @author marshall
 */
public class ComputerSsoLoadMultipleByComputerIdCmd extends DBCmd<Map<Long, Option<ComputerSso>>> {

	private final Set<Long> ids;

	public ComputerSsoLoadMultipleByComputerIdCmd(Set<Long> ids) {

		this.ids = ImmutableSet.copyOf(ids);
	}

	@Override
	public Map<Long, Option<ComputerSso>> exec(CoreSession session) throws CommandException {

		this.db.manual();
		return this.db.find(new ComputerSsoPopulateQuery());
	}

	@CoreNamedQuery(name = "loadMultipleComputersByComputerId", query = "select c from Computer c where computerId in (:ids)")
	private class ComputerSsoPopulateQuery extends FindQuery<Map<Long, Option<ComputerSso>>> {

		@Override
		public Map<Long, Option<ComputerSso>> query(Session session) throws DBServiceException {

			if (ComputerSsoLoadMultipleByComputerIdCmd.this.ids.size() > SystemProperties.getMaxQueryInClauseSize()) {
				throw new DBRequestTooLargeException("REQUEST_TOO_LARGE, orgIds: {}",
						ComputerSsoLoadMultipleByComputerIdCmd.this.ids.size());
			}

			Map<Long, Option<ComputerSso>> rv = new HashMap<Long, Option<ComputerSso>>();

			Query query = this.getNamedQuery(session);
			query.setParameterList("ids", ComputerSsoLoadMultipleByComputerIdCmd.this.ids);
			for (Computer computer : (List<Computer>) query.list()) {

				rv.put(computer.getComputerId(), new Some<ComputerSso>(new ComputerSso(computer)));
			}

			return rv;
		}
	}
}
