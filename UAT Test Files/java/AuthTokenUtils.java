/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.auth;

import java.io.Serializable;

import com.code42.utils.SystemProperties;
import com.code42.utils.Time;

/**
 * Class to hold the auth token information used to authenticate REST requests. The token will degrade to a ZOMBIE state
 * after the timeout interval has been reached. User-initiated REST requests are considered "MEANINGFULL" and will
 * result in the timeout interval being refreshed.
 * 
 * If the dashboard timeout is set to true, the dashboard will timeout along with the session, and the so-called
 * "zombie" state will never happen.
 * 
 * NOTE: All properties are refreshed from the system on a 5 minute interval.
 */
public class AuthTokenUtils implements Serializable {

	private static final long serialVersionUID = -7633831862279406635L;

	// Properties
	protected static final String P_AT_TIMEOUT_INTERVAL = "c42.auth.token.timeout";
	protected static final String P_AT_DASHBOARD_TIMEOUT = "c42.auth.token.dashboard.timeout";
	protected static final String P_AT_SPACE_UPDATE_INTERVAL = "c42.auth.token.space.update.interval";

	// Defaults
	private static final long D_AT_TIMEOUT_INTERVAL = Time.MINUTE * 30;
	private static final boolean D_AT_DASHBOARD_TIMEOUT = false;
	private static final long D_AT_SPACE_UPDATE_INTERVAL = Time.MINUTE * 2;

	// Static Processing Properties
	private static long atTimeoutInterval;
	private static boolean atDashboardTimeout;
	private static long atSpaceUpdateInterval;
	protected static long propertyRefreshInterval = Time.MINUTE * 5;
	private static long propertyLastUpdatedTimestamp = 0L;

	/**
	 * <code>
	 * 	ACTIVE	: the normal state for a session in active use
	 * 	ZOMBIE	: a session that has degraded based on timeouts; useful only for maintain a dashboard
	 *  EXPIRED	: a session that has expired and is useless for any activity; it will shortly be removed
	 * </code>
	 */
	public enum AuthTokenState {
		ACTIVE, ZOMBIE, EXPIRED
	}

	/**
	 * Refresh the token
	 */
	public static void reanimate(AuthToken token) {
		if (token != null) {
			token.setState(AuthTokenState.ACTIVE);
			token.refresh();
		}
	}

	/**
	 * Update the state of the token based on the last time it was meaningfully touched and the config parameters.
	 * 
	 * The token will do one of three things: <code>
	 * 	- stay active, if it has not yet reached its expiration time
	 * 	- move to zombie state, if it has expired, but dashboards are allowed to continue functioning
	 * 	- move to expired state, if it has expired, but dashboards are NOT allowed to continue functioning
	 * </code>
	 * 
	 * @param token
	 */
	public static void updateState(AuthToken token) {

		if (token != null && token.isActive()) {
			long now = System.currentTimeMillis();

			if (now - token.getLastTouched() > getAuthTokenTimeoutInterval()) {

				// The token has expired; determine whether it's to become a zombie or die completely
				if (isAuthTokenDashboardTimeout()) {
					token.setState(AuthTokenState.EXPIRED);
				} else {
					token.setState(AuthTokenState.ZOMBIE);
				}
			}
		}

	}

	/**
	 * Determine whether or not the token is due to be updated in the space, based on the config parameter.
	 * 
	 * @param token
	 * @return boolean true if it should be updated; false otherwise
	 */
	public static boolean isUpdateable(AuthToken token, boolean userInitiated) {

		boolean isUpdateable = false;
		if (token != null) {
			long now = System.currentTimeMillis();
			if (now - token.getLastUpdated() > getAuthTokenSpaceUpdateInterval()) {
				isUpdateable = true;
			} else if (userInitiated && token.isActive()
					&& (now - token.getLastTouched() > getAuthTokenSpaceUpdateInterval())) {
				isUpdateable = true;
			}
		}

		return isUpdateable;

	}

	// ////////////////////////////////
	// HELPER METHODS
	// ////////////////////////////////

	/**
	 * Update the necessary properties, regardless of timing
	 */
	protected static void updateProps() {
		propertyLastUpdatedTimestamp = System.currentTimeMillis();
		atTimeoutInterval = SystemProperties.getOptionalLong(P_AT_TIMEOUT_INTERVAL, D_AT_TIMEOUT_INTERVAL);
		atDashboardTimeout = SystemProperties.getOptionalBoolean(P_AT_DASHBOARD_TIMEOUT, D_AT_DASHBOARD_TIMEOUT);
		atSpaceUpdateInterval = SystemProperties.getOptionalLong(P_AT_SPACE_UPDATE_INTERVAL, D_AT_SPACE_UPDATE_INTERVAL);
	}

	/**
	 * Retrieve the property for auth token timeouts.
	 * 
	 * As a side-effect of this method, all necessary properties are refreshed from the System every five minutes.
	 * 
	 * @return
	 */
	private static long getAuthTokenTimeoutInterval() {
		long now = System.currentTimeMillis();
		if (now - propertyLastUpdatedTimestamp > propertyRefreshInterval) {
			updateProps();
		}
		return atTimeoutInterval;
	}

	private static boolean isAuthTokenDashboardTimeout() {
		return atDashboardTimeout;
	}

	private static long getAuthTokenSpaceUpdateInterval() {
		return atSpaceUpdateInterval;
	}

}
