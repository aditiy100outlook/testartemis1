package com.code42.computer;

import java.io.Serializable;

/**
 * A Data Transfer Object POJO for passing around a computer's current backup activity.
 */
public class ComputerActivityDto implements Serializable {

	private static final long serialVersionUID = 8401872419534506118L;

	boolean connected = false;
	boolean backingUp = false;
	boolean restoring = false;
	long timeRemainingInMs = 0L;
	long remainingBytes = 0;
	long remainingFiles = 0;

	public boolean isConnected() {
		return this.connected;
	}

	public boolean isBackingUp() {
		return this.backingUp;
	}

	public boolean isRestoring() {
		return this.restoring;
	}

	public long getTimeRemainingInMs() {
		return this.timeRemainingInMs;
	}

	public long getRemainingBytes() {
		return this.remainingBytes;
	}

	public long getRemainingFiles() {
		return this.remainingFiles;
	}

	public void setConnected(boolean connected) {
		this.connected = connected;
	}

	public void setBackingUp(boolean backingUp) {
		this.backingUp = backingUp;
	}

	public void setRestoring(boolean restoring) {
		this.restoring = restoring;
	}

	public void setTimeRemainingInMs(long timeRemainingInMs) {
		this.timeRemainingInMs = timeRemainingInMs;
	}

	public void setRemainingBytes(long remainingBytes) {
		this.remainingBytes = remainingBytes;
	}

	public void setRemainingFiles(int remainingFiles) {
		this.remainingFiles = remainingFiles;
	}

}