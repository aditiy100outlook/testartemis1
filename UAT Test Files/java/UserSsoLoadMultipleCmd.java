package com.code42.user;

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

/**
 * Load a set of user SSOs directly from the database if their user IDs can be found in the input set. <br>
 * <br>
 * Note that this is an expensive operation! If you have an IBusinessObjectsService instance available you're almost
 * certainly better off using that. In nearly all cases you want {@link UserSsoFindMultipleByUserIdCmd} rather than this
 * command.
 * 
 * @author marshall
 */
public class UserSsoLoadMultipleCmd extends DBCmd<Map<Integer, Option<UserSso>>> {

	private final Set<Integer> userIds;

	public UserSsoLoadMultipleCmd(Set<Integer> userIds) {

		this.userIds = userIds;
	}

	@Override
	public Map<Integer, Option<UserSso>> exec(CoreSession session) throws CommandException {

		this.db.manual();

		return this.db.find(new UserSsoPopulateByUserIdQuery());
	}

	@CoreNamedQuery(name = "loadMultipleUsersByUserId", query = "select u from User u where user_id in (:userIds)")
	private class UserSsoPopulateByUserIdQuery extends FindQuery<Map<Integer, Option<UserSso>>> {

		@Override
		public Map<Integer, Option<UserSso>> query(Session session) throws DBServiceException {

			if (UserSsoLoadMultipleCmd.this.userIds.size() > SystemProperties.getMaxQueryInClauseSize()) {
				throw new DBRequestTooLargeException("REQUEST_TOO_LARGE, orgIds: {}", UserSsoLoadMultipleCmd.this.userIds
						.size());
			}

			Map<Integer, Option<UserSso>> rv = new HashMap<Integer, Option<UserSso>>();

			Query query = this.getNamedQuery(session);
			query.setParameterList("userIds", UserSsoLoadMultipleCmd.this.userIds);
			for (User user : (List<User>) query.list()) {
				rv.put(user.getUserId(), new Some<UserSso>(new UserSso(user)));
			}

			return rv;
		}
	}
}
