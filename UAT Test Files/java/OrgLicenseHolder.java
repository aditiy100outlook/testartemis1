package com.code42.license;

import com.backup42.computer.LicenseServices;
import com.code42.commerce.product.ProductType;
import com.code42.core.db.DBServiceException;
import com.code42.org.BackupOrg;
import com.code42.org.Org;
import com.code42.org.OrgLicenseAssignmentUpdateQuery;
import com.code42.product.Product;
import com.code42.product.ProductFindByIdQuery;
import com.code42.utils.LangUtils;

/**
 * A license held by an org.
 */
public class OrgLicenseHolder extends LicenseHolder {

	private final Org org;

	OrgLicenseHolder(Org org) {
		super();
		this.org = org;
	}

	@Override
	public void applyId(License license) {

		OrgLicense ol = (OrgLicense) license;
		ol.setOrgId(this.org.getOrgId());
	}

	@Override
	public void applyId(GiftLicense gift) {
		gift.setOrgId(this.org.getOrgId());
	}

	@Override
	public void notifyOfLicenseChange(License license) {
		LicenseServices.getInstance().handleLicenseChangeForOrg(this.org);
	}

	@Override
	public License handleAssignment(License license, boolean override) throws DBServiceException {

		// default to values that accept the newly assigned license
		boolean freeTrial = true;
		boolean outOfDate = true;

		// when the org is first being created, no license is assigned. in that case, we'll accept any license that comes
		// our way as a distinct improvement
		final License curLic = this.db.find(new LicenseFindCurrentByOrgQuery((BackupOrg) this.org));
		if (curLic != null) {
			final Product p = this.db.find(new ProductFindByIdQuery(curLic.getProductId()));

			freeTrial = LangUtils.in(p.getType(), ProductType.Category.HOSTED_TRIAL);
			outOfDate = !curLic.isActive() || curLic.isCancelled();
		}

		// we'll assign the license to the org's current license slot if any of these options are true. free trials can be
		// overridden by a purchase, inactive subs are useless and can go away, and an override directive takes precedence.
		final boolean assignToCurrent = freeTrial || outOfDate || override;

		// the extra step we perform is to set this license into the backup org; there's a special query for doing that
		// too. the caller doesn't know about our org model change, so we're responsible for saving it to the db on our
		// own.
		if (assignToCurrent) {
			final BackupOrg bo = (BackupOrg) this.org;
			bo.setLicenseId(license.getLicenseId());
			this.db.update(new OrgLicenseAssignmentUpdateQuery(this.org, license));
		}

		return license;
	}

}
