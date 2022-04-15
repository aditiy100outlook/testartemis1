/*
 * <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.radius;

import com.backup42.common.perm.C42PermissionPro;
import com.backup42.history.CpcHistoryLogger;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.NotFoundException;
import com.code42.core.impl.DBCmd;
import com.code42.encryption.EncryptionServices;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.user.RadiusServer;
import com.code42.utils.LangUtils;
import com.code42.utils.option.None;
import com.code42.utils.option.Some;

/**
 * Validate and update all the fields on the given RadiusServer, ensuring that the radiusServerUid field is populated.<br>
 */
public class RadiusServerUpdateCmd extends DBCmd<RadiusServer> {

	private static final Logger log = LoggerFactory.getLogger(RadiusServerUpdateCmd.class);

	private Builder data = null;

	/**
	 * Use the Builder static inner class to construct one of these.
	 */
	private RadiusServerUpdateCmd(Builder b) {
		this.data = b;
	}

	@Override
	public RadiusServer exec(CoreSession session) throws CommandException {

		this.ensureNotCPCentral();

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		RadiusServer radiusServer = null;

		this.db.beginTransaction();

		try {

			radiusServer = this.db.find(new RadiusServerFindByIdQuery(this.data.radiusServerId));

			if (radiusServer == null || radiusServer.getRadiusServerId() == null) {
				throw new NotFoundException("RADIUS:: Unable to update RadiusServer; key value is null or invalid: "
						+ this.data.radiusServerId);
			}

			if (this.data.radiusServerName instanceof Some<?>) {
				radiusServer.setRadiusServerName(this.data.radiusServerName.get());
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

			if (!(this.data.attributeString instanceof None)) {
				radiusServer.setAttributes(this.data.attributeString.get());
			}

			if (this.data.timeoutSeconds instanceof Some<?>) {
				radiusServer.setTimeoutSeconds(this.data.timeoutSeconds.get());
			}

			radiusServer = this.db.update(new RadiusServerUpdateQuery(radiusServer));

			this.db.commit();

			CpcHistoryLogger.info(session, "RADIUS:: server modified: {}", radiusServer);

		} catch (CommandException e) {
			this.db.rollback();
			throw e;
		} catch (Throwable e) {
			this.db.rollback();
			log.error("RADIUS:: Unexpected: ", e);
		} finally {
			this.db.endTransaction();
		}

		return radiusServer;
	}

	public static class Builder extends RadiusServerBuilder<Builder, RadiusServerUpdateCmd> {

		/* This value must always be present; it's the only way to get a builder */
		public int radiusServerId = 0;

		public Builder(int radiusServerId) {
			this.radiusServerId = radiusServerId;
		}

		@Override
		public RadiusServerUpdateCmd build() throws BuilderException {
			this.validate();
			return new RadiusServerUpdateCmd(this);
		}
	}

}
