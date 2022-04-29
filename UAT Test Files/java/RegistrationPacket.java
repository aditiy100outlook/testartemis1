package com.code42.activation;

import com.backup42.common.OrgType;

/**
 * Info needed to attempt a registration.
 * 
 */
public class RegistrationPacket {

	private String username;
	private long guid;

	private String password;
	private String ssoAuthToken; // this.msg.getSsoAuthToken()

	private String firstName;
	private String lastName;

	private OrgType clientOrgType;
	private String registrationKey;
	private String appCode;

	private RegistrationPacket() {
		super();
	}

	public String getUsername() {
		return this.username;
	}

	public long getGuid() {
		return this.guid;
	}

	public String getPassword() {
		return this.password;
	}

	public String getSsoAuthToken() {
		return this.ssoAuthToken;
	}

	public String getFirstName() {
		return this.firstName;
	}

	public String getLastName() {
		return this.lastName;
	}

	public OrgType getClientOrgType() {
		return this.clientOrgType;
	}

	public String getRegistrationKey() {
		return this.registrationKey;
	}

	public String getAppCode() {
		return this.appCode;
	}

	/**
	 * Build an appropriate registration packet.
	 * 
	 */
	public static class RegistrationPacketBuilder {

		private final RegistrationPacket r = new RegistrationPacket();

		public RegistrationPacketBuilder username(String username) {
			this.r.username = username.toLowerCase().trim();
			return this;
		}

		public RegistrationPacketBuilder guid(long guid) {
			this.r.guid = guid;
			return this;
		}

		public RegistrationPacketBuilder password(String password) {
			this.r.password = password;
			return this;
		}

		public RegistrationPacketBuilder ssoAuthToken(String ssoAuthToken) {
			this.r.ssoAuthToken = ssoAuthToken;
			return this;
		}

		public RegistrationPacketBuilder firstName(String firstName) {
			this.r.firstName = firstName;
			return this;
		}

		public RegistrationPacketBuilder lastName(String lastName) {
			this.r.lastName = lastName;
			return this;
		}

		public RegistrationPacketBuilder clientOrgType(OrgType clientOrgType) {
			this.r.clientOrgType = clientOrgType;
			return this;
		}

		public RegistrationPacketBuilder registrationKey(String registrationKey) {
			this.r.registrationKey = registrationKey;
			return this;
		}

		public RegistrationPacketBuilder appCode(String appCode) {
			this.r.appCode = appCode;
			return this;
		}

		public RegistrationPacket build() {
			return this.r;
		}
	}
}
