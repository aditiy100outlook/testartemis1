package com.code42.ldap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.code42.core.BuilderException;
import com.code42.utils.LangUtils;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;

/**
 * Builds the input data and the LdapServerUpdate command. This takes the place of a big long constructor.
 * 
 * Note that within validate() below we're only doing null checks on builder methods which take a reference type and not
 * those that take a value type (i.e. a primitive). Even though an integer will get autoboxed into an Integer within the
 * Option type and that Integer could (in theory) be null there's no way for a null integer primitive to be passed into
 * the actual method call... and as such there's no way the Integer in the corresponding Option type could be null in
 * practice. Keep this in mind if you change the method signature on any of the builder methods below.
 */
public abstract class LdapServerBuilder<S, T> {

	public Option<String> ldapServerName = None.getInstance();
	public Option<Boolean> use = None.getInstance();
	public Option<String> url = None.getInstance();
	public Option<Boolean> bindAnonymously = None.getInstance();
	public Option<String> bindDn = None.getInstance();
	public Option<String> bindPw = None.getInstance();
	public Option<String> searchFilter = None.getInstance();
	public Option<String> emailAttribute = None.getInstance();
	public Option<String> firstNameAttribute = None.getInstance();
	public Option<String> lastNameAttribute = None.getInstance();
	public Option<String> passwordAttribute = None.getInstance();
	public Option<String> orgNameScript = None.getInstance();
	public Option<String> activeScript = None.getInstance();
	public Option<String> roleNameScript = None.getInstance();
	public Option<Integer> timeoutSeconds = None.getInstance();

	public LdapServerBuilder() {
	}

	public S ldapServerName(String name) {
		this.ldapServerName = new Some<String>(name);
		return (S) this;
	}

	public S use(boolean use) {
		this.use = new Some<Boolean>(use);
		return (S) this;
	}

	public S url(String url) {
		this.url = new Some<String>(url);
		return (S) this;
	}

	public S bindAnonymously(boolean bindAnonymously) {
		this.bindAnonymously = new Some<Boolean>(bindAnonymously);
		return (S) this;
	}

	public S bindDn(String bindDn) {
		this.bindDn = new Some<String>(bindDn);
		return (S) this;
	}

	public S bindPw(String bindPw) {
		this.bindPw = new Some<String>(bindPw);
		return (S) this;
	}

	public S searchFilter(String searchFilter) {
		this.searchFilter = new Some<String>(searchFilter);
		return (S) this;
	}

	public S emailAttribute(String attribute) {
		this.emailAttribute = new Some<String>(attribute);
		return (S) this;
	}

	public S firstNameAttribute(String attribute) {
		this.firstNameAttribute = new Some<String>(attribute);
		return (S) this;
	}

	public S lastNameAttribute(String attribute) {
		this.lastNameAttribute = new Some<String>(attribute);
		return (S) this;
	}

	public S passwordAttribute(String attribute) {
		this.passwordAttribute = new Some<String>(attribute);
		return (S) this;
	}

	public S orgNameScript(String script) {
		String s = script == null ? null : script.trim();
		if (LangUtils.hasValue(script)) {
			// Note, this should not be necessary, but RESTResource.parseRawBody leaves the quotes when it should not.
			// A better solution would be to fix that method.
			if (s.startsWith("\"") && s.endsWith("\"")) {
				s = s.substring(1, s.length() - 1);
			}
		} else {
			// convert empty or blank string to null
			s = null;
		}
		this.orgNameScript = new Some<String>(s);
		return (S) this;
	}

	public S roleNameScript(String script) {
		String s = script == null ? null : script.trim();
		if (LangUtils.hasValue(script)) {
			// Note, this should not be necessary, but RESTResource.parseRawBody leaves the quotes when it should not.
			// A better solution would be to fix that method.
			if (s.startsWith("\"") && s.endsWith("\"")) {
				s = s.substring(1, s.length() - 1);
			}
		} else {
			// convert empty or blank string to null
			s = null;
		}
		this.roleNameScript = new Some<String>(s);
		return (S) this;
	}

	public S activeScript(String script) {
		String s = script == null ? null : script.trim();
		if (LangUtils.hasValue(script)) {
			// Note, this should not be necessary, but RESTResource.parseRawBody leaves the quotes when it should not.
			// A better solution would be to fix that method.
			if (s.startsWith("\"") && s.endsWith("\"")) {
				s = s.substring(1, s.length() - 1);
			}
		} else {
			// convert empty or blank string to null
			s = null;
		}
		this.activeScript = new Some<String>(s);
		return (S) this;
	}

	public S timeoutSeconds(int seconds) {
		this.timeoutSeconds = new Some<Integer>(seconds);
		return (S) this;
	}

	public void validate() throws BuilderException {

		if (!(this.ldapServerName instanceof None) && !LangUtils.hasValue(this.ldapServerName.get())) {
			throw new BuilderException("ldapServerName cannot be null");
		}

		if (!(this.url instanceof None) && !LangUtils.hasValue(this.ldapServerName.get())) {
			throw new BuilderException("url cannot be null");
		}

		if (!(this.timeoutSeconds instanceof None) && this.timeoutSeconds.get() < 1) {
			throw new BuilderException("timeoutSeconds must be greater than zero");
		}

		if (!this.validateScript(this.activeScript)) {
			throw new BuilderException("Invalid activeScript value: " + this.activeScript.get());
		}

		if (!this.validateScript(this.orgNameScript)) {
			throw new BuilderException("Invalid orgNameScript value: " + this.orgNameScript.get());
		}

		if (!this.validateScript(this.roleNameScript)) {
			throw new BuilderException("Invalid roleNameScript value: " + this.roleNameScript.get());
		}
	}

	private static final Pattern SCRIPT_REGEX = Pattern.compile("^\\s*function\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*\\}\\s*$");

	private boolean validateScript(Option<String> scriptOption) {
		if (scriptOption instanceof None) {
			return true;
		}
		if (scriptOption.get() == null) {
			return true;
		}
		Matcher m = SCRIPT_REGEX.matcher(scriptOption.get());
		return m.matches();
	}

	public abstract T build() throws BuilderException;
}