package com.code42.ssoauth;

import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.utils.LangUtils;

/**
 * Refreshes the metadata for all the {@link SsoAuth} servers.
 * 
 * For SAML, if the server has a URL defined, the metadata will be updated with the contents of that URL.
 * 
 * @see SsoAuth
 */
public class SsoAuthMetadataSyncCmd extends DBCmd<Void> {

	private static final Logger log = LoggerFactory.getLogger(SsoAuthMetadataSyncCmd.class);

	@Override
	public Void exec(CoreSession session) {

		List<SsoAuth> servers = null;
		try {
			servers = this.db.find(new SsoAuthFindAllQuery());
		} catch (CommandException e) {
			log.error("SsoAuth:: Unable to sycn the server metadata", e);
		}

		if (servers != null) {
			log.info("SsoAuth:: Attempting to update {} SSO servers.", servers.size());
			for (SsoAuth server : servers) {

				if (!server.isEnabled()) {
					log.info("SsoAuth:: Skipping disabled SSO server: {}", server);
					break;
				}

				log.info("SsoAuth:: Starting refresh of metadata for SSO server: {}", server);
				String metadata;
				try {
					metadata = this.runtime.run(new SsoAuthFetchMetadataCmd(server), session);
				} catch (CommandException e) {
					log.error("SsoAuth:: Failed to fetch the SsoAuth server metadata.", e);
					break;
				}

				if (metadata == null) {
					if (!LangUtils.hasValue(server.getIdentityProviderMetadata())) {
						log.error("SsoAuth:: No current SsoAuth server metadata set and unable to retrieve metadata.");
					}
					break;
				}

				if (metadata.equals(server.getIdentityProviderMetadata())) {
					log.debug("SsoAuth:: Metadata has not changed.");
					break;
				}

				this.updateServer(server, metadata, session);

				log.info("SsoAuth:: Completed refreshing metadata for SSO server: {}", server);
			}
		}

		return null;
	}

	/**
	 * Updates the server with the provided metadata.
	 * 
	 * @param server an SSO server
	 * @param metadata metadata for an SSO server
	 * @param session the core session for running the update command
	 */
	private void updateServer(SsoAuth server, String metadata, CoreSession session) {
		if (LangUtils.hasValue(metadata)) {
			try {
				log.trace("SsoAuth:: Starting to persist metadata for SSO server: {}", server);
				SsoAuthUpdateCmd.Builder builder = new SsoAuthUpdateCmd.Builder();
				builder.identityProviderMetadata(metadata);
				SsoAuthUpdateCmd updateCmd = builder.build();
				this.runtime.run(updateCmd, session);
				log.trace("SsoAuth:: Completd persisting metadata for SSO server: {}", server);
			} catch (CommandException e) {
				log.error("SsoAuth:: Unable to persist the server metadata", e);
			}
		}
	}

}
