package com.code42.license;

import com.code42.core.CommandException;

public class LicenseUnavailableException extends CommandException {

	private static final long serialVersionUID = 3296089785694104067L;

	private final boolean userLicense; // user or computer license?
	private final String name; // The name of the user or computer who owns the license
	private final long cpcArchiveSizeInBytes;
	private final int computerCount; // number of computers the transfer may affect

	public LicenseUnavailableException(String msg, boolean userLicense, String name, long cpcArchiveSizeInBytes,
			int computerCount) {
		super(msg);
		this.userLicense = userLicense;
		this.name = name;
		this.cpcArchiveSizeInBytes = cpcArchiveSizeInBytes;
		this.computerCount = computerCount;
	}

	public boolean isUserLicense() {
		return this.userLicense;
	}

	public String getName() {
		return this.name;
	}

	public long getCpcArchiveSizeInBytes() {
		return this.cpcArchiveSizeInBytes;
	}

	public int getComputerCount() {
		return this.computerCount;
	}
}
