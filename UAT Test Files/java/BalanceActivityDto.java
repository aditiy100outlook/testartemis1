package com.code42.balance;

import java.util.Date;

/**
 * Describe balance activity- either current or history- as it pertains to archives.
 * 
 * This dto describes operations on archives themselves, not the larger override commands.
 */
public class BalanceActivityDto {

	private long guid;
	private long archiveBytes;
	private Status status = Status.RUNNING;

	private int srcMountId;
	private String srcMountName;
	private int srcNodeId;
	private String srcNodeName = "";

	private int tgtMountId;
	private String tgtMountName;
	private int tgtNodeId;
	private String tgtNodeName = "";

	private Date startDate;
	private Date endDate;

	public enum Status {
		RUNNING, FAILED, COMPLETED
	}

	public BalanceActivityDto() {
		super();
	}

	public long getGuid() {
		return this.guid;
	}

	public void setGuid(long guid) {
		this.guid = guid;
	}

	public long getArchiveBytes() {
		return this.archiveBytes;
	}

	public void setArchiveBytes(long archiveBytes) {
		this.archiveBytes = archiveBytes;
	}

	public Status getStatus() {
		return this.status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public int getSrcMountId() {
		return this.srcMountId;
	}

	public void setSrcMountId(int srcMountId) {
		this.srcMountId = srcMountId;
	}

	public String getSrcMountName() {
		return this.srcMountName;
	}

	public void setSrcMountName(String srcMountName) {
		this.srcMountName = srcMountName;
	}

	public int getSrcNodeId() {
		return this.srcNodeId;
	}

	public void setSrcNodeId(int srcNodeId) {
		this.srcNodeId = srcNodeId;
	}

	public String getSrcNodeName() {
		return this.srcNodeName;
	}

	public void setSrcNodeName(String srcNodeName) {
		this.srcNodeName = srcNodeName;
	}

	public int getTgtMountId() {
		return this.tgtMountId;
	}

	public void setTgtMountId(int tgtMountId) {
		this.tgtMountId = tgtMountId;
	}

	public String getTgtMountName() {
		return this.tgtMountName;
	}

	public void setTgtMountName(String tgtMountName) {
		this.tgtMountName = tgtMountName;
	}

	public int getTgtNodeId() {
		return this.tgtNodeId;
	}

	public void setTgtNodeId(int tgtNodeId) {
		this.tgtNodeId = tgtNodeId;
	}

	public String getTgtNodeName() {
		return this.tgtNodeName;
	}

	public void setTgtNodeName(String tgtNodeName) {
		this.tgtNodeName = tgtNodeName;
	}

	public Date getStartDate() {
		return this.startDate;
	}

	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}

	public Date getEndDate() {
		return this.endDate;
	}

	public void setEndDate(Date endDate) {
		this.endDate = endDate;
	}

	@Override
	public String toString() {
		return "BalanceActivityDto [guid=" + this.guid + ", archiveBytes=" + this.archiveBytes + ", status=" + this.status
				+ ", srcMountId=" + this.srcMountId + ", srcMountName=" + this.srcMountName + ", srcNodeId=" + this.srcNodeId
				+ ", srcNodeName=" + this.srcNodeName + ", tgtMountId=" + this.tgtMountId + ", tgtMountName="
				+ this.tgtMountName + ", tgtNodeId=" + this.tgtNodeId + ", tgtNodeName=" + this.tgtNodeName + ", startDate="
				+ this.startDate + ", endDate=" + this.endDate + "]";
	}
}
