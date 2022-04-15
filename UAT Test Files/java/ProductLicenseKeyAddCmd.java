/*
 * <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.license;

import java.util.List;

import com.backup42.app.license.MasterLicenseService;
import com.backup42.history.CpcHistoryLogger;
import com.code42.commerce.product.ProductConstants.BlackProductRef;
import com.code42.core.CommandException;
import com.code42.core.auth.IAuthorizationService;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.Core;
import com.code42.core.impl.DBCmd;
import com.code42.perm.C42PermissionBase;
import com.code42.product.Product;
import com.code42.product.ProductFindByIdQuery;
import com.code42.server.license.LicenseException;
import com.code42.server.license.MasterLicense;
import com.code42.server.license.ProductLicense;
import com.code42.utils.Base64;
import com.code42.utils.LangUtils;
import com.google.inject.Inject;

/**
 * Validates and adds a pl
 */
public class ProductLicenseKeyAddCmd extends DBCmd<List<ProductLicenseDto>> {

	@Inject
	private IAuthorizationService auth;

	private String pl;

	public enum Error {
		INVALID_PL, LICENSE_EXCEPTION
	}

	public ProductLicenseKeyAddCmd(String pl) {
		this.pl = pl;
	}

	@Override
	public List<ProductLicenseDto> exec(CoreSession session) throws CommandException {

		this.ensureNotCPCentral();

		this.auth.isAuthorized(session, C42PermissionBase.Admin.ALL);

		try {
			MasterLicenseService manager = MasterLicenseService.getInstance();
			// scrub the string before using
			this.pl = Base64.scrubString(this.pl);

			ProductLicense prodLicense = ProductLicense.generateInstance(this.pl.toString());
			MasterLicense tempMl = manager.getMasterLicense();
			prodLicense.setMasterLicense(tempMl);

			if (prodLicense.validate().getStatus() == com.code42.server.license.License.VALIDATION_SUCCESSFUL) {

				Product product = this.db.find(new ProductFindByIdQuery(prodLicense.getProductId()));
				if (!LangUtils.in(product.getReferenceId(), BlackProductRef.VALID_PRODUCTS)) {
					throw new CommandException(Error.INVALID_PL, "Invalid PL");
				}

				manager.addProductLicense(prodLicense);
				final String prefix = this.pl.length() > 16 ? this.pl.substring(0, 15) : "";
				CpcHistoryLogger.info(session, "Product license added - key prefix=" + prefix + "; " + prodLicense
						+ "; by user=" + session.getUser());
			} else {
				throw new CommandException(Error.INVALID_PL, "Invalid PL");
			}
		} catch (LicenseException e) {
			throw new CommandException(Error.LICENSE_EXCEPTION, "License Exception");
		}

		List<ProductLicenseDto> dto = this.runtime.run(new ProductLicenseDtoFindCmd(), session);
		Core.writeAppLog();
		return dto;
	}
}