package com.code42.user;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.perm.C42PermissionCommerce.Commerce;

public class UserFindByChannelCustomerIdCmd extends DBCmd<User> {

	private static final Logger log = LoggerFactory.getLogger(UserFindByChannelCustomerIdCmd.class);

	private String channelCustomerId;

	public UserFindByChannelCustomerIdCmd(String channelCustomerId) {
		this.channelCustomerId = channelCustomerId;
	}

	@Override
	public User exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, Commerce.CHANNEL);
		User user = null;

		try {
			user = this.db.find(new UserFindByChannelCustomerIdQuery(this.channelCustomerId));
		} catch (DBServiceException e) {
			log.info("Incorrect number of user returned.", e);
		}
		return user;
	}

}
