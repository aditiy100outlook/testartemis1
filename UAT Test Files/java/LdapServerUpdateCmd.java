/*
 * <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.ldap;

import com.backup42.common.perm.C42PermissionPro;
import com.backup42.history.CpcHistoryLogger;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.NotFoundException;
import com.code42.core.impl.DBCmd;
import com.code42.encryption.EncryptionServices;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.utils.LangUtils;
import com.code42.utils.option.Some;

/**
 * Validate and update all the fields on the given LdapServer, ensuring that the ldapServerUid field is populated.<br>
 */
public class LdapServerUpdateCmd extends DBCmd<LdapServer> {

	private static final Logger log = LoggerFactory.getLogger(LdapServerUpdateCmd.class);

	private Builder data = null;

	/**
	 * Use the Builder static inner class to construct one of these.
	 */
	private LdapServerUpdateCmd(Builder b) {
		this.data = b;
	}

	@Override
	public LdapServer exec(CoreSession session) throws CommandException {

		this.ensureNotCPCentral();

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		LdapServer ldapServer = null;

		this.db.beginTransaction();

		try {

			ldapServer = this.db.find(new LdapServerFindByIdQuery(this.data.ldapServerId));

			if (ldapServer == null || ldapServer.getLdapServerId() == null) {
				throw new NotFoundException("Unable to update LdapServer; key value is null or invalid: "
						+ this.data.ldapServerId);
			}

			if (this.data.ldapServerName instanceof Some<?>) {
				ldapServer.setLdapServerName(this.data.ldapServerName.get());
			}

			if (this.data.use instanceof Some<?>) {
				ldapServer.setUseLdap(this.data.use.get());
			}

			if (this.data.url instanceof Some<?>) {
				ldapServer.setUrl(this.data.url.get());
			}

			if (this.data.bindAnonymously instanceof Some<?>) {
				ldapServer.setAnonymous(this.data.bindAnonymously.get());
			}

			if (this.data.bindDn instanceof Some<?>) {
				ldapServer.setBindDn(this.data.bindDn.get());
			}

			if (this.data.bindPw instanceof Some<?>) {
				String bindPw = this.data.bindPw.get();
				if (LangUtils.hasValue(bindPw)) {
					bindPw = EncryptionServices.getCrypto().encrypt(bindPw);
				}
				ldapServer.setBindPw(bindPw);
			}

			if (this.data.searchFilter instanceof Some<?>) {
				ldapServer.setPersonSearch(this.data.searchFilter.get());
			}

			if (this.data.emailAttribute instanceof Some<?>) {
				ldapServer.setEmailField(this.data.emailAttribute.get());
			}

			if (this.data.firstNameAttribute instanceof Some<?>) {
				ldapServer.setFirstNameField(this.data.firstNameAttribute.get());
			}

			if (this.data.lastNameAttribute instanceof Some<?>) {
				ldapServer.setLastNameField(this.data.lastNameAttribute.get());
			}

			if (this.data.passwordAttribute instanceof Some<?>) {
				ldapServer.setUserPwField(this.data.passwordAttribute.get());
			}

			if (this.data.orgNameScript instanceof Some<?>) {
				ldapServer.setOrgNameScript(this.data.orgNameScript.get());
			}

			if (this.data.roleNameScript instanceof Some<?>) {
				ldapServer.setRoleNameScript(this.data.roleNameScript.get());
			}

			if (this.data.activeScript instanceof Some<?>) {
				ldapServer.setActiveScript(this.data.activeScript.get());
			}

			if (this.data.timeoutSeconds instanceof Some<?>) {
				ldapServer.setTimeoutSeconds(this.data.timeoutSeconds.get());
			}

			if (this.data.use instanceof Some<?>) {
				ldapServer.setUseLdap(this.data.use.get());
			}

			/*
			 * An LdapServer should NEVER be here without a uid
			 */
			assert (LangUtils.hasValue(ldapServer.getLdapServerUid()));

			ldapServer = this.db.update(new LdapServerUpdateQuery(ldapServer));

			this.db.commit();

			CpcHistoryLogger.info(session, "LDAP:: modified server: {}", ldapServer);

		} catch (CommandException e) {
			this.db.rollback();
			throw e;
		} catch (Throwable e) {
			this.db.rollback();
			log.error("Unexpected: ", e);
		} finally {
			this.db.endTransaction();
		}

		return ldapServer;
	}

	public static class Builder extends LdapServerBuilder<Builder, LdapServerUpdateCmd> {

		/* This value must always be present; it's the only way to get a builder */
		public int ldapServerId = 0;

		public Builder(int ldapServerId) {
			this.ldapServerId = ldapServerId;
		}

		@Override
		public LdapServerUpdateCmd build() throws BuilderException {
			this.validate();
			return new LdapServerUpdateCmd(this);
		}
	}

}
