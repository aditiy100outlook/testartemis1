package com.code42.user;

import java.util.Date;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

/**
 * Encapsulation of the business logic around updating a notion of "user history" when a user logs in to the web app. We
 * can specify to login as a user other than ourselves if need be, but the default behavior is to login as the subject
 * executing this command.
 */
public class UserHistoryLoginCmd extends DBCmd<Integer> {

	/* By default we're logging into the manage app.. */
	private String appCode = "CPC";
	private Integer userId = null;

	public UserHistoryLoginCmd() {
	}

	public UserHistoryLoginCmd(String appCode) {
		this.appCode = appCode;
	}

	public UserHistoryLoginCmd(Integer userId) {
		this.userId = userId;
	}

	public UserHistoryLoginCmd(String appCode, Integer userId) {
		this.appCode = appCode;
		this.userId = userId;
	}

	@Override
	public Integer exec(CoreSession session) throws CommandException {

		/* Create a new UserHistory object, populate it and save */
		UserHistory history = new UserHistory();
		history.setLoginDate(new Date());
		history.setUserId(this.userId != null ? this.userId : session.getUser().getUserId());

		/* For now we only support CPC logins */
		if (this.appCode.equals("CPC")) {
			history.setAppCode(AppCode.CPC);
		} else {
			throw new CommandException("Unsupported app code: " + this.appCode);
		}

		this.db.create(new UserHistoryCreateQuery(history));

		return history.getUserHistoryId();
	}

}
