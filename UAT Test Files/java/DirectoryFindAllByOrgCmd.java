package com.code42.directory;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.backup42.CpcConstants;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.directory.Directory;
import com.code42.core.directory.DirectoryException;
import com.code42.core.directory.DirectoryMapping;
import com.code42.core.directory.impl.mapping.ScriptMapping;
import com.code42.core.impl.AbstractCmd;
import com.code42.core.ldap.LDAPSpec;
import com.code42.encryption.EncryptionServices;
import com.code42.ldap.LdapServer;
import com.code42.ldap.LdapServerFindAllByOrgCmd;
import com.code42.org.IOrg;
import com.code42.utils.LangUtils;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

/**
 * Generate an ordered list of directories for an org. This list should be used when attempting to find directory
 * entries for users within this org.
 */
public class DirectoryFindAllByOrgCmd extends AbstractCmd<List<Directory>> {

	/* ================= Dependencies ================= */
	private com.code42.core.directory.DirectoryFactory factory;

	/* ================= DI injection points ================= */
	@Inject
	public void setFactory(com.code42.core.directory.DirectoryFactory arg) {
		this.factory = arg;
	}

	private final int orgId;

	public DirectoryFindAllByOrgCmd(int orgId) {
		this.orgId = orgId;
	}

	public DirectoryFindAllByOrgCmd(IOrg org) {
		this.orgId = org.getOrgId();
	}

	@Override
	public List<Directory> exec(CoreSession session) throws CommandException {

		try {
			List<LdapServer> servers = null;

			// Ignore LDAP for orgId 1 (the ADMIN_ID)
			if (this.orgId != CpcConstants.Orgs.ADMIN_ID) {
				servers = this.runtime.run(new LdapServerFindAllByOrgCmd(this.orgId), this.auth.getAdminSession());
			}

			if (servers == null) {
				/* A non-LDAP org... return the local directory only */
				return ImmutableList.of(this.factory.getLocalDirectory());
			}

			List<Directory> rv = new LinkedList<Directory>();
			for (LdapServer server : servers) {
				rv.add(this.createDirectory(server));
			}
			return ImmutableList.copyOf(rv);
		} catch (DirectoryException de) {
			throw new CommandException("Exception creating local directory", de);
		}
	}

	private Directory createDirectory(LdapServer server) throws DirectoryException {

		ScriptMapping.Builder mappingBuilder = new ScriptMapping.Builder();
		if (LangUtils.hasValue(server.getFirstNameField())) {
			mappingBuilder.firstname(server.getFirstNameField());
		}
		if (LangUtils.hasValue(server.getLastNameField())) {
			mappingBuilder.lastname(server.getLastNameField());
		}
		if (LangUtils.hasValue(server.getEmailField())) {
			mappingBuilder.email(server.getEmailField());
		}
		if (LangUtils.hasValue(server.getActiveScript())) {
			mappingBuilder.activeScript(server.getActiveScript());
		}
		if (LangUtils.hasValue(server.getOrgNameScript())) {
			mappingBuilder.orgNameScript(server.getOrgNameScript());
		}
		if (LangUtils.hasValue(server.getRoleNameScript())) {
			mappingBuilder.roleNameScript(server.getRoleNameScript());
		}
		DirectoryMapping mapping;
		try {
			mapping = mappingBuilder.build();
		} catch (BuilderException be) {
			throw new DirectoryException("Unable to create mapping", be);
		}

		LDAPSpec.Builder builder = new LDAPSpec.Builder();
		builder.url(server.getUrl()).searchFilter(server.getPersonSearch()).mapping(mapping);
		if (LangUtils.hasValue(server.getBindDn())) {
			builder.bindDN(server.getBindDn());
		}
		if (LangUtils.hasValue(server.getBindPw())) {
			builder.bindPassword(EncryptionServices.getCrypto().decrypt(server.getBindPw()));
		}
		builder.followReferrals(server.isReferralFollow());

		return DirectoryFindAllByOrgCmd.this.factory.getLDAPDirectory(builder.build(), server.getTimeoutSeconds(),
				TimeUnit.SECONDS);
	}
}
