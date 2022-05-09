package com.code42.computer;

import org.hibernate.Session;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.CreateQuery;
import com.code42.core.impl.DBCmd;
import com.code42.social.FriendComputerUsage;
import com.code42.utils.UniqueId;

/**
 * Create a row of t_friend_computer_usage.
 */
public class FriendComputerUsageCreateCmd extends DBCmd<FriendComputerUsage> {

	private final FriendComputerUsage fcu;

	public FriendComputerUsageCreateCmd(final FriendComputerUsage fcu) {
		this.fcu = fcu;
	}

	@Override
	public FriendComputerUsage exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		if (this.fcu.getVersion() == null) {
			this.fcu.setVersion(UniqueId.generateId());
		}

		try {
			this.db.beginTransaction();

			final FriendComputerUsage updatedFcu = this.db.create(new FriendComputerUsageCreateQuery(this.fcu));

			this.run(new FriendComputerUsageChangePublishCmd(updatedFcu, true), session);

			this.db.commit();

			return updatedFcu;
		} finally {
			this.db.endTransaction();
		}
	}

	private static class FriendComputerUsageCreateQuery extends CreateQuery<FriendComputerUsage> {

		private final FriendComputerUsage fcu;

		public FriendComputerUsageCreateQuery(FriendComputerUsage fcu) {
			this.fcu = fcu;
		}

		@Override
		public FriendComputerUsage query(Session session) throws DBServiceException {
			session.save(this.fcu);
			return this.fcu;
		}
	}
}
