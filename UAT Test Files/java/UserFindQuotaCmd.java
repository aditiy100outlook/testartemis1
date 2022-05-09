package com.code42.user;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.hibernate.Session;

import com.backup42.social.SocialComputerNetworkServices;
import com.code42.backup.AllottedSpace;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsEveryUserManageableCmd;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;
import com.code42.executor.jsr166.Arrays;
import com.code42.social.FriendComputer;
import com.code42.user.UserFindQuotaCmd.UserQuotaDto;

/**
 * QUESTION: Is this an appropriate name for this class? I could argue it should be QuotaFindByUser so that all the
 * Quota commands sort together. It depends on how we'll search for this command most regularly.
 */
public class UserFindQuotaCmd extends DBCmd<List<UserQuotaDto>> {

	private Collection<Integer> userIds;

	public UserFindQuotaCmd(int userId) {
		this(Arrays.asList(new Integer[] { userId }));
	}

	public UserFindQuotaCmd(Collection<Integer> userIds) {
		this.userIds = userIds;
	}

	@Override
	public List<UserQuotaDto> exec(CoreSession session) throws CommandException {
		this.runtime.run(new IsEveryUserManageableCmd(this.userIds, C42PermissionApp.User.READ), session);

		return this.db.find(new UserFindQuotaQuery(this.userIds));
	}

	/**
	 * Query class that provides a session for this call.
	 */
	public static class UserFindQuotaQuery extends FindQuery<List<UserQuotaDto>> {

		private Collection<Integer> userIds;

		public UserFindQuotaQuery(int userId) {
			this(Arrays.asList(new Integer[] { userId }));
		}

		public UserFindQuotaQuery(Collection<Integer> userIds) {
			this.userIds = userIds;
		}

		@Override
		public List<UserQuotaDto> query(Session session) throws DBServiceException {
			List<UserQuotaDto> quotas = new ArrayList<UserQuotaDto>();
			for (Integer userId : this.userIds) {
				FriendComputer fc = SocialComputerNetworkServices.getInstance().getCPCFriendComputer(userId);

				Long quota = null;
				// always remember to check for the UNLIMITED constant. if that's present then there is no quota.
				if (fc != null && AllottedSpace.UNLIMITED != fc.getOfferedBytes()) {
					quota = fc.getOfferedBytes();
				}
				quotas.add(new UserQuotaDto(userId, quota));
			}

			return quotas;
		}

	}

	/**
	 * Data Transfer Object
	 */
	public static class UserQuotaDto {

		private int userId;
		private Long quotaInBytes;

		public UserQuotaDto(int userId, Long quotaInBytes) {
			this.userId = userId;
			this.quotaInBytes = quotaInBytes;
		}

		public int getUserId() {
			return this.userId;
		}

		public Long getQuotaInBytes() {
			return this.quotaInBytes;
		}
	}

}
