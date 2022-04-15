package com.code42.env;

import com.backup42.app.license.MasterLicenseService;
import com.backup42.common.CPVersion;
import com.code42.core.CommandException;
import com.code42.core.IEnvironment;
import com.code42.core.alert.ISystemAlertService;
import com.code42.core.alert.SystemAlert;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.core.security.ICryptoService;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.OrgSsoAuthFindCountQuery;
import com.code42.server.license.MasterLicense;
import com.code42.ssoauth.SsoAuth;
import com.code42.ssoauth.SsoAuthFindByIdQuery;
import com.code42.user.UserHistoryEmptyQuery;
import com.code42.utils.SystemProperties;
import com.code42.utils.SystemProperty;
import com.code42.utils.SystemPropertyValues;
import com.google.inject.Inject;

/**
 * Retrieve information about the current server environment
 */
public class ServerEnvDtoFindCmd extends DBCmd<ServerEnvDto> {

	private static final Logger log = LoggerFactory.getLogger(ServerEnvDtoFindCmd.class);

	@Inject
	private ISystemAlertService alertSvc;

	@Inject
	private ICryptoService crypto;

	@Inject
	private IEnvironment env;

	@Override
	public ServerEnvDto exec(CoreSession session) throws CommandException {

		log.info("Finding ServerEnvDto");

		ServerEnvDto dto = new ServerEnvDto();

		dto.setVersion(CPVersion.getVersion());
		dto.setProductVersion(CPVersion.getProductVersion());
		dto.setClientProductVersion(CPVersion.getClientProductVersion());
		dto.setMaster(this.env.isMaster());
		dto.setBusinessCluster(this.env.isBusinessCluster());
		dto.setConsumerCluster(this.env.isConsumerCluster());
		dto.setServerId(this.env.getMyNodeId());
		dto.setClusterServerId(this.env.getMyClusterId());
		dto.setServerGuid(this.env.getMyNodeGuid());
		dto.setDefaultAdminPassword(this.alertSvc.hasAlert(SystemAlert.Type.ADMIN_PASSWORD_UNCHANGED, null));
		// User may choose to not set the admin email address. All we *really* care about is if there
		// are some system administrators with their email set so we can send them system alerts.
		dto.setAdminEmailSet(!this.alertSvc.hasAlert(SystemAlert.Type.SYSTEM_ALERT_RECIPIENTS_MISSING, null));

		String buildEnv = SystemProperties.getOptional(SystemProperty.BUILD_ENV, SystemPropertyValues.BUILD_ENV_DEV);
		dto.setBuildEnv(buildEnv);

		int portPrefix = Integer.valueOf(SystemProperties.getRequired(SystemProperty.SERVER_PORT_PREFIX));
		dto.setPortPrefix(portPrefix);

		dto.setSSLRequired(this.env.isSslRequired());

		MasterLicense mlk = MasterLicenseService.getInstance().getMasterLicense();
		dto.setHasMasterLicenseKey(mlk != null);
		if (mlk != null) {
			dto.setMlk(mlk.getLicense());
		}

		Boolean userHistoryEmpty = this.db.find(new UserHistoryEmptyQuery());
		dto.setHasAnyoneLoggedIn(!userHistoryEmpty);

		dto.setQueryLimited(SystemProperties.getOptionalBoolean(SystemProperty.QUERY_LIMIT, false));
		dto.setStatsEnabled(SystemProperties.getOptionalBoolean(SystemProperty.REALTIME_ALL_ENABLED, true));

		dto.setSecureKeystoreLocked(this.crypto.isLocked());

		if (!this.env.isCpCentral()) {
			// SSO server - we're only using ID 1 right now... maybe forever
			SsoAuth ssoAuth = this.db.find(new SsoAuthFindByIdQuery(SsoAuth.SERVER_SSO_AUTH));
			if (ssoAuth != null && ssoAuth.isEnabled()) {
				dto.setSsoAuthProvider(ssoAuth.getSsoAuthName());

				// Considered enabled when enabled and has at least one org configured
				Integer orgSsoAuthCount = this.db.find(new OrgSsoAuthFindCountQuery());
				if (orgSsoAuthCount != null && orgSsoAuthCount > 0) {
					dto.setSsoAuthEnabled(true);
				}
			}
		}

		int securePort = this.serverService.getMyNode().getSecurePort();
		dto.setHttpsPort(securePort);

		return dto;
	}
}
