/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.auth;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.org.Org;
import com.code42.org.OrgFindByIdQuery;
import com.code42.org.OrgSettingsInfo;
import com.code42.org.OrgSettingsInfoFindByOrgCmd;
import com.code42.user.User;
import com.code42.user.UserFindByIdQuery;
import com.code42.user.UserInviteDto;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Perform the handling of inbound invite tokens; ensure their validity and return the related User object.
 */
public class InviteTokenValidateCmd extends DBCmd<UserInviteDto> {

	/* ================= Dependencies ================= */
	private InviteTokenHandler handler;

	@Inject
	public void setLocal(@Named("invite") AutoTokenHandler handler) {
		this.handler = (InviteTokenHandler) handler;
	}

	private final String encryptedToken;

	public InviteTokenValidateCmd(String encryptedToken) {
		this.encryptedToken = encryptedToken;
	}

	@Override
	public UserInviteDto exec(CoreSession session) throws CommandException {

		/*
		 * NOTE: this command is called without an authenticated session. Do NOT depend on session having a useful value.
		 */

		/*
		 * Decrypt and validate the token.
		 */
		InviteToken token = this.handler.handleInboundToken(this.encryptedToken);

		Integer userId = token.getUserId();
		User user = this.db.find(new UserFindByIdQuery(userId));
		Org org = this.db.find(new OrgFindByIdQuery(user.getOrgId()));
		final OrgSettingsInfoFindByOrgCmd.Builder builder = new OrgSettingsInfoFindByOrgCmd.Builder();
		builder.orgId(user.getOrgId());
		final OrgSettingsInfo osi = this.runtime.run(builder.build(), this.auth.getAdminSession());
		return new UserInviteDto(user, org, osi);
	}

}
