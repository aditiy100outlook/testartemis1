package com.code42.license;

import com.code42.core.CommandException;

public class LicenseDoesNotExistException extends CommandException {

	private static final long serialVersionUID = 3296089785694104067L;

	public LicenseDoesNotExistException(String str, Object... objects) {
		super(str, objects);
	}

	public LicenseDoesNotExistException(String str, Throwable t, Object... objects) {
		super(str, t, objects);
	}

	public LicenseDoesNotExistException(String str, Throwable t) {
		super(str, t);
	}

	public LicenseDoesNotExistException(String str) {
		super(str);
	}

	public LicenseDoesNotExistException(Throwable t) {
		super(t);
	}
}
