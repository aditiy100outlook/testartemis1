package com.code42.org;

import com.code42.backup.SecurityKeyType;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.org.OrgSettingsFindSecurityKeyDtoByOrgCmd.SecurityKeyDto;

/**
 * Find security key info for an org, including inherited info.
 * 
 * This specialized command exists so that users (without org permissions) may see whether their security key settings
 * are locked at the org level.
 * 
 * @author mharper
 */
public class OrgSettingsFindSecurityKeyDtoByOrgCmd extends DBCmd<SecurityKeyDto> {

	private final long computerId;
	private final int orgId;

	private OrgSettingsFindSecurityKeyDtoByOrgCmd(long computerId, int orgId) {
		this.computerId = computerId;
		this.orgId = orgId;
	}

	@Override
	public SecurityKeyDto exec(CoreSession session) throws CommandException {
		// this command requires lower permissions than the OrgSettings finder
		this.run(new IsComputerManageableCmd(this.computerId, C42PermissionApp.Computer.READ), session);

		// find the org info with elevated permissions.
		OrgSettingsInfoFindByOrgCmd cmd = (new OrgSettingsInfoFindByOrgCmd.Builder()).orgId(this.orgId).build();
		OrgSettingsInfo osi = this.run(cmd, this.auth.getAdminSession());

		// parse the object into a dto
		return new SecurityKeyDto(osi.getSecurityKeyType(), osi.isSecurityKeyLocked());
	}

	/**
	 * Simple bean containing information about how the org sees its security keys.
	 */
	public static class SecurityKeyDto {

		private final String securityKeyType;
		private final boolean securityKeyLocked;

		private SecurityKeyDto(SecurityKeyType securityKeyType, boolean securityKeyLocked) {
			this.securityKeyType = securityKeyType.toString();
			this.securityKeyLocked = securityKeyLocked;
		}

		public String getSecurityKeyType() {
			return this.securityKeyType;
		}

		public boolean isSecurityKeyLocked() {
			return this.securityKeyLocked;
		}
	}

}
