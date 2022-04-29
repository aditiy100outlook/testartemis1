/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.user;

import java.util.Date;

import com.code42.core.impl.CoreBridge;
import com.code42.exception.DebugRuntimeException;
import com.code42.org.Org;
import com.code42.org.OrgAuthTypeFindByOrgCmd;
import com.code42.org.OrgSettingsInfo;
import com.code42.util.option.IDto;

/**
 * Response object for User Invites
 */
public class UserInviteDto implements IDto {

	private final User user;
	private final Org org;
	private final OrgSettingsInfo orgSettings;

	public UserInviteDto(User user, Org org, OrgSettingsInfo orgSettings) {
		this.user = user;
		this.org = org;
		this.orgSettings = orgSettings;

		if (this.user == null || this.org == null) {
			throw new DebugRuntimeException("Invalid values", new Object[] { user, org });
		}
	}

	public Integer getUserId() {
		return this.user.getUserId();
	}

	public String getUsername() {
		return this.user.getUsername();
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

	public Integer getOrgId() {
		return this.user.getOrgId();
	}

	public String getOrgName() {
		return this.org.getOrgName();
	}

	public String getUsernameId() {
		return this.user.getUserUid();
	}

	public Date getCreationDate() {
		return this.user.getCreationDate();
	}

	public Date getModificationDate() {
		return this.user.getModificationDate();
	}

	public boolean getUsernameIsAnEmail() {
		return this.orgSettings.getUsernameIsAnEmail();
	}

	public String getAuthType() {
		String authType = CoreBridge.runNoException(new OrgAuthTypeFindByOrgCmd(this.user.getOrgId())).toString();
		return authType;
	}
}
