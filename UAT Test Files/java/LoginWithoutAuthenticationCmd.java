/*
 * Created on Nov 9, 2010 by Tony Lindquist <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.user;

import com.code42.core.CommandException;
import com.code42.core.auth.OrgBlockedException;
import com.code42.core.auth.OrgDeactivatedException;
import com.code42.core.auth.UserBlockedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.code42.data.DataProviderException;
import com.code42.exception.DebugRuntimeException;
import com.code42.exception.IgnoredException;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.Org;
import com.code42.org.OrgFindByIdQuery;

public class LoginWithoutAuthenticationCmd extends DBCmd<User> {

	private static final Logger log = LoggerFactory.getLogger(LoginWithoutAuthenticationCmd.class.getName());

	// properties
	private final User user;
	private final boolean verifyBlocked;

	public LoginWithoutAuthenticationCmd(User user) {
		this(user, true);
	}

	public LoginWithoutAuthenticationCmd(User user, boolean verifyBlocked) {
		this.user = user;
		this.verifyBlocked = verifyBlocked;
	}

	@Override
	public User exec(CoreSession session) throws CommandException {

		// Blocked users can never login.
		if (this.verifyBlocked && this.user.isBlocked()) {
			throw new UserBlockedException("Unable to login, blocked user.");
		}

		// Find the org for this user.
		final Org org = this.db.find(new OrgFindByIdQuery(this.user.getOrgId()));

		// Make sure the org is active.
		if (!org.isActive()) {
			throw new OrgDeactivatedException("Unable to login, org is not active.");
		}

		// Make sure the org is not blocked.
		if (this.verifyBlocked && org.isBlocked()) {
			throw new OrgBlockedException("Unable to login, org is blocked.");
		}

		// Inactive users can login. Must set active in case it is inactive before permission check or it will fail.
		final boolean wasInactive = !this.user.isActive();

		// If they are inactive then save since they are now active. Login allows reactivate.
		if (wasInactive) {
			try {
				CoreBridge.run(new UserActivateCmd(this.user.getUserId()));
			} catch (CommandException e) {
				throw new DebugRuntimeException("Unexpected error while activating a user: " + this.user, e);
			}
		}

		// Logging in, so lets update the user history.
		this.addUserHistory(this.user, "PRO Core");
		log.info(" PRO Core login: " + this.user);

		return this.user;
	}

	/**
	 * Add a new user history entry.
	 * 
	 * @param user the user
	 * @param appCode the application app code
	 */
	private void addUserHistory(User user, String appCode) {
		try {
			// ParamItem pi = ParamItem.getDataProvider().findByParamNameAndName(Param.PARAM_APP_CODES, appCode);

			// TODO Fix once we can change the structure of UserHistory; appCodes are going away
			// ParamItem pi = this.db.find(new ParamItemFindByParamAndName(Param.PARAM_APP_CODES, appCode));
			// UserHistory history = new UserHistory();
			// history.setUserId(user.getUserId().intValue());
			// history.setLoginDate(new Date());
			// history.setLoginAppParamItem(pi);
			// UserHistory.getDataProvider().save(history);
			// user.setUserHistoryId(history.getUserHistoryId());
		} catch (DataProviderException dpe) {
			IgnoredException ee = new IgnoredException("Unable to add user history. e=" + dpe.getMessage(), dpe,
					new Object[] { user, "app=" + appCode });
			log.warn(ee.getMessage(), ee);
		} catch (Exception e) {
			IgnoredException ee = new IgnoredException("Unable to add user history. e=" + e.getMessage(), e, new Object[] {
					user, "app=" + appCode });
			log.warn(ee.getMessage(), ee);
		}
	}

}
