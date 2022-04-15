package com.code42.user;

import java.io.Serializable;
import java.util.Date;

import com.code42.core.BuilderException;
import com.code42.core.impl.CoreBridge;

/**
 * Simple non-persistent object representing the basic User information. Suitable for storing in a cache somewhere.
 */
public class UserSso implements Serializable, IUser {

	private static final long serialVersionUID = -1036581220524582321L;

	private final int userId;
	private final String userUid;
	private final String username;
	private final String email;
	private final int orgId;
	private final boolean active;
	private final boolean blocked;
	private final boolean invited;
	private final long maxBytes;
	private final Date creationDate;

	public UserSso(IUser user) {

		this.userId = user.getUserId();
		this.userUid = user.getUserUid();
		this.username = user.getUsername();
		this.email = user.getEmail();
		this.orgId = user.getOrgId();
		this.active = user.isActive();
		this.blocked = user.isBlocked();
		this.invited = user.isInvited();
		this.maxBytes = user.getMaxBytes();
		this.creationDate = user.getCreationDate();
	}

	private UserSso(Builder builder) {

		this.userId = builder.userId;
		this.userUid = builder.userUid;
		this.username = builder.username;
		this.email = builder.email;
		this.orgId = builder.orgId;
		this.active = builder.active;
		this.blocked = builder.blocked;
		this.maxBytes = builder.maxBytes;
		this.creationDate = builder.creationDate;

		/* Copy of the logic from User.isInvited; probably should eventually find it's way to a command somewhere */
		/* Do NOT store the password in the SSO unless you explicitly need it's data! */
		this.invited = (builder.password == null);
	}

	public Integer getUserId() {
		return this.userId;
	}

	public String getUserUid() {
		return this.userUid;
	}

	public String getUsername() {
		return this.username;
	}

	public String getEmail() {
		return this.email;
	}

	public int getOrgId() {
		return this.orgId;
	}

	public boolean isActive() {
		return this.active;
	}

	public boolean isBlocked() {
		return this.blocked;
	}

	public boolean isInvited() {
		return this.invited;
	}

	public long getMaxBytes() {
		return this.maxBytes;
	}

	public Date getCreationDate() {
		return this.creationDate;
	}

	public User toUser() {
		return CoreBridge.runNoException(new UserFindByIdCmd(this.userId));
	}

	@Override
	public String toString() {
		StringBuilder buffer = new StringBuilder();
		buffer.append("UserSso[");
		buffer.append("userId=").append(this.userId);
		buffer.append(",userUid=").append(this.userUid);
		buffer.append(", orgId=").append(this.orgId);
		buffer.append(", username=").append(this.username);
		buffer.append(", email=").append(this.email);
		buffer.append(", active=").append(this.active);
		buffer.append(", blocked=").append(this.blocked);
		buffer.append(", maxBytes=").append(this.maxBytes);
		buffer.append(", creationDate=").append(this.creationDate);
		buffer.append("]");
		return buffer.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((this.userUid == null) ? 0 : this.userUid.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (this.getClass() != obj.getClass()) {
			return false;
		}
		UserSso other = (UserSso) obj;
		if (this.userUid == null) {
			if (other.userUid != null) {
				return false;
			}
		} else if (!this.userUid.equals(other.userUid)) {
			return false;
		}
		return true;
	}

	public static class Builder {

		private Integer userId;
		private String userUid;
		private String username;
		private String email;
		private Integer orgId;
		private Boolean active;
		private Boolean blocked;
		private Long maxBytes;
		private Date creationDate;
		private String password;

		public Builder userId(int userId) {
			this.userId = userId;
			return this;
		}

		public Builder userUid(String userUid) {
			this.userUid = userUid;
			return this;
		}

		public Builder username(String username) {
			this.username = username;
			return this;
		}

		public Builder email(String email) {
			this.email = email;
			return this;
		}

		public Builder orgId(int orgId) {
			this.orgId = orgId;
			return this;
		}

		public Builder active(boolean active) {
			this.active = active;
			return this;
		}

		public Builder blocked(boolean blocked) {
			this.blocked = blocked;
			return this;
		}

		public Builder maxBytes(long maxBytes) {
			this.maxBytes = maxBytes;
			return this;
		}

		public Builder creationDate(Date creationDate) {
			this.creationDate = creationDate;
			return this;
		}

		/*
		 * Required for the invited check in the OrgSso constructor.
		 * 
		 * Do NOT store this value directly in the OrgSso unless you explicitly need it! The last thing we need to do is
		 * start making passwords available in SSO objects, even if they're hashed.
		 */
		public Builder password(String password) {
			this.password = password;
			return this;
		}

		public void validate() throws BuilderException {
			if (this.userId == null) {
				throw new BuilderException("User Id must be set");
			}
			if (this.userUid == null) {
				throw new BuilderException("User UID must be set");
			}
			if (this.username == null) {
				throw new BuilderException("Username must be set");
			}
			if (this.orgId == null) {
				throw new BuilderException("Org Id must be set");
			}
			if (this.active == null) {
				throw new BuilderException("Active status must be set");
			}
			if (this.blocked == null) {
				throw new BuilderException("Blocked status must be set");
			}
			if (this.maxBytes == null) {
				throw new BuilderException("Max Bytes must be set");
			}
			if (this.creationDate == null) {
				throw new BuilderException("Creation Date must be set");
			}
		}

		public UserSso build() throws BuilderException {
			this.validate();
			return new UserSso(this);
		}
	}
}
