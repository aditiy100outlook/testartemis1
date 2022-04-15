package com.code42.balance;

/**
 * Describe a set of balancer settings.
 */
public class BalanceSettingsDto {

	private boolean enabled = false;
	private int allowedDiskVariancePerc = 10;

	private int localCopyPriority = 100;
	private int remoteCopyPriority = 100;

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public int getAllowedDiskVariancePerc() {
		return this.allowedDiskVariancePerc;
	}

	public void setAllowedDiskVariancePerc(int allowedDiskVariancePerc) {
		this.allowedDiskVariancePerc = allowedDiskVariancePerc;
	}

	public int getLocalCopyPriority() {
		return this.localCopyPriority;
	}

	public void setLocalCopyPriority(int localCopyPriority) {
		this.localCopyPriority = localCopyPriority;
	}

	public int getRemoteCopyPriority() {
		return this.remoteCopyPriority;
	}

	public void setRemoteCopyPriority(int remoteCopyPriority) {
		this.remoteCopyPriority = remoteCopyPriority;
	}

	@Override
	public String toString() {
		return "BalanceSettingsDto [enabled=" + this.enabled + ", allowedDiskVariancePerc=" + this.allowedDiskVariancePerc
				+ ", localCopyPriority=" + this.localCopyPriority + ", remoteCopyPriority=" + this.remoteCopyPriority + "]";
	}
}
