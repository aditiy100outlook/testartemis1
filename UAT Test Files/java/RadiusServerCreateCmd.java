/*
 * <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.radius;

import com.backup42.common.perm.C42PermissionPro;
import com.backup42.history.CpcHistoryLogger;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.encryption.EncryptionServices;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.user.RadiusServer;
import com.code42.utils.LangUtils;
import com.code42.utils.option.Some;

/**
 * Validate and update all the fields on the given RadiusServer, ensuring that the ldapServerUid field is populated.<br>
 */
public class RadiusServerCreateCmd extends DBCmd<RadiusServer> {

	private static final Logger log = LoggerFactory.getLogger(RadiusServerCreateCmd.class);

	private Builder data = null;

	/**
	 * Use the Builder static inner class to construct one of these.
	 */
	private RadiusServerCreateCmd(Builder b) {
		this.data = b;
	}

	@Override
	public RadiusServer exec(CoreSession session) throws CommandException {

		this.ensureNotCPCentral();

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		RadiusServer radiusServer = null;

		this.db.beginTransaction();

		try {

			radiusServer = new RadiusServer();

			if (this.data.radiusServerName instanceof Some<?>) {
				radiusServer.setRadiusServerName(this.data.radiusServerName.get());
			}

			if (this.data.address instanceof Some<?>) {
				radiusServer.setAddress(this.data.address.get());
			}

			if (this.data.address instanceof Some<?>) {
				radiusServer.setAddress(this.data.address.get());
			}

			if (this.data.sharedSecret instanceof Some<?>) {
				String encrypted = this.data.sharedSecret.get();
				if (LangUtils.hasValue(encrypted)) {
					encrypted = EncryptionServices.getCrypto().encrypt(encrypted);
				}
				radiusServer.setSharedSecret(encrypted);
			}

			if (this.data.timeoutSeconds instanceof Some<?>) {
				radiusServer.setTimeoutSeconds(this.data.timeoutSeconds.get());
			}

			radiusServer = this.db.create(new RadiusServerCreateQuery(radiusServer));

			this.db.commit();

			CpcHistoryLogger.info(session, "RADIUS:: new server created: {}", radiusServer);

		} catch (CommandException e) {
			this.db.rollback();
			throw e;
		} catch (Throwable e) {
			this.db.rollback();
			log.error("Unexpected: ", e);
		} finally {
			this.db.endTransaction();
		}

		return radiusServer;
	}

	public static class Builder extends RadiusServerBuilder<Builder, RadiusServerCreateCmd> {

		@Override
		public void validate() throws BuilderException {
			super.validate();

			/*
			 * The super implementation does not throw an error if the option is NONE. So this does
			 */

			if (!LangUtils.hasValue(this.address)) {
				throw new BuilderException(Error.MISSING_ADDRESS, "RADIUS address must have a value");
			}

			if (!LangUtils.hasValue(this.sharedSecret)) {
				throw new BuilderException(Error.MISSING_SHARED_SECRET, "RADIUS shared secret must have a value");
			}

			if (!LangUtils.hasValue(this.attributeString)) {
				throw new BuilderException(Error.MISSING_ATTRIBUTES,
						"One of these attributes is required: NAS-IP-Address or NAS-Identifier");
			}
		}

		@Override
		public RadiusServerCreateCmd build() throws BuilderException {
			this.validate();
			return new RadiusServerCreateCmd(this);
		}
	}

}
