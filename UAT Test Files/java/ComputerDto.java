package com.code42.computer;

import java.util.Date;
import java.util.List;

import com.backup42.alerts.AlertInfo;
import com.backup42.common.ComputerType;
import com.code42.backup.SecurityKeyType;
import com.code42.org.OrgSso;
import com.code42.server.destination.Destination;
import com.code42.user.UserSso;

/**
 * Computer data transfer object
 */
public class ComputerDto {

	private Computer computer;
	private OrgSso org;
	private UserSso user;
	private Config settings;

	// Additional information that can be added to a computer
	/** null means destinations were not requested. Empty means none were found */
	List<ComputerBackupUsageDto> backupUsage;

	/** null means the authority destination was not requested. Empty means none were found */
	AuthorityTargetUsageDto authority;

	private SecurityKeyType securityKeyType;
	private List<Destination> availableDestinations;

	public ComputerDto(Computer c, UserSso user, OrgSso org) {
		this.computer = c;
		this.org = org;
		this.user = user;
	}

	public Long getComputerId() {
		return this.computer.getComputerId();
	}

	public Long getParentComputerId() {
		return this.computer.getParentComputerId();
	}

	public boolean isChild() {
		return this.computer.isChild();
	}

	public String getAddress() {
		return this.computer.getAddress();
	}

	public String getRemoteAddress() {
		return this.computer.getRemoteAddress();
	}

	public long getGuid() {
		return this.computer.getGuid();
	}

	public ComputerType getComputerType() {
		return this.computer.getType();
	}

	public Long getAuthDate() {
		return this.computer.getAuthDate();
	}

	public UserSso getUser() {
		return this.user;
	}

	public int getUserId() {
		return this.computer.getUserId();
	}

	public String getUserUid() {
		return this.user.getUserUid();
	}

	public OrgSso getOrgSso() {
		return this.org;
	}

	public Integer getOrgId() {
		return this.org.getOrgId();
	}

	public String getOrgUid() {
		return this.org.getOrgUid();
	}

	public Date getCreationDate() {
		return this.computer.getCreationDate();
	}

	public Date getModificationDate() {
		return this.computer.getModificationDate();
	}

	public boolean getActive() {
		return this.computer.getActive();
	}

	public boolean getBlocked() {
		return this.computer.getBlocked() || this.user.isBlocked() || this.org.isBlocked();
	}

	public String getName() {
		return this.computer.getName();
	}

	public Date getLoginDate() {
		return this.computer.getLoginDate();
	}

	public Date getLastConnected() {
		return this.computer.getLastConnected();
	}

	public AlertInfo getAlertInfo() {
		return this.computer.getAlertInfo();
	}

	public int getAlertState() {
		return this.computer.getAlertState();
	}

	public String getDisplayVersion() {
		return this.computer.getDisplayVersion();
	}

	public Long getVersion() {
		return this.computer.getVersion();
	}

	public String getProductVersion() {
		return this.computer.getProductVersion();
	}

	public String getOsName() {
		return this.computer.getOsName();
	}

	public String getOsVersion() {
		return this.computer.getOsVersion();
	}

	public String getOsArch() {
		return this.computer.getOsArch();
	}

	public String getJavaVersion() {
		return this.computer.getJavaVersion();
	}

	public String getBackupCode() {
		return this.computer.getBackupCode();
	}

	public String getDataKeyChecksum() {
		return this.computer.getDataKeyChecksum();
	}

	public String getLoginHash() {
		return this.computer.getLoginHash();
	}

	public String getTimeZone() {
		return this.computer.getTimeZone();
	}

	public Integer getDuplicateIdentityCount() {
		return this.computer.getDuplicateIdentityCount();
	}

	public String getStatus() {
		return this.computer.getStatus();
	}

	public List<ComputerBackupUsageDto> getDestinations() {
		return this.backupUsage;
	}

	public Computer getComputer() {
		return this.computer;
	}

	public void setSettings(Config config) {
		this.settings = config;
	}

	public Config getSettings() {
		return this.settings;
	}

	public void setSecurityKeyType(SecurityKeyType securityKeyType) {
		this.securityKeyType = securityKeyType;
	}

	public SecurityKeyType getSecurityKeyType() {
		return this.securityKeyType;
	}

	public AuthorityTargetUsageDto getAuthority() {
		return this.authority;
	}

	public void setAuthority(AuthorityTargetUsageDto authority) {
		this.authority = authority;
	}

	public void setAvailableDestinations(List<Destination> availableDestinations) {
		this.availableDestinations = availableDestinations;
	}

	public List<Destination> getAvailableDestinations() {
		return this.availableDestinations;
	}
}
