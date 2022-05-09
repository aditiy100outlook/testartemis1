/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.server;

import com.code42.core.CommandException;

/**
 * Thrown by the NodeCreateCmd when attempting to attach a StorageNode to this Master. If the given connection string is
 * from a Master with a different MLK, this exception will be thrown.
 */
public class InvalidMasterLicenseKeyException extends CommandException {

	public static enum Error {
		INVALID_MASTER_LICENSE_KEY
	}

	private static final long serialVersionUID = 2777918744555766304L;

	public InvalidMasterLicenseKeyException(String str, Object... objects) {
		super(Error.INVALID_MASTER_LICENSE_KEY, str, objects);
	}

}
