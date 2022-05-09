package com.code42.license;

import java.util.ArrayList;
import java.util.List;

import com.backup42.app.license.MasterLicenseService;
import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.product.Product;
import com.code42.product.ProductFindByIdQuery;
import com.code42.server.license.ProductLicense;

/**
 * Find a Dto for all VALID ProductLicenses.
 */
public class ProductLicenseDtoFindCmd extends DBCmd<List<ProductLicenseDto>> {

	private final static Logger log = Logger.getLogger(ProductLicenseDtoFindCmd.class);

	private final MasterLicenseService masterLicenseService = MasterLicenseService.getInstance();

	public ProductLicenseDtoFindCmd() {
		super();
	}

	@Override
	public List<ProductLicenseDto> exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.ALL);

		final List<ProductLicenseDto> dtos = new ArrayList<ProductLicenseDto>();

		final List<ProductLicense> pls = this.masterLicenseService.getProductLicenses();
		for (ProductLicense pl : pls) {
			try {
				pl.validate();
				if (!pl.isValid()) {
					// skip this one
					continue;
				}

				final Product product = this.db.find(new ProductFindByIdQuery(pl.getProductId()));
				dtos.add(new ProductLicenseDto(pl, product));
			} catch (Exception e) {
				log.warn("Failed to validate/wrap a ProductLicense. Forced to skip", e, pl);
			}
		}

		return dtos;
	}
}
