package com.code42.archive;

import java.util.Date;

/**
 * Describe the basics of an Archive.
 */
public class ArchiveDto {

	// ids
	private long computerId; // source
	private long guid; // source
	private long targetGuid; // target
	private long mountPointId;
	private long destinationId;

	// backup info
	private long selectedBytes;
	private long selectedFiles;
	private long todoBytes;
	private long archiveBytes;
	private Date lastCompletedBackup;
	private boolean isUsing; // cold storage

	// maintenance info
	private Date lastMaintained;
	private Long maintenanceDuration; // nullable
	private Long compactBytesRemoved; // nullable

	public long getComputerId() {
		return this.computerId;
	}

	public void setComputerId(long computerId) {
		this.computerId = computerId;
	}

	public long getGuid() {
		return this.guid;
	}

	public void setGuid(long guid) {
		this.guid = guid;
	}

	public long getTargetGuid() {
		return this.targetGuid;
	}

	public void setTargetGuid(long targetGuid) {
		this.targetGuid = targetGuid;
	}

	public long getMountPointId() {
		return this.mountPointId;
	}

	public void setMountPointId(long mountPointId) {
		this.mountPointId = mountPointId;
	}

	public long getDestinationId() {
		return this.destinationId;
	}

	public void setDestinationId(long destinationId) {
		this.destinationId = destinationId;
	}

	public long getSelectedBytes() {
		return this.selectedBytes;
	}

	public void setSelectedBytes(long selectedBytes) {
		this.selectedBytes = selectedBytes;
	}

	public long getSelectedFiles() {
		return this.selectedFiles;
	}

	public void setSelectedFiles(long selectedFiles) {
		this.selectedFiles = selectedFiles;
	}

	public long getTodoBytes() {
		return this.todoBytes;
	}

	public void setTodoBytes(long todoBytes) {
		this.todoBytes = todoBytes;
	}

	public long getArchiveBytes() {
		return this.archiveBytes;
	}

	public void setArchiveBytes(long archiveBytes) {
		this.archiveBytes = archiveBytes;
	}

	public Date getLastCompletedBackup() {
		return this.lastCompletedBackup;
	}

	public void setLastCompletedBackup(Date lastCompletedBackup) {
		this.lastCompletedBackup = lastCompletedBackup;
	}

	public boolean getIsUsing() {
		return this.isUsing;
	}

	public void setIsUsing(boolean isUsing) {
		this.isUsing = isUsing;
	}

	public Date getLastMaintained() {
		return this.lastMaintained;
	}

	public void setLastMaintained(Date lastMaintained) {
		this.lastMaintained = lastMaintained;
	}

	public Long getMaintenanceDuration() {
		return this.maintenanceDuration;
	}

	public void setMaintenanceDuration(Long maintenanceDuration) {
		this.maintenanceDuration = maintenanceDuration;
	}

	public Long getCompactBytesRemoved() {
		return this.compactBytesRemoved;
	}

	public void setCompactBytesRemoved(Long compactBytesRemoved) {
		this.compactBytesRemoved = compactBytesRemoved;
	}

	@Override
	public String toString() {
		return "ArchiveDto [computerId=" + this.computerId //
				+ ", guid=" + this.guid //
				+ ", mountPointId=" + this.mountPointId //
				+ ", destinationId=" + this.destinationId //
				+ ", selectedBytes=" + this.selectedBytes //
				+ ", todoBytes=" + this.todoBytes //
				+ ", archiveBytes=" + this.archiveBytes //
				+ ", lastCompletedBackup=" + this.lastCompletedBackup //
				+ ", isUsing=" + this.isUsing //
				+ ", lastMaintained=" + this.lastMaintained //
				+ ", maintenanceDuration=" + this.maintenanceDuration //
				+ ", compactBytesRemoved=" + this.compactBytesRemoved //
				+ "]";
	}
}
