package com.code42.ssoauth;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.io.IOUtils;
import org.opensaml.saml2.metadata.EntityDescriptor;

import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.ssoauth.saml.SamlUtils;
import com.code42.utils.ExpiringCache;
import com.code42.utils.InvalidEntryException;
import com.code42.utils.LangUtils;
import com.code42.utils.SystemProperties;
import com.code42.utils.Time;

/**
 * Fetches the {@link SsoAuth} metadata from the metadata URL.
 */
public class SsoAuthFetchMetadataCmd extends AbstractCmd<String> {

	private static final Logger log = LoggerFactory.getLogger(SsoAuthFetchMetadataCmd.class);

	private static final ExpiringCache rateLimitCache = ExpiringCache.getInstance();
	private static final String RATE_LIMIT = "SsoAuthFetchMetadataRateLimit";

	private SsoAuth ssoAuth;

	public SsoAuthFetchMetadataCmd(SsoAuth ssoAuth) {
		this.ssoAuth = ssoAuth;
	}

	/**
	 * Fetches the updated metadata from the SSO server.
	 * 
	 * @param ssoServer an SSO Server
	 * @return the updated metadata or null if not found or invalid.
	 */
	@Override
	public String exec(CoreSession session) throws CommandException {

		if (this.ssoAuth != null) {
			String metadataUrl = this.ssoAuth.getIdentityProviderMetadataUrl();

			if (!LangUtils.hasValue(metadataUrl)) {
				log.error("SsoAuth:: SSO Server does not have a url set.");
				throw new CommandException("SSO Server does not have a url set.");
			}
			log.debug("SsoAuth:: Using server URL {}", metadataUrl);

			if (!this.canFetchMetadata(metadataUrl)) {
				log.error("SsoAuth:: Cannot sync the metadata for URL {} again due to rate limiting.", metadataUrl);
				// Not throwing an exception so that saves can continue
				return null;
			}

			try {
				// Fetch the new metadata
				URL url = new URL(metadataUrl);
				InputStream in = url.openStream();
				String metadata = IOUtils.toString(in, "UTF-8"); // Read the URL into a string
				IOUtils.closeQuietly(in);
				log.trace("SsoAuth:: Recieved the server metadata: \n{}", metadata);

				this.validate(metadata);

				return metadata;
			} catch (MalformedURLException e) {
				log.error("SsoAuth:: The server URL {} is malformed", metadataUrl, e);
				throw new CommandException("The server URL {} is malformed", metadataUrl, e);
			} catch (IOException e) {
				log.error("SsoAuth:: Error opening a connection to the server {}", metadataUrl, e);
				throw new CommandException("Error opening a connection to the server {}", metadataUrl, e);
			}
		}

		return null;
	}

	/**
	 * Returns whether the metadata is a valid EntityDescriptor or not.
	 * 
	 * @param metadata
	 * @throws BuilderException if the metadata is invalid
	 */
	private void validate(String metadata) throws BuilderException {
		// Validate that it is actually an EntityDescriptor
		EntityDescriptor xml = SamlUtils.parseSamlObject(metadata);
		log.debug("SsoAuth:: Successfully validated metadata for server with entityId {}", xml.getEntityID());
	}

	/**
	 * Determines if the system can sync the metadata.
	 * 
	 * Since sycning metadata fetches and processes XML from an external URL, this is limited to once a minute, except in
	 * development.
	 */
	private synchronized boolean canFetchMetadata(String url) {
		if (SystemProperties.isDevEnv()) {
			return true;
		}

		Object rateLimited = rateLimitCache.get(RATE_LIMIT, url);
		if (rateLimited == null) {
			try {
				rateLimitCache.put(RATE_LIMIT, url, new Object(), System.currentTimeMillis() + 60 * Time.SECOND);
			} catch (InvalidEntryException e) {
				log.error("SsoAuth:: Failed to udpate the rate limit the for the metadata sync", e);
			}
			return true;
		}

		log.warn("SsoAuth:: Not syncing SSO metadata at this moment due to rate limiting");

		return false;
	}

}
