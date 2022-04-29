package com.code42.env;

import com.backup42.CpcConstants;
import com.code42.utils.SystemPropertyValues;

public class ServerEnvDto {

	private long version;
	private String productVersion;
	private String clientProductVersion;
	private boolean master;
	private boolean hasMasterLicenseKey;
	private boolean hasAnyoneLoggedIn;
	private boolean businessCluster;
	private boolean consumerCluster;
	private boolean sslRequired;
	private boolean defaultAdminPassword;
	private boolean adminEmailSet;
	private boolean queryLimited;
	private String buildEnv;
	private int portPrefix;
	private int serverId;
	private int clusterId;
	private long serverGuid;
	private String mlk;
	private boolean statsEnabled;
	private boolean secureKeystoreLocked;
	private String ssoAuthProvider;
	private boolean ssoAuthEnabled;
	private Integer httpsPort;

	protected void setMaster(boolean master) {
		this.master = master;
	}

	public boolean isMaster() {
		return this.master;
	}

	public boolean isStorage() {
		return !this.master;
	}

	protected void setHasMasterLicenseKey(boolean hasMasterLicenseKey) {
		this.hasMasterLicenseKey = hasMasterLicenseKey;
	}

	protected void setMlk(String mlk) {
		this.mlk = mlk;
	}

	public String getMlk(String mlk) {
		return this.mlk;
	}

	public boolean hasMasterLicenseKey() {
		return this.hasMasterLicenseKey;
	}

	protected void setHasAnyoneLoggedIn(boolean hasAnyoneLoggedIn) {
		this.hasAnyoneLoggedIn = hasAnyoneLoggedIn;
	}

	public boolean hasAnyoneLoggedIn() {
		return this.hasAnyoneLoggedIn;
	}

	protected void setBusinessCluster(boolean business) {
		this.businessCluster = business;
	}

	public boolean isBusinessCluster() {
		return this.businessCluster;
	}

	protected void setConsumerCluster(boolean consumer) {
		this.consumerCluster = consumer;
	}

	public boolean isConsumerCluster() {
		return this.consumerCluster;
	}

	public boolean isEnterpriseCluster() {
		return !this.consumerCluster && !this.businessCluster;
	}

	protected void setBuildEnv(String buildEnv) {
		this.buildEnv = buildEnv;
	}

	public String getBuildEnv(String buildEnv) {
		return this.buildEnv;
	}

	protected void setPortPrefix(int portPrefix) {
		this.portPrefix = portPrefix;
	}

	public int getPortPrefix() {
		return this.portPrefix;
	}

	protected void setSSLRequired(boolean sslRequired) {
		this.sslRequired = sslRequired;
	}

	public boolean isSSLRequired() {
		return this.sslRequired;
	}

	protected void setDefaultAdminPassword(boolean flag) {
		this.defaultAdminPassword = flag;
	}

	public boolean isDefaultAdminPassword() {
		return this.defaultAdminPassword;
	}

	protected void setAdminEmailSet(boolean flag) {
		this.adminEmailSet = flag;
	}

	public boolean isAdminEmailSet() {
		return this.adminEmailSet;
	}

	public boolean isDev() {
		return this.buildEnv.equals(SystemPropertyValues.BUILD_ENV_DEV);
	}

	public boolean isStg() {
		return this.buildEnv.equals(SystemPropertyValues.BUILD_ENV_STG);
	}

	public boolean isPrd() {
		return this.buildEnv.equals(SystemPropertyValues.BUILD_ENV_PRD);
	}

	public boolean isBeta() {
		return this.buildEnv.equals(SystemPropertyValues.BUILD_ENV_BETA);
	}

	protected void setServerId(int serverId) {
		this.serverId = serverId;
	}

	public int getServerId() {
		return this.serverId;
	}

	protected void setClusterServerId(int clusterId) {
		this.clusterId = clusterId;
	}

	public int getClusterServerId() {
		return this.clusterId;
	}

	protected void setServerGuid(long serverGuid) {
		this.serverGuid = serverGuid;
	}

	public long getServerGuid() {
		return this.serverGuid;
	}

	public long getVersion() {
		return this.version;
	}

	public void setVersion(long version) {
		this.version = version;
	}

	public String getProductVersion() {
		return this.productVersion;
	}

	public void setProductVersion(String productVersion) {
		this.productVersion = productVersion;
	}

	public String getClientProductVersion() {
		return this.clientProductVersion;
	}

	public void setClientProductVersion(String clientProductVersion) {
		this.clientProductVersion = clientProductVersion;
	}

	public boolean isQueryLimited() {
		return this.queryLimited;
	}

	public void setQueryLimited(boolean queryLimited) {
		this.queryLimited = queryLimited;
	}

	public boolean isStatsEnabled() {
		return this.statsEnabled;
	}

	public void setStatsEnabled(boolean statsEnabled) {
		this.statsEnabled = statsEnabled;
	}

	public int getCpOrgId() {
		return CpcConstants.Orgs.CP_ID;
	}

	public int getProOrgId() {
		return CpcConstants.Orgs.PRO_ID;
	}

	/**
	 * SecureKeystore is enabled and locked. Server is disabled until the SecureKeystore is unlocked by admin entering the
	 * keystore password.
	 */
	public boolean isSecureKeystoreLocked() {
		return this.secureKeystoreLocked;
	}

	public void setSecureKeystoreLocked(boolean secureKeystoreLocked) {
		this.secureKeystoreLocked = secureKeystoreLocked;
	}

	public String getSsoAuthProvider() {
		return this.ssoAuthProvider;
	}

	public void setSsoAuthProvider(String name) {
		this.ssoAuthProvider = name;
	}

	public boolean isSsoAuthEnabled() {
		return this.ssoAuthEnabled;
	}

	public void setSsoAuthEnabled(boolean enabled) {
		this.ssoAuthEnabled = enabled;
	}

	public int getHttpsPort() {
		return this.httpsPort;
	}

	/**
	 * No colon in this port
	 * 
	 * @param numeric - a string
	 */
	public void setHttpsPort(int httpsPort) {
		this.httpsPort = httpsPort;
		// if (LangUtils.hasValue(numeric)) {
		// this.httpsPort = Integer.parseInt(numeric.trim());
		// }
	}
}
