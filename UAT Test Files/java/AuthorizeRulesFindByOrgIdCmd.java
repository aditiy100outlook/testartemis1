package com.code42.account;

import java.util.List;

import com.backup42.account.AccountServices;
import com.backup42.app.cpc.CPCBackupProperty;
import com.backup42.common.AuthorizeRules;
import com.backup42.service.SettingsServices;
import com.code42.auth.AuthenticatorFindByOrgCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.Authenticator;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.LDAPAuthenticator;
import com.code42.core.auth.impl.RADIUSAuthenticator;
import com.code42.core.directory.Directory;
import com.code42.core.impl.AbstractCmd;
import com.code42.directory.DirectoryFindAllByOrgCmd;
import com.code42.org.IOrg;
import com.code42.org.OrgFindByIdCmd;
import com.code42.org.OrgSettingsInfo;
import com.code42.org.OrgSsoFindByOrgIdCmd;
import com.code42.utils.LangUtils;
import com.code42.utils.SystemProperties;

/**
 * Finds authorization rules for the given org.
 */
public class AuthorizeRulesFindByOrgIdCmd extends AbstractCmd<AuthorizeRules> {

	private int orgId;
	private boolean isNewOrg = false;

	public AuthorizeRulesFindByOrgIdCmd(int orgId) {
		this.orgId = orgId;
	}

	public AuthorizeRulesFindByOrgIdCmd(int orgId, boolean isNewOrg) {
		this.orgId = orgId;
		this.isNewOrg = isNewOrg;
	}

	@Override
	public AuthorizeRules exec(CoreSession session) throws CommandException {

		OrgSettingsInfo osi = SettingsServices.getInstance().getOrgSettingsInfo(this.orgId, this.isNewOrg);
		IOrg org = this.run(new OrgSsoFindByOrgIdCmd(this.orgId), session);
		if (org == null) {
			org = this.run(new OrgFindByIdCmd(this.orgId), session);
		}

		/*
		 * CP-7610
		 * 
		 * If we didn't find an org via either path above then there's no point in continuing... we won't be able to come up
		 * with anything (and in fact most of the operations below us will bomb out completely).
		 */
		if (org == null) {

			throw new AuthorizeRulesFindByOrgIdCmd.UnknownOrgException(this.orgId);
		}

		List<Directory> directories = this.run(new DirectoryFindAllByOrgCmd(this.orgId), session);

		boolean ldap = AccountServices.getInstance().isDirNotLocal(directories);
		boolean ssoAuth = AccountServices.getInstance().isOrgSsoAuth(this.orgId);
		boolean systemAllowsDeferredLogins = SystemProperties.getOptionalBoolean(
				CPCBackupProperty.ALLOW_DEFERRED_CLIENT_REGISTRATION,
				CPCBackupProperty.Default.ALLOW_DEFERRED_CLIENT_REGISTRATION);
		boolean deferredAllowed = systemAllowsDeferredLogins && (ldap || ssoAuth);

		boolean usernameIsAnEmail = osi.getUsernameIsAnEmail() != null ? osi.getUsernameIsAnEmail() : false;

		AuthorizeRules.Builder builder = new AuthorizeRules.Builder(ldap);
		builder.usernameIsAnEmail(usernameIsAnEmail).deferredAllowed(deferredAllowed);

		boolean isLdap = false;
		boolean isRadius = false;
		List<Authenticator> authenticators = this.run(new AuthenticatorFindByOrgCmd(org), this.auth.getAdminSession());
		if (LangUtils.hasElements(authenticators)) {
			for (Authenticator a : authenticators) {
				if (a instanceof LDAPAuthenticator) {
					isLdap = true;
				} else if (a instanceof RADIUSAuthenticator) {
					isRadius = true;
				}
			}
		}

		// Don't hash the password for an outside service check
		if (isLdap || isRadius) {
			builder.hashPassword(false);
		}
		return builder.build();
	}

	/* =============================== Inner exception classes =============================== */
	/*
	 * Exception indicating that we couldn't find an org for the specified org ID
	 */
	public static class UnknownOrgException extends CommandException {

		private static final long serialVersionUID = 967352449297202640L;

		private final int orgid;

		public UnknownOrgException(int orgid) {

			super("Unknown org: " + orgid);
			this.orgid = orgid;
		}

		public int getOrgId() {
			return this.orgid;
		}
	}
}