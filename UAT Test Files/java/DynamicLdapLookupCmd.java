package com.code42.ldap;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.backup42.app.cpc.CPCBackupProperty;
import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.directory.Directory;
import com.code42.core.directory.DirectoryEntry;
import com.code42.core.directory.DirectoryEntryProperty;
import com.code42.core.directory.DirectoryFactory;
import com.code42.core.directory.DirectoryMapping;
import com.code42.core.directory.impl.mapping.ScriptMapping;
import com.code42.core.impl.AbstractCmd;
import com.code42.core.ldap.LDAPSpec;
import com.code42.utils.LangUtils;
import com.code42.utils.SystemProperties;
import com.google.inject.Inject;

/**
 * Perform an arbitrary search against an LDAP directory. <br>
 * <br>
 * Note that this class performs much the same function as DirectorySearchAllCmd although this class has a builder for
 * easy use from REST. Also note that we could have also implemented this command using LDAPServices directly; instead
 * we follow the (generally recommended) approach of using the higher-level abstractions if possible.
 * 
 */
public class DynamicLdapLookupCmd extends AbstractCmd<List<DirectoryEntry>> {

	public enum Error {
		// CP-5053 - Disable LDAP Search on initial display of LDAP Server Settings Page
		EXACT_SEARCH_REQUIRED
	}

	public static final String SEARCH_ALL_USERS = "*";

	/* ================= Dependencies ================= */
	private DirectoryFactory factory;

	/* ================= DI injection points ================= */
	@Inject
	public void setFactory(DirectoryFactory arg) {
		this.factory = arg;
	}

	private Builder builder;

	private DynamicLdapLookupCmd(Builder builder) {
		this.builder = builder;
	}

	@Override
	public List<DirectoryEntry> exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		// If b42.ldap.exactSearchRequired system property is set then do not allow wildcard LDAP searches.
		// CP-5053 - Disable LDAP Search on initial display of LDAP Server Settings Page
		boolean exactRequired = SystemProperties.getOptionalBoolean(CPCBackupProperty.LDAP_EXACT_SEARCH_REQUIRED, false);
		if (exactRequired && this.builder.username != null && this.builder.username.contains(SEARCH_ALL_USERS)) {
			throw new CommandException(Error.EXACT_SEARCH_REQUIRED, "Exact username search is required.");
		}

		// This mapping stuff is a candidate for an external class
		ScriptMapping.Builder mappingBuilder = new ScriptMapping.Builder();
		if (LangUtils.hasValue(this.builder.firstNameAttribute)) {
			mappingBuilder.firstname(this.builder.firstNameAttribute);
		}
		if (LangUtils.hasValue(this.builder.lastNameAttribute)) {
			mappingBuilder.lastname(this.builder.lastNameAttribute);
		}
		if (LangUtils.hasValue(this.builder.emailAttribute)) {
			mappingBuilder.email(this.builder.emailAttribute);
		}
		if (LangUtils.hasValue(this.builder.activeScript)) {
			mappingBuilder.activeScript(this.builder.activeScript);
		}
		if (LangUtils.hasValue(this.builder.orgNameScript)) {
			mappingBuilder.orgNameScript(this.builder.orgNameScript);
		}
		if (LangUtils.hasValue(this.builder.roleNameScript)) {
			mappingBuilder.roleNameScript(this.builder.roleNameScript);
		}
		DirectoryMapping mapping = mappingBuilder.build();

		LDAPSpec.Builder specBuilder = new LDAPSpec.Builder();
		specBuilder.url(this.builder.url).searchFilter(this.builder.searchFilter).mapping(mapping);
		specBuilder.bindDN(this.builder.bindDn).bindPassword(this.builder.bindPw);
		// TODO: This value should not be hard-coded, but should come from the UI
		specBuilder.followReferrals(false);

		try {

			Directory ldap = this.factory.getLDAPDirectory(specBuilder.build(), true/* test - no LDAP system alert */);
			List<DirectoryEntry> result = ldap.findUser(this.builder.username, this.builder.timeoutSeconds, TimeUnit.SECONDS,
					this.builder.limit);

			// Add the mapping data
			for (DirectoryEntry entry : result) {
				entry.setProperty(DirectoryEntryProperty.MAPPING, mapping);
			}

			return result;
		} catch (Throwable t) {
			throw new CommandException("DynamicLdapLookupCmd error", t);
		}
	}

	public static class Builder {

		/* URL contains host, port and search base */
		private String url;
		private String searchFilter;

		private String bindDn;
		private String bindPw;

		private int timeoutSeconds = 30;
		private int limit = 0;

		private String username;
		private String emailAttribute;
		private String firstNameAttribute;
		private String lastNameAttribute;
		private String orgNameScript;
		private String activeScript;
		private String roleNameScript;

		public Builder url(String url) {
			this.url = url;
			return this;
		}

		/** a question mark in the filter is required */
		public Builder searchFilter(String filter) {
			this.searchFilter = filter;
			return this;
		}

		public Builder bindDn(String bindDn) {
			this.bindDn = bindDn;
			return this;
		}

		public Builder bindPw(String bindPw) {
			this.bindPw = bindPw;
			return this;
		}

		public Builder username(String username) {
			this.username = username;
			return this;
		}

		public Builder emailAttribute(String emailAttr) {
			this.emailAttribute = emailAttr;
			return this;
		}

		public Builder firstNameAttribute(String firstNameAttr) {
			this.firstNameAttribute = firstNameAttr;
			return this;
		}

		public Builder lastNameAttribute(String lastNameAttr) {
			this.lastNameAttribute = lastNameAttr;
			return this;
		}

		public Builder orgNameScript(String orgNameScript) {
			this.orgNameScript = orgNameScript;
			return this;
		}

		public Builder activeScript(String activeScript) {
			this.activeScript = activeScript;
			return this;
		}

		public Builder roleNameScript(String roleNameScript) {
			this.roleNameScript = roleNameScript;
			return this;
		}

		public Builder timeoutSeconds(int seconds) {
			this.timeoutSeconds = seconds;
			return this;
		}

		public Builder limit(int limit) {
			this.limit = limit;
			return this;
		}

		public void validate() throws BuilderException {
			if (this.username == null) {
				throw new BuilderException("username is required");
			}
			if (this.searchFilter == null) {
				throw new BuilderException("filter is required");
			}
			if (!this.searchFilter.contains("?")) {
				throw new BuilderException("filter must have a question mark.  Example: (mail=?)");
			}
		}

		public DynamicLdapLookupCmd build() throws BuilderException {
			this.validate();
			return new DynamicLdapLookupCmd(this);
		}

	}
}
