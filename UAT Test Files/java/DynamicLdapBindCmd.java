package com.code42.ldap;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.core.ldap.ILDAPService;
import com.code42.core.ldap.LDAPSpec;
import com.code42.utils.LangUtils;
import com.google.inject.Inject;

/**
 * Perform an arbitrary bind against an LDAP directory. <br>
 * <br>
 * Note that unliked DynamicLdapLookupCmd we can't use the Directory interface here. Generic Directories have no notion
 * of "binding" to the directory so it isn't implemented on the interface; it's very much an LDAP-specific operation. As
 * such as have to fall down to the next level of abstraction and deal with ILDAPServices directly.
 */
public class DynamicLdapBindCmd extends AbstractCmd<Void> {

	/* ================= Dependencies ================= */
	private ILDAPService ldap;

	/* ================= DI injection points ================= */
	@Inject
	public void setLDAP(ILDAPService arg) {
		this.ldap = arg;
	}

	private Builder builder;

	private DynamicLdapBindCmd(Builder builder) {
		this.builder = builder;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		LDAPSpec.Builder specBuilder = new LDAPSpec.Builder();
		specBuilder.url(this.builder.url);
		specBuilder.bindDN(this.builder.bindDn);
		specBuilder.bindPassword(this.builder.bindPw);

		LDAPSpec server = specBuilder.build();

		try {
			this.ldap.bind(server, this.builder.timeoutSeconds, TimeUnit.SECONDS).get();
		} catch (InterruptedException e) {
			throw new CommandException("Unable to bind to: " + server.getURL() + ", error: " + e.getMessage());
		} catch (ExecutionException e) {
			throw new CommandException("Unable to bind to: " + server.getURL() + ", error: " + e.getMessage());
		}

		return null;
	}

	public static class Builder {

		/* URL contains host, port and search base */
		private String url;

		private String bindDn;
		private String bindPw;

		private int timeoutSeconds = 30;

		public Builder url(String url) {
			this.url = url;
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

		public Builder timeoutSeconds(int seconds) {
			this.timeoutSeconds = seconds;
			return this;
		}

		public void validate() throws BuilderException {
			if (!LangUtils.hasValue(this.url)) {
				throw new BuilderException("url is required");
			}
		}

		public DynamicLdapBindCmd build() throws BuilderException {
			this.validate();
			return new DynamicLdapBindCmd(this);
		}
	}
}
