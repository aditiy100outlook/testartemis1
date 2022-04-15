/*
 * <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.license;

import com.backup42.history.CpcHistoryLogger;
import com.code42.core.CommandException;
import com.code42.core.auth.IAuthorizationService;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.Core;
import com.code42.core.impl.DBCmd;
import com.code42.perm.C42PermissionBase;
import com.code42.server.license.LicenseException;
import com.google.inject.Inject;

/**
 * Validates and adds/changes a mlk
 */
public class MasterLicenseKeyChangeCmd extends DBCmd<MasterLicenseDto> {

	@Inject
	private IAuthorizationService auth;

	private String mlk;

	public enum Error {
		INVALID_MLK, LICENSE_EXCEPTION
	}

	public MasterLicenseKeyChangeCmd(String mlk) {
		super(false); // no parent session
		this.mlk = mlk;
	}

	@Override
	public MasterLicenseDto exec(CoreSession session) throws CommandException {

		this.ensureNotCPCentral();

		this.auth.isAuthorized(session, C42PermissionBase.Admin.ALL);

		try {
			final boolean recorded = this.run(new MasterLicenseKeyRecordNewCmd(this.mlk), session);

			if (!recorded) {
				throw new CommandException(Error.INVALID_MLK, "Invalid MLK");
			} else {
				final String prefix = this.mlk.length() > 16 ? this.mlk.substring(0, 15) : "";
				CpcHistoryLogger.info(session, "Master license key added - key prefix=" + prefix + "; by user="
						+ session.getUser());
			}
		} catch (LicenseException e) {
			throw new CommandException(Error.LICENSE_EXCEPTION, "License Exception");
		}

		// ensure there aren't any cached objects affecting us; we need the new object
		this.db.ensureNoSession();

		Core.writeAppLog();
		final MasterLicenseDto dto = this.runtime.run(new MasterLicenseDtoFindCmd(), session);
		return dto;
	}
}
