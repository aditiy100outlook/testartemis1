/*
 * Created on Apr 14, 2011 by Tony Lindquist <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.org;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.backup42.common.config.ServiceConfig;
import com.code42.ldap.OrgAuthenticatorMapping;
import com.code42.server.destination.Destination;
import com.code42.utils.InheritableValue;
import com.code42.utils.LangUtils;
import com.code42.utils.Weekday;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

/**
 * This class is a transform for org setting information; it's a composite of OrgSettingsInfo and OrgNotifySettings.
 */
public class OrgSettingsDto {

	private final OrgSettingsInfo osi;
	private final OrgNotifySettings ons;
	private final ServiceConfig config;
	private final OrgAuthenticatorMapping orgLdapMapping;
	private final OrgAuthenticatorMapping orgRadiusMapping;
	private final OrgAuthenticatorMapping orgSsoMapping;
	// XXX: this method of settings inheritance - loading a "default" bean - should go away in favor of
	// the more modern InheritableValue approach. http://bugz.c42/bugzilla/show_bug.cgi?id=1019
	private final OrgNotifySettings inheritedNotifySettings;
	private final OrgNotifySettings currentNotifySettings;
	private final List<Destination> allDestinations;
	private final List<Destination> myOrgDestinations;
	private final List<Destination> inheritedDestinations;

	public OrgSettingsDto(OrgSettingsInfo osi, OrgNotifySettings ons, OrgNotifySettings inheritedNotifySettings,
			ServiceConfig config, OrgAuthenticatorMapping orgLdapMapping, OrgAuthenticatorMapping orgRadiusMapping,
			OrgAuthenticatorMapping orgSsoMapping, List<Destination> myOrgDestinations,
			List<Destination> inheritedDestinations, List<Destination> allDestinations) {
		this.osi = osi;
		this.ons = ons;
		this.config = config;
		this.orgLdapMapping = orgLdapMapping;
		this.orgRadiusMapping = orgRadiusMapping;
		this.orgSsoMapping = orgSsoMapping;

		this.inheritedNotifySettings = inheritedNotifySettings;
		this.myOrgDestinations = myOrgDestinations;
		this.allDestinations = allDestinations;
		this.inheritedDestinations = inheritedDestinations;
		this.currentNotifySettings = this.isUseReportingDefaults() ? this.inheritedNotifySettings : this.ons;
	}

	// ///////////////////////////////////////////////////////////
	// Pass through getters for the composite objects
	// ///////////////////////////////////////////////////////////

	public Integer getOrgId() {
		return this.osi.getOrgId();
	}

	public int getBackupWarningDays() {
		return this.currentNotifySettings.getBackupWarningDays();
	}

	public int getBackupWarningDaysInheritedValue() {
		return this.inheritedNotifySettings.getBackupWarningDays();
	}

	public int getBackupCriticalDays() {
		return this.currentNotifySettings.getBackupCriticalDays();
	}

	public int getBackupCriticalDaysInheritedValue() {
		return this.inheritedNotifySettings.getBackupCriticalDays();
	}

	public boolean getSendReportsToOrgManagers() {
		return this.currentNotifySettings.isSendReportsToOrgManagers();
	}

	public boolean getSendReportsToOrgManagersInheritedValue() {
		return this.inheritedNotifySettings.isSendReportsToOrgManagers();
	}

	public Collection<String> getAdditionalReportEmailAddresses() {
		String[] ary = LangUtils.split(this.currentNotifySettings.getAdditionalReportEmailAddresses(), ",");
		if (ary == null || ary.length == 0) {
			return Collections.emptyList();
		}
		return Arrays.asList(ary);
	}

	public String getAdditionalReportEmailAddressesString() {
		return this.currentNotifySettings.getAdditionalReportEmailAddresses();
	}

	public Collection<String> getAdditionalReportEmailAddressesInheritedValue() {
		String[] ary = LangUtils.split(this.inheritedNotifySettings.getAdditionalReportEmailAddresses(), ",");
		if (ary == null || ary.length == 0) {
			return Collections.emptyList();
		}
		return Arrays.asList(ary);
	}

	public String getAdditionalReportEmailAddressesStringInheritedValue() {
		return this.inheritedNotifySettings.getAdditionalReportEmailAddresses();
	}

	public String getReportSchedule() {
		return Weekday.toWeekString(this.currentNotifySettings.getReportSchedule());
	}

	public String getReportScheduleInheritedValue() {
		return Weekday.toWeekString(this.inheritedNotifySettings.getReportSchedule());
	}

	public Date getReportLastSent() {
		return this.ons.getReportsLastSent();
	}

	public boolean getAutoLoginToDesktop() {
		return this.config.serviceUI.autoLogin.getValue();
	}

	public Boolean getUsernameIsAnEmail() {
		return this.osi.getUsernameIsAnEmail();
	}

	public Boolean getUsernameIsAnEmailInheritedValue() {
		return this.osi.getUsernameIsAnEmailWrapper().getInheritedValue();
	}

	public boolean isUsernameIsAnEmailInherited() {
		return this.osi.isUsernameIsAnEmailInherited();
	}

	public Collection<Integer> getLdapServerIds() {
		return this.orgLdapMapping.getAuthenticatorIds();
	}

	public Collection<Integer> getInheritedLdapServerIds() {
		return this.orgLdapMapping.getInheritedAuthenticatorIds();
	}

	public Collection<Integer> getRadiusServerIds() {
		return this.orgRadiusMapping.getAuthenticatorIds();
	}

	public Collection<Integer> getInheritedRadiusServerIds() {
		return this.orgRadiusMapping.getInheritedAuthenticatorIds();
	}

	public Collection<Integer> getSsoAuthIds() {
		return this.orgSsoMapping.getAuthenticatorIds();
	}

	public Collection<Integer> getInheritedSsoAuthIds() {
		return this.orgSsoMapping.getInheritedAuthenticatorIds();
	}

	public Boolean getAutoOfferSelf() {
		return this.osi.getAutoOfferSelf();
	}

	public boolean isAutoOfferSelfInherited() {
		return this.osi.isAutoOfferSelfInherited();
	}

	/**
	 * It's possible for nulls to sneak into the database for org 1, despite our best efforts. Thwart future NPEs by
	 * declaring a default value.
	 */
	private boolean safeBooleanCheck(InheritableValue<Boolean> i, boolean defaultReturnVal) {
		Boolean b = i.getInheritedValue();
		return (b == null) ? defaultReturnVal : b;
	}

	public boolean isAutoOfferSelfInheritedValue() {
		return this.safeBooleanCheck(this.osi.getAutoOfferSelfWrapper(), true);
	}

	public Boolean isAllowLocalFolders() {
		return this.osi.isAllowLocalFolders();
	}

	public boolean isAllowLocalFoldersInherited() {
		return this.osi.isAllowLocalFoldersInherited();
	}

	public boolean isAllowLocalFoldersInheritedValue() {
		return this.safeBooleanCheck(this.osi.getAllowLocalFoldersWrapper(), true);
	}

	public String getSecurityKeyType() {
		return this.osi.getSecurityKeyType().name();
	}

	public String getSecurityKeyTypeInheritedValue() {
		return this.osi.getSecurityKeyTypeWrapper().getInheritedValue().name();
	}

	public boolean isSecurityKeyInherited() {
		return this.osi.isSecurityKeyInherited();
	}

	public Boolean isSecurityKeyLocked() {
		return this.osi.isSecurityKeyLocked();
	}

	public boolean isSecurityKeyLockedByParent() {
		return this.osi.isSecurityKeyLockedByParent();
	}

	public Long getDefaultUserMaxBytes() {
		return this.osi.getDefaultUserMaxBytes();
	}

	public Long getDefaultUserMaxBytesInheritedValue() {
		return this.osi.getDefaultUserMaxBytesWrapper().getInheritedValue();
	}

	public boolean isDefaultUserMaxBytesInherited() {
		return this.osi.isDefaultUserMaxBytesInherited();
	}

	public Integer getDefaultUserMobileQuota() {
		return this.osi.getDefaultUserMobileQuota();
	}

	public Integer getDefaultUserMobileQuotaInheritedValue() {
		return this.osi.getDefaultUserMobileQuotaWrapper().getInheritedValue();
	}

	public boolean isDefaultUserMobileQuotaInherited() {
		return this.osi.isDefaultUserMobileQuotaInherited();
	}

	// stored in megabytes
	public Integer getWebRestoreUserLimit() {
		return this.osi.getWebRestoreUserLimit();
	}

	public Integer getWebRestoreUserLimitInheritedValue() {
		return this.osi.getWebRestoreUserLimitWrapper().getInheritedValue();
	}

	public boolean isWebRestoreUserLimitInherited() {
		return this.osi.isWebRestoreUserLimitInherited();
	}

	// stored in megabytes
	public Integer getWebRestoreAdminLimit() {
		return this.osi.getWebRestoreAdminLimit();
	}

	public Integer getWebRestoreAdminLimitInheritedValue() {
		return this.osi.getWebRestoreAdminLimitWrapper().getInheritedValue();
	}

	public boolean isWebRestoreAdminLimitInherited() {
		return this.osi.isWebRestoreAdminLimitInherited();
	}

	public Integer getArchiveHoldDays() {
		return this.osi.getArchiveHoldDays();
	}

	public Integer getArchiveHoldDaysInheritedValue() {
		return this.osi.getArchiveHoldDaysWrapper().getInheritedValue();
	}

	public boolean isArchiveHoldDaysInherited() {
		return this.osi.isArchiveHoldDaysInherited();
	}

	/**
	 * Return a boolean indicating whether this org should use the server defaults.
	 */
	public boolean isUseReportingDefaults() {
		return this.ons == null ? false : this.ons.isUseReportingDefaults();
	}

	public List<Destination> getDestinations() {
		return this.myOrgDestinations;
	}

	public List<Destination> getInheritedDestinations() {
		return this.inheritedDestinations;
	}

	public List<Destination> getAllDestinations() {
		return this.allDestinations;
	}

	private static List<String> rolesAsList(String roles) {
		if (LangUtils.hasValue(roles)) {
			return Lists.newArrayList(Splitter.on(',').trimResults().split(roles));
		} else {
			return Collections.EMPTY_LIST;
		}
	}

	public List<String> getDefaultRoles() {
		return rolesAsList(this.osi.getDefaultRoles());
	}

	public List<String> getDefaultRolesInheritedValue() {
		return rolesAsList(this.osi.getDefaultRolesWrapper().getInheritedValue());
	}

	public boolean isDefaultRolesInherited() {
		return this.osi.isDefaultRolesInherited();
	}

}
