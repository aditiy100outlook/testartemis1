/*
 * <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.license;

import com.backup42.app.license.MasterLicenseService;
import com.code42.backup.central.ICentralService;
import com.code42.core.CommandException;
import com.code42.core.auth.IAuthorizationService;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.perm.C42PermissionBase;
import com.code42.utils.Base64;
import com.google.inject.Inject;

/**
 * Validates and adds a pl
 */
public class ProductLicenseRemoveCmd extends DBCmd<Void> {

	@Inject
	private IAuthorizationService auth;
	@Inject
	private ICentralService centralService;

	private String plkId;

	public enum Error {
		PLKID_DOESNTEXIST, LICENSE_EXCEPTION
	}

	/**
	 * Use the Builder static inner class to construct one of these.
	 */
	private ProductLicenseRemoveCmd(String plkId) {
		this.plkId = plkId;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.ensureNotCPCentral();

		this.auth.isAuthorized(session, C42PermissionBase.Admin.ALL);

		try {
			MasterLicenseService manager = MasterLicenseService.getInstance();
			// scrub the string before using
			this.plkId = Base64.scrubString(this.plkId);

			int idval = Integer.parseInt(this.plkId);
			if (!manager.removeProductLicense(idval)) {
				throw new CommandException(Error.PLKID_DOESNTEXIST, "Cannont find plk Id");
			} else {
				this.centralService.getUI().notifyLicenseChange();
			}
		} catch (Exception e) {
			throw new CommandException(Error.LICENSE_EXCEPTION, "License Exception", e);
		}

		return null;
	}
}