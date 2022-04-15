package com.code42.server.mount;

import com.code42.server.destination.Destination;
import com.code42.server.node.Node;
import com.code42.utils.LangUtils;
import com.code42.utils.RoundSafe;

/**
 * Describe a mount and its environment to the outside world.
 */
public class MountPointDto {

	public enum FreeBytesAlert {
		WARNING, CRITICAL
	}

	private final MountPoint mount;

	private long archiveBytes = 0;
	private long selectedBytes = 0;
	private long todoBytes = 0;

	private int backupSessionCount = 0;
	private int assignedComputerCount = 0;
	private int backupComputerCount = 0;
	private int assignedUserCount = 0;
	private int licensedUserCount = 0;
	private int assignedOrgCount = 0;

	private long totalBytes = 0;
	private long freeBytes = 0;
	private FreeBytesAlert freeBytesAlert = null;

	private long coldBytes = 0;

	private boolean online = false;

	private boolean serverOnline = false;

	private String serverName = null;
	private int destinationId = 0;
	private String destinationName = null;

	public MountPointDto(MountPoint mount, Node node, Destination destination) {
		this.mount = mount;
		this.serverName = node.getComputer().getName();
		this.destinationId = destination.getDestinationId();
		this.destinationName = destination.getComputer().getName();
	}

	public int getMountPointId() {
		return this.mount.getMountPointId();
	}

	public String getName() {
		return this.mount.getName();
	}

	public String getNote() {
		return this.mount.getNote();
	}

	public String getPath() {
		return this.mount.getPrefixPath();
	}

	public String getDirectory() {
		return this.mount.getVolumeLabel();
	}

	public String getAbsolutePath() {
		return this.mount.getAbsolutePath();
	}

	public int getServerId() {
		return this.mount.getServerId();
	}

	public String getServerName() {
		return this.serverName;
	}

	public void setServerName(String serverName) {
		this.serverName = serverName;
	}

	public int getDestinationId() {
		return this.destinationId;
	}

	public void setDestinationId(int destinationId) {
		this.destinationId = destinationId;
	}

	public String getDestinationName() {
		return this.destinationName;
	}

	public void setDestinationName(String destinationName) {
		this.destinationName = destinationName;
	}

	public int getBackupSessionCount() {
		return this.backupSessionCount;
	}

	public void setBackupSessionCount(int numCurrentBackupSessions) {
		this.backupSessionCount = numCurrentBackupSessions;
	}

	public int getAssignedComputerCount() {
		return this.assignedComputerCount;
	}

	public void setAssignedComputerCount(int numAssignedComputers) {
		this.assignedComputerCount = numAssignedComputers;
	}

	public int getBackupComputerCount() {
		return this.backupComputerCount;
	}

	public void setBackupComputerCount(int numBackupComputers) {
		this.backupComputerCount = numBackupComputers;
	}

	public int getAssignedUserCount() {
		return this.assignedUserCount;
	}

	public void setAssignedUserCount(int numAssignedUsers) {
		this.assignedUserCount = numAssignedUsers;
	}

	public int getLicensedUserCount() {
		return this.licensedUserCount;
	}

	public void setLicensedUserCount(int numLicensedUsers) {
		this.licensedUserCount = numLicensedUsers;
	}

	public int getAssignedOrgCount() {
		return this.assignedOrgCount;
	}

	public void setAssignedOrgCount(int numAssignedOrgs) {
		this.assignedOrgCount = numAssignedOrgs;
	}

	public long getUsedBytes() {
		return this.getTotalBytes() - this.getFreeBytes();
	}

	public void setFreeBytes(long freeBytes) {
		this.freeBytes = freeBytes;
	}

	public FreeBytesAlert getFreeBytesAlert() {
		return this.freeBytesAlert;
	}

	public void setFreeBytesCritical() {
		this.freeBytesAlert = FreeBytesAlert.CRITICAL;
	}

	public void setFreeBytesWarning() {
		this.freeBytesAlert = FreeBytesAlert.WARNING;
	}

	/**
	 * @return percentage, 1-100, to one decimal point
	 */
	public double getUsedPercentage() {
		double used = this.getDisplayPercentage(this.getUsedBytes(), this.getTotalBytes());
		return used;
	}

	public long getFreeBytes() {
		return this.freeBytes;
	}

	/**
	 * @return percentage, 1-100, to one decimal point
	 */
	public double getFreePercentage() {
		double free = this.getDisplayPercentage(this.getFreeBytes(), this.getTotalBytes());
		return free;
	}

	public long getColdBytes() {
		return this.coldBytes;
	}

	public void setColdBytes(long coldBytes) {
		this.coldBytes = coldBytes;
	}

	/**
	 * @return percentage, 1-100, to one decimal point
	 */
	public double getColdPercentageOfUsed() {
		double cold = this.getDisplayPercentage(this.getColdBytes(), this.getUsedBytes());
		return cold;
	}

	/**
	 * @return percentage, 1-100, to one decimal point
	 */
	public double getColdPercentageOfTotal() {
		double cold = this.getDisplayPercentage(this.getColdBytes(), this.getTotalBytes());
		return cold;
	}

	public long getTotalBytes() {
		return this.totalBytes;
	}

	public void setTotalBytes(long totalBytes) {
		this.totalBytes = totalBytes;
	}

	public boolean isOnline() {
		return this.online;
	}

	public void setOnline(boolean online) {
		this.online = online;
	}

	public boolean isServerOnline() {
		return this.serverOnline;
	}

	public void setServerOnline(boolean bool) {
		this.serverOnline = bool;
	}

	public boolean isAcceptingInboundBackup() {
		final boolean accepting = this.mount.isEnabled();
		return accepting;
	}

	public boolean isBalancingData() {
		return this.mount.getBalanceData();
	}

	public boolean isAcceptingNewComputers() {
		return this.mount.getBalanceNewUsers();
	}

	public Long getWriteSpeed() {
		return this.mount.getBps();
	}

	public long getArchiveBytes() {
		return this.archiveBytes;
	}

	public void setArchiveBytes(long archiveBytes) {
		this.archiveBytes = archiveBytes;
	}

	public long getSelectedBytes() {
		return this.selectedBytes;
	}

	public void setSelectedBytes(long selectedBytes) {
		this.selectedBytes = selectedBytes;
	}

	public long getTodoBytes() {
		return this.todoBytes;
	}

	public void setTodoBytes(long todoBytes) {
		this.todoBytes = todoBytes;
	}

	protected MountPoint getMountPoint() {
		return this.mount;
	}

	@Override
	public String toString() {
		return "MountPointDto [mountPointId=" + this.getMountPointId() + ", name=" + this.getName() + ", note="
				+ this.getNote() + ", path=" + this.getPath() + ", directory=" + this.getDirectory() + ", absolutePath="
				+ this.getAbsolutePath() + ", serverId=" + this.getServerId() + ", serverName=" + this.getServerName()
				+ ", destinationId=" + this.getDestinationId() + ", destinationName=" + this.getDestinationName()
				+ ", currentBackupSessionCount=" + this.getBackupSessionCount() + ", numAssignedComputers="
				+ this.getAssignedComputerCount() + ", usedBytes=" + this.getUsedBytes() + ", usedPercentage="
				+ this.getUsedPercentage() + ", freeBytes=" + this.getFreeBytes() + ", freePercentage="
				+ this.getFreePercentage() + ", coldBytes=" + this.getColdBytes() + ", coldPercentageOfUsed="
				+ this.getColdPercentageOfUsed() + ", coldPercentageOfTotal=" + this.getColdPercentageOfTotal()
				+ ", totalBytes=" + this.getTotalBytes() + ", isAcceptingInboundBackup=" + this.isAcceptingInboundBackup()
				+ ", isBalancingData=" + this.isBalancingData() + ", isAcceptingNewComputers=" + this.isAcceptingNewComputers()
				+ ", writeSpeed=" + this.getWriteSpeed() + "]";
	}

	// /////////////////////////
	// HELPER METHODS
	// /////////////////////////
	/**
	 * Convert the given numbers into a fraction represented as a 0-100 percentage value, accurate to a single decimal
	 * point. All percentages in this DTO use the same rules.
	 */
	private double getDisplayPercentage(long numerator, long denominator) {
		if (denominator == 0) { // dividing by 0
			return 0;
		}

		double perc = ((double) numerator) / denominator;
		perc = RoundSafe.decimalToPercentage(perc, 1);
		perc = LangUtils.boundValue(perc, 0, 100);
		return perc;
	}
}
