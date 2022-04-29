package com.code42.license;

import com.code42.core.CommandException;

/**
 * The license can't be assigned to a computer.
 */
public class IneligibleLicenseException extends CommandException {

	private static final long serialVersionUID = -5742330029921703908L;

	public IneligibleLicenseException(String str, Object... objects) {
		super(str, objects);
	}

	public IneligibleLicenseException(String str, Throwable t, Object... objects) {
		super(str, t, objects);
	}

	public IneligibleLicenseException(String str, Throwable t) {
		super(str, t);
	}

	public IneligibleLicenseException(String str) {
		super(str);
	}

	public IneligibleLicenseException(Throwable t) {
		super(t);
	}
}
