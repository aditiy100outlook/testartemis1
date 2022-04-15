package com.code42.license;

import com.code42.core.db.DBServiceException;

/**
 * Licenses that are anonymous need their own holder imlementation.
 */
public class AnonymousLicenseHolder extends LicenseHolder {

	public AnonymousLicenseHolder() {
		super();
	}

	@Override
	public void applyId(License license) {
		// do nothing; there is no id to apply
	}

	@Override
	public void applyId(GiftLicense gift) {
		// do nothing; there is no id to apply
	}

	@Override
	public void notifyOfLicenseChange(License license) {
		// do nothing; there is no one to notify
	}

	@Override
	public License handleAssignment(License license, boolean override) throws DBServiceException {
		// do nothing; there is no one to notify
		return license;
	}

}
