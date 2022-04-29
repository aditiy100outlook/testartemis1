package com.code42.computer;

import java.util.Date;

import com.code42.archiverecord.ArchiveRecord;

public class ArchiveRecordDto {

	private ArchiveRecord archiveRecord;

	public ArchiveRecordDto(ArchiveRecord ar) {
		this.archiveRecord = ar;
	}

	public ArchiveRecord getArchiveRecord() {
		return this.archiveRecord;
	}

	public void setArchiveRecord(ArchiveRecord archiveRecord) {
		this.archiveRecord = archiveRecord;
	}

	public int getSendRateAverage() {
		return this.archiveRecord.getSendRateAverage();
	}

	public int getCompletionRateAverage() {
		return this.archiveRecord.getCompletionRateAverage();
	}

	public Integer getOrgId() {
		return this.archiveRecord.getOrgId();
	}

	public double getPercentComplete() {
		return this.archiveRecord.getPercentComplete();
	}

	public Integer getUserId() {
		return this.archiveRecord.getUserId();
	}

	public Long getSourceComputerId() {
		return this.archiveRecord.getSourceComputerId();
	}

	public long getTargetComputerId() {
		return this.archiveRecord.getTargetComputerId();
	}

	public long getSelectedFiles() {
		return this.archiveRecord.getSelectedFiles();
	}

	public long getSelectedBytes() {
		return this.archiveRecord.getSelectedBytes();
	}

	public double getCompressionRatio() {
		return this.archiveRecord.getCompressionRatio();
	}

	public long getTodoFiles() {
		return this.archiveRecord.getTodoFiles();
	}

	public long getTodoBytes() {
		return this.archiveRecord.getTodoBytes();
	}

	public long getArchiveBytes() {
		return this.archiveRecord.getArchiveBytes();
	}

	public long getBillableBytes() {
		return this.archiveRecord.getBillableBytes();
	}

	public Date getLastActivity() {
		return this.archiveRecord.getLastActivity();
	}

	public Date getLastCompletedBackup() {
		return this.archiveRecord.getLastCompletedBackup();
	}

	public Date getLastConnected() {
		return this.archiveRecord.getLastConnected();
	}

	public Date getCreationDate() {
		return this.archiveRecord.getCreationDate();
	}

	public boolean isUsing() {
		return this.archiveRecord.isUsing();
	}

	public long getCompletedFiles() {
		return this.archiveRecord.getCompletedFiles();
	}

	public long getCompletedBytes() {
		return this.archiveRecord.getCompletedBytes();
	}
}
