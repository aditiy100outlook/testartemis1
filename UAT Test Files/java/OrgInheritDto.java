package com.code42.org;

/**
 * Information about which orgs are contributing inherited data.
 * 
 * Nulls for ids and names indicate that the root org is the source of inheritance.
 */
public class OrgInheritDto {

	// The org that is using the inherited settings
	private final int orgId;

	// The org that's providing settings in t_org
	private Integer orgIdProvidingOrg;
	private String orgNameProvidingOrg;

	// The org that's providing destination config (see t_org => inherit_destinations)
	private Integer orgIdProvidingDestinations;
	private String orgNameProvidingDestinations;

	// The org that's providing device defaults
	private Integer orgIdProvidingDeviceDefaults;
	private String orgNameProvidingDeviceDefaults;

	OrgInheritDto(int orgId) {
		this.orgId = orgId;
	}

	public Integer getOrgIdProvidingOrg() {
		return this.orgIdProvidingOrg;
	}

	public void setOrgIdProvidingOrg(Integer orgIdProvidingOrg) {
		this.orgIdProvidingOrg = orgIdProvidingOrg;
	}

	public String getOrgNameProvidingOrg() {
		return this.orgNameProvidingOrg;
	}

	public void setOrgNameProvidingOrg(String orgNameProvidingOrg) {
		this.orgNameProvidingOrg = orgNameProvidingOrg;
	}

	public Integer getOrgIdProvidingDestinations() {
		return this.orgIdProvidingDestinations;
	}

	public void setOrgIdProvidingDestinations(Integer orgIdProvidingDestinations) {
		this.orgIdProvidingDestinations = orgIdProvidingDestinations;
	}

	public String getOrgNameProvidingDestinations() {
		return this.orgNameProvidingDestinations;
	}

	public void setOrgNameProvidingDestinations(String orgNameProvidingDestinations) {
		this.orgNameProvidingDestinations = orgNameProvidingDestinations;
	}

	public Integer getOrgIdProvidingDeviceDefaults() {
		return this.orgIdProvidingDeviceDefaults;
	}

	public void setOrgIdProvidingDeviceDefaults(Integer orgIdProvidingDeviceDefaults) {
		this.orgIdProvidingDeviceDefaults = orgIdProvidingDeviceDefaults;
	}

	public String getOrgNameProvidingDeviceDefaults() {
		return this.orgNameProvidingDeviceDefaults;
	}

	public void setOrgNameProvidingDeviceDefaults(String orgNameProvidingDeviceDefaults) {
		this.orgNameProvidingDeviceDefaults = orgNameProvidingDeviceDefaults;
	}

	public int getOrgId() {
		return this.orgId;
	}

}
