package com.code42.org;

import java.util.Date;
import java.util.List;

import com.backup42.common.OrgType;
import com.code42.address.Address;
import com.code42.address.AddressDto;
import com.code42.computer.Config;
import com.code42.core.hierarchy.AggregateHierarchyStats;
import com.code42.server.destination.Destination;
import com.code42.utils.Pair;
import com.google.common.annotations.VisibleForTesting;

/**
 * A Data Transfer Object. However, this is intentionally not serializable because we don't want it being inadvertently
 * added to the "space".
 */
public class OrgDto implements IOrgInfo {

	private BackupOrg org;
	// orgInfo contains redundant information, given the presence of a BackupOrg reference. However, this object
	// is not populated unless requested for performance reasons, while the BackupOrg object is always present.
	private OrgInfo orgInfo;
	private OrgInheritDto orgInheritDto;
	private AddressDto address;
	protected Integer computerCount;
	protected Integer backupComputerCount;
	protected Integer userCount;
	protected Integer licensedUserCount;
	protected Integer orgCount;
	protected OrgAlertsDto alertCounts;
	protected List<OrgBackupUsageDto> backupUsage;
	protected Long coldBytes;
	private Config deviceDefaults;
	private OrgSettingsDto orgSettingsDto;
	private List<Destination> destinations;
	private List<AggregateHierarchyStats> hierarchyCounts;
	private List<AggregateHierarchyStats> configInheritanceCounts;
	private List<Pair<Long, Long>> archiveSizes;

	public OrgDto(BackupOrg org) {
		this.org = org;
	}

	public OrgDto(BackupOrg org, Address address) {
		this.org = org;
		if (address != null) {
			this.address = new AddressDto(address);
		}
	}

	public Integer getOrgId() {
		return this.org.getOrgId();
	}

	public String getOrgUid() {
		return this.org.getOrgUid();
	}

	public String getOrgName() {
		return this.org.getOrgName();
	}

	public Integer getParentOrgId() {
		return this.org.getParentOrgId();
	}

	public boolean isActive() {
		return this.org.isActive();
	}

	public boolean isDestinationsInherited() {
		return this.org.getInheritDestinations();
	}

	public boolean isBlocked() {
		return this.org.isBlocked();
	}

	public boolean isCustomConfig() {
		return this.org.getCustomConfig();
	}

	public String getStatus() {
		return this.org.getStatus();
	}

	public OrgType getType() {
		return this.org.getType();
	}

	@Deprecated
	public String getExternalId() {
		return this.org.getOrgUid();
	}

	public Long getMasterGuid() {
		return this.org.getMasterGuid();
	}

	public String getRegistrationKey() {
		return this.org.getRegistrationKey();
	}

	public Date getCreationDate() {
		return this.org.getCreationDate();
	}

	public Date getModificationDate() {
		return this.org.getModificationDate();
	}

	public Integer getMaxSeats() {
		return this.orgInfo != null ? this.orgInfo.getMaxSeats() : this.org.getMaxSeats();
	}

	public Integer getMaxSeatsInheritedValue() {
		return this.orgInfo != null ? this.orgInfo.getMaxSeatsWrapper().getInheritedValue() : null;
	}

	public boolean isMaxSeatsInherited() {
		return this.orgInfo != null ? this.orgInfo.isMaxSeatsInherited() : false;
	}

	public Long getMaxBytes() {
		return this.orgInfo != null ? this.orgInfo.getMaxBytes() : this.org.getMaxBytes();
	}

	public Long getMaxBytesInheritedValue() {
		return this.orgInfo != null ? this.orgInfo.getMaxBytesWrapper().getInheritedValue() : null;
	}

	public boolean isMaxBytesInherited() {
		return this.orgInfo != null ? this.orgInfo.isMaxBytesInherited() : false;
	}

	public AddressDto getAddress() {
		return this.address;
	}

	public List<OrgBackupUsageDto> getBackupUsage() {
		return this.backupUsage;
	}

	public Integer getComputerCount() {
		return this.computerCount;
	}

	public Integer getBackupComputerCount() {
		return this.backupComputerCount;
	}

	public Integer getUserCount() {
		return this.userCount;
	}

	public Integer getLicensedUserCount() {
		return this.licensedUserCount;
	}

	public Integer getOrgCount() {
		return this.orgCount;
	}

	public OrgAlertsDto getAlertCounts() {
		return this.alertCounts;
	}

	public Long getColdBytes() {
		return this.coldBytes;
	}

	public void setDestinations(List<Destination> destinations) {
		this.destinations = destinations;
	}

	public List<Destination> getDestinations() {
		return this.destinations;
	}

	public void setHierarchyCounts(List<AggregateHierarchyStats> hierarchyCounts) {
		this.hierarchyCounts = hierarchyCounts;
	}

	public Integer getAllOrgCount() {
		return (this.hierarchyCounts != null && this.hierarchyCounts.get(0) != null) ? this.hierarchyCounts.get(0)
				.getOrgCount() : null;
	}

	public Integer getAllDeviceCount() {
		return (this.hierarchyCounts != null && this.hierarchyCounts.get(0) != null) ? this.hierarchyCounts.get(0)
				.getDeviceCount() : null;
	}

	public Integer getInheritedOrgCount() {
		return (this.hierarchyCounts != null && this.hierarchyCounts.get(1) != null) ? this.hierarchyCounts.get(1)
				.getOrgCount() : null;
	}

	public Integer getInheritedDeviceCount() {
		return (this.hierarchyCounts != null && this.hierarchyCounts.get(1) != null) ? this.hierarchyCounts.get(1)
				.getDeviceCount() : null;
	}

	public void setConfigInheritanceCounts(List<AggregateHierarchyStats> counts) {
		this.configInheritanceCounts = counts;
	}

	public Integer getAllConfigOrgCount() {
		return (this.configInheritanceCounts != null && this.configInheritanceCounts.get(0) != null) ? this.configInheritanceCounts
				.get(0).getOrgCount()
				: null;
	}

	public Integer getAllConfigDeviceCount() {
		return (this.configInheritanceCounts != null && this.configInheritanceCounts.get(0) != null) ? this.configInheritanceCounts
				.get(0).getDeviceCount()
				: null;
	}

	public Integer getInheritedConfigOrgCount() {
		return (this.configInheritanceCounts != null && this.configInheritanceCounts.get(1) != null) ? this.configInheritanceCounts
				.get(1).getOrgCount()
				: null;
	}

	public Integer getInheritedConfigDeviceCount() {
		return (this.configInheritanceCounts != null && this.configInheritanceCounts.get(1) != null) ? this.configInheritanceCounts
				.get(1).getDeviceCount()
				: null;
	}

	public void setDeviceDefaults(Config config) {
		this.deviceDefaults = config;
	}

	public Config getDeviceDefaults() {
		return this.deviceDefaults;
	}

	public void setSettings(OrgSettingsDto orgSettingsDto) {
		this.orgSettingsDto = orgSettingsDto;
	}

	public OrgSettingsDto getSettings() {
		return this.orgSettingsDto;
	}

	public void setOrgInfo(OrgInfo orgInfo) {
		this.orgInfo = orgInfo;
	}

	public List<Pair<Long, Long>> getArchiveSizes() {
		return this.archiveSizes;
	}

	public void setArchiveSizes(List<Pair<Long, Long>> archiveSizes) {
		this.archiveSizes = archiveSizes;
	}

	@VisibleForTesting
	protected boolean hasOrgInfo() {
		return this.orgInfo != null;
	}

	public void setOrgInheritDto(OrgInheritDto orgInheritDto) {
		this.orgInheritDto = orgInheritDto;
	}

	public OrgInheritDto getOrgInheritDto() {
		return this.orgInheritDto;
	}
}
