/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.auth;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.code42.auth.AuthTokenUtils.AuthTokenState;
import com.code42.utils.LangUtils;

/**
 * Class to hold the auth token information used to authenticate REST requests.
 */
public class AuthToken implements Serializable {

	private static final long serialVersionUID = -7633831862279406635L;

	private final int userId;
	private final int realUserId;
	private List<IPermission> permissions;
	private AuthTokenState state = null;
	private long lastTouched; // the time at which this token was last MEANINGFULLY touched by a user REST request
	private long lastUpdated; // the time at which this token was last updated in the space, regardless of state

	public AuthToken(int realUserId, int userId) {
		this.userId = userId;
		this.realUserId = realUserId;
		this.state = AuthTokenState.ACTIVE;
		this.lastTouched = System.currentTimeMillis();
		this.lastUpdated = this.lastTouched;
	}

	/**
	 * Construct an AuthToken from the values stored on the given {@link StoredAuthToken}
	 * 
	 * @param authToken token to pull values from for constructing this AuthToken.
	 */
	public AuthToken(final StoredAuthToken authToken) {
		this(authToken.getRealUserId(), authToken.getUserId());
		this.lastTouched = authToken.getLastTouchedDate().getTime();
		this.lastUpdated = authToken.getLastUpdatedDate().getTime();
		this.permissions = new ArrayList<IPermission>(authToken.getPermissions());

		AuthTokenUtils.updateState(this);
	}

	public int getUserId() {
		return this.userId;
	}

	public int getRealUserId() {
		return this.realUserId;
	}

	public boolean isActive() {
		return this.state == AuthTokenState.ACTIVE;
	}

	public boolean isZombie() {
		return this.state == AuthTokenState.ZOMBIE;
	}

	public boolean isExpired() {
		return this.state == AuthTokenState.EXPIRED;
	}

	public List<IPermission> getPermissions() {
		return this.permissions;
	}

	public void setPermissions(List<IPermission> permissions) {
		this.permissions = permissions;
	}

	/**
	 * Refresh the object; this does not necessarily result in the token being updated in the space.
	 */
	public void refresh() {
		if (this.isActive()) {
			this.lastTouched = System.currentTimeMillis();
		}
	}

	/**
	 * Mark the token as updated in the space; only used when the token is placed in the space and controls how often the
	 * token is updated in the space.
	 */
	public void updated() {
		this.lastUpdated = System.currentTimeMillis();
	}

	/**
	 * Copy the state and timestamps from the given token to this one.
	 * 
	 * @param token
	 */
	public void updateFrom(AuthToken token) {
		if (token != null) {
			this.lastTouched = token.lastTouched;
			this.lastUpdated = token.lastUpdated;
			this.state = token.state;
			this.permissions = token.permissions;
		}
	}

	public long getLastTouched() {
		return this.lastTouched;
	}

	public long getLastUpdated() {
		return this.lastUpdated;
	}

	/**
	 * Set the state of this token; adjust timestamps as necessary.
	 * 
	 * <code>
	 * 	- force an update into the space
	 *  - ensure that future math regarding the last "touch" time will work, if expiring
	 * </code>
	 * 
	 * @param state
	 */
	public void setState(AuthTokenState state) {
		this.state = state;

		this.lastUpdated = 0; // The state has changed; force a space update
		if (state == AuthTokenState.EXPIRED) {
			this.lastTouched = 0;
		}
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("AuthToken [");
		builder.append("userId=").append(this.userId);
		builder.append(", realUserId=").append(this.realUserId);
		builder.append(", state=").append(this.state);
		if (LangUtils.hasElements(this.permissions)) {
			builder.append(", permissions=[");
			for (IPermission permission : this.permissions) {
				builder.append(permission.getValue()).append(",");
			}
			builder.append("]");
		}
		builder.append(", lastTouched=").append(this.lastTouched);
		builder.append(", lastUpdated=").append(this.lastUpdated);
		builder.append("]");
		return builder.toString();
	}

}
