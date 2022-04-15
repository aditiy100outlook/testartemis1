package com.code42.activation;

import com.backup42.common.OrgType;
import com.code42.backup.DataKey;
import com.code42.exception.DebugRuntimeException;
import com.code42.utils.LangUtils;

/**
 * Info needed to attempt a Login.
 * 
 */
public class LoginPacket {

	// who are they?
	private String username;
	private Long guid;

	// credentials we will validate; only one of these is required
	private String password;
	private DataKey loginKey; // authenticate device during reconnection (loginViaKey)
	private String ssoAuthToken; // this.msg.getSsoAuthToken()

	// misc payload
	private String appCode; // this.msg.getAppCode()
	private OrgType orgType; // this.msg.getOrgType()

	private LoginPacket() {
		super();
	}

	public String getUsername() {
		return this.username;
	}

	public Long getGuid() {
		return this.guid;
	}

	public String getPassword() {
		return this.password;
	}

	public DataKey getLoginKey() {
		return this.loginKey;
	}

	public String getSsoAuthToken() {
		return this.ssoAuthToken;
	}

	public String getAppCode() {
		return this.appCode;
	}

	public OrgType getOrgType() {
		return this.orgType;
	}

	/**
	 * Build an appropriate login packet.
	 * 
	 */
	public static class LoginPacketBuilder {

		private final LoginPacket p = new LoginPacket();

		public LoginPacketBuilder username(String username) {
			this.p.username = username;
			return this;
		}

		public LoginPacketBuilder guid(long guid) {
			this.p.guid = guid;
			return this;
		}

		public LoginPacketBuilder password(String password) {
			this.p.password = password;
			return this;
		}

		public LoginPacketBuilder loginKey(DataKey key) {
			this.p.loginKey = key;
			return this;
		}

		public LoginPacketBuilder ssoAuthToken(String ssoAuthToken) {
			this.p.ssoAuthToken = ssoAuthToken;
			return this;
		}

		public LoginPacketBuilder appCode(String appCode) {
			this.p.appCode = appCode;
			return this;
		}

		public LoginPacketBuilder orgType(OrgType orgType) {
			this.p.orgType = orgType;
			return this;
		}

		/**
		 * Enforce a few requirements at build-time.
		 */
		private void validate() {

			// both device identifiers are required
			if (!LangUtils.hasValue(this.p.username) || this.p.guid == null) {
				throw new DebugRuntimeException("Both username and guid are required.", this);
			}

			// at least one of the login credentials must be provided
			if (this.p.password == null && this.p.loginKey == null) {
				throw new DebugRuntimeException("Login credentials are required; you must provide either a password or a key.",
						this);
			}
		}

		public LoginPacket build() {
			this.validate();
			return this.p;
		}
	}
}
