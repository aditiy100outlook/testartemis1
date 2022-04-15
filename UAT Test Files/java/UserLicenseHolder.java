package com.code42.license;

import com.backup42.computer.LicenseServices;
import com.code42.core.db.DBServiceException;
import com.code42.user.User;

/**
 * A user-owned license.
 */
public class UserLicenseHolder extends LicenseHolder {

	private static final LicenseServices licServices = LicenseServices.getInstance();

	private final User user;

	UserLicenseHolder(User user) {
		super();
		this.user = user;
	}

	@Override
	public void applyId(License license) {

		final UserLicense ul = (UserLicense) license;
		ul.setUserId(this.user.getUserId());
	}

	@Override
	public void applyId(GiftLicense gift) {
		gift.setUserId(this.user.getUserId());
	}

	@Override
	public void notifyOfLicenseChange(License license) {
		licServices.handleLicenseChangeForUser(this.user);
	}

	@Override
	public License handleAssignment(License license, boolean override) throws DBServiceException {

		// do nothing

		return license;
	}

}
