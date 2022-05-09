/**
 * 
 */
package com.code42.ldap;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.backup42.app.cpc.CPCBackupProperties;
import com.code42.encryption.EncryptionServices;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.utils.LangUtils;

/**
 * Generic data object containing all the info needed to lookup a person from LDAP.
 */
public class LdapServerDto {

	private static final Logger log = LoggerFactory.getLogger(LdapServerDto.class);

	private final static Pattern uniqueIdAttrPattern = Pattern.compile("\\((\\w+)=\\?\\)");

	LdapServer ldapServer = null;

	public LdapServerDto(LdapServer ldap) {
		this.ldapServer = ldap;
	}

	public int getLdapServerId() {
		return this.ldapServer.getLdapServerId();
	}

	public String getLdapServerUid() {
		return this.ldapServer.getLdapServerUid();
	}

	public String getLdapServerName() {
		return this.ldapServer.getLdapServerName();
	}

	public String getUrl() {
		return this.ldapServer.getUrl();
	}

	public String getBindDn() {
		return this.ldapServer.getBindDn();
	}

	public String getBindPw() {
		String encrypted = this.ldapServer.getBindPw();
		String decrypted = null;
		if (LangUtils.hasValue(encrypted)) {
			try {
				decrypted = EncryptionServices.getCrypto().decrypt(encrypted);
			} catch (Throwable t) {
				log.error("Error decrypting LDAP password", t);
				decrypted = "";
			}
		}
		return decrypted;
	}

	public String getBindPwEncrypted() {
		return this.ldapServer.getBindPw();
	}

	/**
	 * Simple Example: "(mail=?)".
	 * <p>
	 * Complex Example: "(&(objectClass=orgInetPerson)(mail=?)(|(status=C)(status=E)))"
	 * 
	 * @return An LDAP search String that is guaranteed to find one or no persons (never multiple, although we just pick
	 *         one if it does).
	 */
	public String getSearchFilter() {
		return this.ldapServer.getPersonSearch();
	}

	public int getTimeoutSeconds() {
		return this.ldapServer.getTimeoutSeconds();
	}

	public boolean hasUniqueIdAttr() {
		return LangUtils.hasValue(this.getUniqueIdAttr());
	}

	public String getUniqueIdAttr() {
		String uniqueIdAttr = null;
		Matcher m = uniqueIdAttrPattern.matcher(this.getSearchFilter());
		if (m.find()) {
			uniqueIdAttr = m.group(1);
		}
		return uniqueIdAttr;
	}

	public String getEmailAttribute() {
		return this.ldapServer.getEmailField();
	}

	public String getFirstNameAttribute() {
		return this.ldapServer.getFirstNameField();
	}

	public String getLastNameAttribute() {
		return this.ldapServer.getLastNameField();
	}

	/**
	 * @return true if password compare is an option for authentication (could be used instead of bind)
	 */
	public boolean allowPasswordCompare() {
		return CPCBackupProperties.isLdapCompareAllowed();
	}

	/**
	 * Only displayed when b42.ldap.allowCompareVsBind property is true
	 * 
	 * @return null if we're not allowing password compare as an option
	 */
	public String getPasswordAttribute() {
		String field = this.ldapServer.getUserPwField();
		if (this.allowPasswordCompare()) {
			// convert null to blank.
			return field == null ? "" : field;
		}
		return null;
	}

	public String getOrgNameScript() {
		return this.ldapServer.getOrgNameScript();
	}

	public String getActiveScript() {
		return this.ldapServer.getActiveScript();
	}

	public String getRoleNameScript() {
		return this.ldapServer.getRoleNameScript();
	}

	/**
	 * This bits may be set:
	 * <ul>
	 * <li>1 = use ldap</li>
	 * <li>2 = search anonymously</li>
	 * <li>4 = URL specifies SSL</li>
	 * <li>5 = ignorePartialResultException</li>
	 * </ul>
	 * 
	 * @return all the flags as an int
	 */
	private int getLdapFlags() {
		return this.ldapServer.getFlags();
	}

	/**
	 * This only means something if the server has LDAP authentication set up.
	 * 
	 * @return true if this org's users should authenticate using the servers LDAP definition.
	 */
	public boolean getUseLdap() {
		return (this.getLdapFlags() & 1) == 1;
	}

	/**
	 * This only means something if the 1 bit is on (we have LDAP authentication turned on).
	 * 
	 * @return true if we should bind anonymously to LDAP.
	 */
	public boolean isBindAnonymously() {
		return (this.getLdapFlags() & 2) == 2;
	}

	/**
	 * @return true if this is an SSL connection
	 */
	public boolean isSsl() {
		return (this.getLdapFlags() & 4) == 4;
	}

	/**
	 * NOT USED UNTIL WE GET MORE INFORMATION.
	 * 
	 * @return true if we want to ignore PartialResultExceptions. Spring does this as well.
	 */
	public boolean isIgnorePartialResultException() {
		return (this.getLdapFlags() & 8) == 8;
	}

	public Date getCreationDate() {
		return this.ldapServer.getCreationDate();
	}

	public Date getModificationDate() {
		return this.ldapServer.getModificationDate();
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append(this.getClass().getSimpleName()).append("[");
		str.append("url:").append(this.getUrl());
		str.append(", anonymousBind:").append(this.isBindAnonymously());
		if (!this.isBindAnonymously()) {
			str.append(", bindDn:").append(this.getBindDn());
		}
		str.append(", searchFilter:").append(this.getSearchFilter());
		str.append(", emailAttr:").append(this.getEmailAttribute());
		str.append(", firstNameAttr:").append(this.getFirstNameAttribute());
		str.append(", lastNameAttribute:").append(this.getLastNameAttribute());
		str.append(", hasOrgNameScript:").append(LangUtils.hasValue(this.getOrgNameScript()));
		str.append(", hasActiveScript:").append(LangUtils.hasValue(this.getActiveScript()));
		str.append(", hasRoleNameScript:").append(LangUtils.hasValue(this.getRoleNameScript()));
		str.append("]");
		return str.toString();
	}

	/**
	 * Make sure you turn on assertions when you run this. Or be real careful in checking the output.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println(" *** Make sure you have assertions turned on  (-ea JVM argument) *** ");

		LdapServer server = new LdapServer();
		LdapServerDto ldapInfo = new LdapServerDto(server);
		server.setPersonSearch("(&(objectClass=orgInetPerson)(mail=?)(|(status=C)(status=E)))");
		myAssert("mail", ldapInfo.getUniqueIdAttr(), null);
	}

	public static void myAssert(String val1, String val2, String msg) {
		System.out.println("asserting that " + val1 + "=" + val2);
		assert val1.equals(val2) : msg;

	}

}