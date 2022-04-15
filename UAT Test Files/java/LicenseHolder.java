package com.code42.license;

import com.code42.core.ICoreRuntime;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.IDBService;

/**
 * The license holder is just that- the actor that owns your license. The holder is responsible for any impls that
 * require knowledge of WHO or WHAT owns your license.
 */
public abstract class LicenseHolder {

	protected ICoreRuntime runtime;
	protected IDBService db;

	/**
	 * Dependency.
	 */
	public void setRuntime(ICoreRuntime runtime) {
		this.runtime = runtime;
	}

	/**
	 * Dependency.
	 */
	public void setDb(IDBService db) {
		this.db = db;
	}

	/**
	 * Apply the holder's identity to the license.
	 */
	public abstract void applyId(License license);

	/**
	 * Apply the holder's identity to the gift; thereby marking it as redeemed by this license holder.
	 */
	public abstract void applyId(GiftLicense gift);

	/**
	 * The provided license is being assigned to you. Take any action necessary.
	 * 
	 * It must be guaranteed that the license has been saved to the db and has an id. External references may be created
	 * at this point.
	 * 
	 * Does not update the license. The caller is responsible for saving the license entity.
	 * 
	 * @param override assign this license to yourself, now.
	 */
	public abstract License handleAssignment(License license, boolean override) throws DBServiceException;

	/**
	 * Notfiy any affected parties that the license has (potentially) been updated.
	 */
	public abstract void notifyOfLicenseChange(License license);

}
