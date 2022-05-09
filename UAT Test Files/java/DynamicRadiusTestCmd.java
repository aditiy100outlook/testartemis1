package com.code42.radius;

import java.util.concurrent.TimeUnit;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.core.radius.IRADIUSService;
import com.code42.core.radius.RADIUSCommunicationException;
import com.code42.core.radius.RADIUSException;
import com.code42.core.radius.RADIUSSpec;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.utils.LangUtils;
import com.google.inject.Inject;

/**
 * Perform a lookup of a testing account against an LDAP directory. <br>
 */
public class DynamicRadiusTestCmd extends AbstractCmd<Void> {

	private static final Logger log = LoggerFactory.getLogger(DynamicRadiusTestCmd.class);

	/* ================= Dependencies ================= */
	private IRADIUSService radius;

	/* ================= DI injection points ================= */
	@Inject
	public void setRadius(IRADIUSService arg) {
		this.radius = arg;
	}

	private Builder builder;

	private DynamicRadiusTestCmd(Builder builder) {
		this.builder = builder;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		RADIUSSpec spec = new RADIUSSpec(this.builder.address.get(), this.builder.sharedSecret.get(),
				this.builder.attributes);

		try {
			this.radius.status(spec, this.builder.timeoutSeconds.get(), TimeUnit.SECONDS, false/* alert */);
			log.debug("RADIUS server {} is up and healthy", spec);
		} catch (RADIUSCommunicationException e) {
			// Do not log the stack for this exception. It's expected when the server is not up.
			log.info("Error communicating with RADIUS server {} {}", spec, e.getMessage());
			throw new CommandException("Error communicating with RADIUS server {}", spec, e);
		} catch (RADIUSException e) {
			log.info("Error testing RADIUS server {}", spec, e);
			throw new CommandException("Error testing RADIUS server {}", spec, e);
		}

		return null;
	}

	public static class Builder extends RadiusServerBuilder<Builder, DynamicRadiusTestCmd> {

		@Override
		public void validate() throws BuilderException {
			super.validate();

			/*
			 * The super implementation does not throw an error if the option is NONE. So this does
			 */

			if (!LangUtils.hasValue(this.address)) {
				throw new BuilderException(Error.MISSING_ADDRESS, "address cannot be null");
			}

			if (!LangUtils.hasValue(this.sharedSecret)) {
				throw new BuilderException(Error.MISSING_SHARED_SECRET, "shared secret cannot be null");
			}

			if (!LangUtils.hasValue(this.attributeString)) {
				throw new BuilderException(Error.MISSING_ATTRIBUTES,
						"One of these attributes is required: NAS-IP-Address or NAS-Identifier");
			}
		}

		@Override
		public DynamicRadiusTestCmd build() throws BuilderException {
			this.validate();
			return new DynamicRadiusTestCmd(this);
		}
	}

}
