/*
 * <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.ssoauth;

import java.io.ByteArrayOutputStream;
import java.security.KeyStore;

import org.hibernate.Session;

import com.backup42.common.perm.C42PermissionPro;
import com.backup42.history.CpcHistoryLogger;
import com.code42.auth.PasswordUtil;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.CreateQuery;
import com.code42.core.impl.DBCmd;
import com.code42.core.security.ICryptoService;
import com.code42.keystore.Keystore;
import com.code42.keystore.KeystoreCreateCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.utils.option.Some;
import com.google.inject.Inject;

/**
 * Validate and update all the fields on the given SsoAuth.<br>
 */
public class SsoAuthCreateCmd extends DBCmd<SsoAuth> {

	private static final Logger log = LoggerFactory.getLogger(SsoAuthCreateCmd.class);

	private Builder data = null;

	@Inject
	private ICryptoService crypto;

	/**
	 * Use the Builder static inner class to construct one of these.
	 */
	private SsoAuthCreateCmd(Builder b) {
		this.data = b;
	}

	@Override
	public SsoAuth exec(CoreSession session) throws CommandException {

		this.ensureNotCPCentral();

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		SsoAuth ssoAuth = null;

		this.db.beginTransaction();

		try {

			ssoAuth = new SsoAuth();

			if (this.data.ssoAuthName instanceof Some<?>) {
				ssoAuth.setSsoAuthName(this.data.ssoAuthName.get());
			}

			if (this.data.identityProviderMetadata instanceof Some<?>) {
				ssoAuth.setIdentityProviderMetadata(this.data.identityProviderMetadata.get());
			}

			if (this.data.identityProviderMetadataUrl instanceof Some<?>) {
				ssoAuth.setIdentityProviderMetadataUrl(this.data.identityProviderMetadataUrl.get());
			}

			if (this.data.enabled instanceof Some<?>) {
				ssoAuth.setEnabled(this.data.enabled.get());
			} else {
				// Default to true
				ssoAuth.setEnabled(true);
			}

			final String password = PasswordUtil.generatePassword(); // Use a random password for now, 20 characters long.
			final KeyStore jks = this.crypto.createDefaultKeystore(password);
			final boolean storePassword = true;
			final byte[] jksBytes = toBytes(jks, password);
			final Keystore keystore = this.run(new KeystoreCreateCmd(jksBytes, password, storePassword), session);
			ssoAuth.setKeystoreId(keystore.getKeystoreId());

			ssoAuth = this.db.create(new SsoAuthCreateQuery(ssoAuth));

			this.db.commit();

			CpcHistoryLogger.info(session, "Sso:: new server: {}", ssoAuth);

		} catch (CommandException e) {
			this.db.rollback();
			throw e;
		} catch (Throwable e) {
			this.db.rollback();
			log.error("Unexpected: ", e);
		} finally {
			this.db.endTransaction();
		}

		return ssoAuth;
	}

	static byte[] toBytes(KeyStore jks, String password) throws CommandException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			jks.store(out, password.toCharArray());
		} catch (Exception e) {
			throw new CommandException("Failed creating Java KeyStore.");
		}
		return out.toByteArray();
	}

	public static class Builder extends SsoAuthBuilder<Builder, SsoAuthCreateCmd> {

		@Override
		public SsoAuthCreateCmd build() throws BuilderException {
			this.validate();
			return new SsoAuthCreateCmd(this);
		}
	}

	/**
	 * Provides basic create ability.
	 */
	class SsoAuthCreateQuery extends CreateQuery<SsoAuth> {

		final SsoAuth ssoAuth;

		public SsoAuthCreateQuery(SsoAuth s) {
			this.ssoAuth = s;
			s.setSsoAuthId(1); // Only ID one is allowed right now
		}

		@Override
		public SsoAuth query(Session session) throws DBServiceException {
			if (this.ssoAuth != null) {
				session.save(this.ssoAuth);
				return this.ssoAuth;
			} else {
				throw new DBServiceException("Error Creating object; invalid argument: " + this.ssoAuth);
			}
		}
	}
}
