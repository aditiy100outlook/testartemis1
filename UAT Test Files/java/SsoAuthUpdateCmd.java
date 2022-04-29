/*
 * <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.ssoauth;

import org.hibernate.Session;

import com.backup42.common.perm.C42PermissionPro;
import com.backup42.history.CpcHistoryLogger;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.UpdateQuery;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.utils.option.Some;
import com.google.inject.Inject;

/**
 * Validate and update fields on the given SsoAuth server.<br>
 */
public class SsoAuthUpdateCmd extends DBCmd<SsoAuth> {

	private static final Logger log = LoggerFactory.getLogger(SsoAuthUpdateCmd.class);

	@Inject
	private ICoreSsoAuthService ssoAuthService;

	private Builder data = null;

	/**
	 * Use the Builder static inner class to construct one of these.
	 */
	private SsoAuthUpdateCmd(Builder b) {
		this.data = b;
	}

	@Override
	public SsoAuth exec(CoreSession session) throws CommandException {

		this.ensureNotCPCentral();

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		SsoAuth ssoAuth = null;

		this.db.beginTransaction();

		try {

			ssoAuth = this.db.find(new SsoAuthFindByIdQuery(SsoAuth.SERVER_SSO_AUTH));

			// Lazily Create the SsoAuth row if it doesn't exist.
			if (ssoAuth == null) {
				final SsoAuthCreateCmd.Builder create = new SsoAuthCreateCmd.Builder();

				create.ssoAuthName(this.data.ssoAuthName.get());

				if (this.data.identityProviderMetadata instanceof Some<?>) {
					create.identityProviderMetadata(this.data.identityProviderMetadata.get());
				}

				if (this.data.identityProviderMetadataUrl instanceof Some<?>) {
					create.identityProviderMetadataUrl(this.data.identityProviderMetadataUrl.get());
				}

				if (this.data.enabled instanceof Some<?>) {
					create.enabled(this.data.enabled.get());
				}
				ssoAuth = this.run(create.build(), session);

				this.commit();
				CpcHistoryLogger.info(session, "SsoAuth:: create SsoAuth: {}", ssoAuth);

				return ssoAuth;
			}

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
			}

			ssoAuth = this.db.update(new SsoAuthUpdateQuery(ssoAuth));

			this.commit();

			CpcHistoryLogger.info(session, "SsoAuth:: modified SsoAuth: {}", ssoAuth);

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

	private void commit() throws CommandException {
		super.db.commit();
		this.ssoAuthService.reset();
	}

	public static class Builder extends SsoAuthBuilder<Builder, SsoAuthUpdateCmd> {

		public Builder() {
		}

		@Override
		public SsoAuthUpdateCmd build() throws BuilderException {
			this.validate();
			return new SsoAuthUpdateCmd(this);
		}
	}

	/**
	 * Provides basic updating ability.
	 */
	class SsoAuthUpdateQuery extends UpdateQuery<SsoAuth> {

		final SsoAuth ssoAuth;

		public SsoAuthUpdateQuery(SsoAuth ssoAuth) {
			this.ssoAuth = ssoAuth;
		}

		@Override
		public SsoAuth query(Session session) throws DBServiceException {
			if (this.ssoAuth != null && this.ssoAuth.getSsoAuthId() != null) {
				session.update(this.ssoAuth);
				return this.ssoAuth;
			} else {
				throw new DBServiceException("Error Updating SsoAuth; invalid argument: " + this.ssoAuth);
			}
		}
	}

}
