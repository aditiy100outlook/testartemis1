/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.org;

import java.util.List;

import com.backup42.common.config.ServiceConfig;
import com.code42.computer.Config;
import com.code42.config.ConfigFindByOrgIdCmd;
import com.code42.core.CommandException;
import com.code42.core.UnsupportedRequestException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.ldap.OrgAuthenticatorMapping;
import com.code42.ldap.OrgLdapMappingFindByOrgCmd;
import com.code42.org.OrgSettingsInfoFindByOrgCmd.Builder;
import com.code42.org.destination.DestinationFindAvailableByOrgCmd;
import com.code42.radius.OrgRadiusMappingFindByOrgCmd;
import com.code42.server.destination.Destination;
import com.code42.server.destination.DestinationFindAllByClusterCmd;
import com.code42.ssoauth.OrgSsoAuthMappingFindByOrgCmd;

public class OrgSettingsDtoFindByOrgCmd extends DBCmd<OrgSettingsDto> {

	private final OrgDto org;

	public OrgSettingsDtoFindByOrgCmd(OrgDto org) {
		this.org = org;
	}

	@Override
	public OrgSettingsDto exec(CoreSession session) throws CommandException {

		// Authorization
		this.runtime.run(new IsOrgManageableCmd(this.org.getOrgId(), C42PermissionApp.Org.READ), session);

		try {

			// OrgSettingsInfo
			Builder builder = new OrgSettingsInfoFindByOrgCmd.Builder();
			builder.orgId(this.org.getOrgId());
			OrgSettingsInfo osi = this.runtime.run(builder.build(), session);

			// OrgNotifySettings
			OrgNotifySettingsFindByOrgCmd ocmd = new OrgNotifySettingsFindByOrgCmd.Builder(this.org.getOrgId()).build();
			OrgNotifySettings ons = this.runtime.run(ocmd, session);

			OrgNotifySettingsFindByOrgCmd scmd = new OrgNotifySettingsFindByOrgCmd.Builder(this.org.getOrgId())
					.findInheritedSettings().build();
			OrgNotifySettings sns = this.run(scmd, this.auth.getAdminSession());

			// Config
			Config c = this.runtime.run(new ConfigFindByOrgIdCmd(this.org.getOrgId()), session);
			ServiceConfig config = c.toServiceConfig();

			OrgAuthenticatorMapping orgLdapMapping = this.run(new OrgLdapMappingFindByOrgCmd(this.org.getOrgId()), session);
			OrgAuthenticatorMapping orgRadiusMapping = this.run(new OrgRadiusMappingFindByOrgCmd(this.org.getOrgId()),
					session);
			OrgAuthenticatorMapping orgSsoMapping = this.run(new OrgSsoAuthMappingFindByOrgCmd(this.org.getOrgId()), session);

			final List<Destination> myOrgDestinations = this.run(new DestinationFindAvailableByOrgCmd(this.org.getOrgId()),
					session);
			final List<Destination> inheritedDestinations = this.run(new DestinationFindAvailableByOrgCmd(
					this.org.getOrgId(), true), session);

			final boolean includeProviderDestinations = (this.org.getMasterGuid() == null);
			final List<Destination> allDestinations = this.run(
					new DestinationFindAllByClusterCmd(includeProviderDestinations), this.auth.getAdminSession());

			return new OrgSettingsDto(osi, ons, sns, config, orgLdapMapping, orgRadiusMapping, orgSsoMapping,
					myOrgDestinations, inheritedDestinations, allDestinations);
		} catch (UnsupportedRequestException e) {
			throw e;
		} catch (Exception e) {
			throw new CommandException("Unable to get settings info for org: " + this.org.getOrgId(), e);
		}
	}
}
