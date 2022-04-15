package com.code42.computer;

import org.hibernate.Session;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;
import com.code42.social.FriendComputerUsage;

/**
 * Find a row of t_friend_computer_usage by its id.
 * 
 * @author mharper
 */
public class FriendComputerUsageFindByIdCmd extends DBCmd<FriendComputerUsage> {

	private final long fcuId;

	public FriendComputerUsageFindByIdCmd(long fcuId) {
		super();
		this.fcuId = fcuId;
	}

	@Override
	public FriendComputerUsage exec(CoreSession session) throws CommandException {
		// Use most strict auth check for now. It's possible this could be loosened.
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		FriendComputerUsageFindByIdQuery query = new FriendComputerUsageFindByIdQuery(this.fcuId);
		return this.db.find(query);
	}

	private static class FriendComputerUsageFindByIdQuery extends FindQuery<FriendComputerUsage> {

		private final long fcuId;

		public FriendComputerUsageFindByIdQuery(long fcuId) {
			super();
			this.fcuId = fcuId;
		}

		@Override
		public FriendComputerUsage query(Session session) throws DBServiceException {
			return (FriendComputerUsage) session.get(FriendComputerUsage.class, this.fcuId);
		}

	}

}