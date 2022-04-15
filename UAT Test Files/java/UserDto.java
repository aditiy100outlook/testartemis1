package com.code42.user;

import java.util.Date;
import java.util.List;

import com.backup42.common.OrgType;
import com.code42.backup.SecurityKeyType;
import com.code42.org.OrgSso;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;

public class UserDto {

	private User user;
	private OrgSso org;
	protected Integer computerCount = null;
	protected UserAlertsDto alertCounts = null;
	protected List<UserBackupUsageDto> backupUsage = null;
	protected List<UserRoleDto> roles = null;
	protected Option<Date> lastLoginDate = None.getInstance(); // null is a valid value so we use Option here
	protected Option<SecurityKeyType> securityKeyType = None.getInstance();
	private Boolean usernameIsAnEmail;
	private String authType = null;

	public UserDto(User user, OrgSso org) {
		this.user = user;
		this.org = org;
	}

	public Integer getUserId() {
		return this.user.getUserId();
	}

	public String getUserUid() {
		return this.user.getUserUid();
	}

	public User getUser() {
		return this.user;
	}

	public boolean isActive() {
		return this.user.isActive();
	}

	public boolean isBlocked() {
		return this.user.isBlocked() || this.org.isBlocked();
	}

	public boolean isInvited() {
		return this.user.getPassword() == null;
	}

	public int getOrgId() {
		return this.user.getOrgId();
	}

	public String getOrgName() {
		return this.org.getOrgName();
	}

	public String getOrgUid() {
		return this.org.getOrgUid();
	}

	public OrgType getOrgType() {
		return this.org.getType();
	}

	public OrgSso getOrg() {
		return this.org;
	}

	public Date getCreationDate() {
		return this.user.getCreationDate();
	}

	public String getUsername() {
		return this.user.getUsername();
	}

	public boolean isEmailPromo() {
		return this.user.isEmailPromo();
	}

	public Boolean isUsernameIsAnEmail() {
		return this.usernameIsAnEmail;
	}

	public void setUsernameIsAnEmail(Boolean usernameIsAnEmail) {
		this.usernameIsAnEmail = usernameIsAnEmail;
	}

	public boolean isPasswordResetRequired() {
		return this.user.isPasswordResetRequired();
	}

	public String getEmail() {
		return this.user.getEmail();
	}

	public String getFirstName() {
		return this.user.getFirstName();
	}

	public String getLastName() {
		return this.user.getLastName();
	}

	public String getDisplayName() {
		return this.user.getDisplayName();
	}

	public String getStatus() {
		return this.user.getStatus();
	}

	public Date getModificationDate() {
		return this.user.getModificationDate();
	}

	public String getFirstLastSearch() {
		return this.user.getFirstLastSearch();
	}

	public String getLastFirstSearch() {
		return this.user.getLastFirstSearch();
	}

	public Integer getComputerCount() {
		return this.computerCount;
	}

	public void setComputerCount(Integer computerCount) {
		this.computerCount = computerCount;
	}

	public UserAlertsDto getAlertCounts() {
		return this.alertCounts;
	}

	public List<UserBackupUsageDto> getDestinations() {
		return this.backupUsage;
	}

	public List<UserRoleDto> getRoles() {
		return this.roles;
	}

	public void setLastLoginDate(Date d) {
		this.lastLoginDate = new Some(d);
	}

	public boolean isLastLoginDateSet() {
		return !(this.lastLoginDate instanceof None);
	}

	public Date getLastLoginDate() {
		if (this.isLastLoginDateSet()) {
			return this.lastLoginDate.get();
		} else {
			return null;
		}
	}

	public Long getQuotaInBytes() {
		return this.user.getMaxBytes();
	}

	public Boolean getUsernameIsAnEmail() {
		return this.usernameIsAnEmail;
	}

	public void setAlertCounts(UserAlertsDto alertCounts) {
		this.alertCounts = alertCounts;
	}

	public void setSecurityKeyType(SecurityKeyType type) {
		this.securityKeyType = new Some(type);
	}

	public boolean isSecurityKeyTypeSet() {
		return !(this.securityKeyType instanceof None);
	}

	public SecurityKeyType getSecurityKeyType() {
		return this.securityKeyType.get();
	}

	/**
	 * @return one of these values: LOCAL, RADIUS, LDAP, or SSO
	 */
	public String getAuthType() {
		return this.authType;
	}

	public void setAuthType(String authType) {
		this.authType = authType;
	}
}
