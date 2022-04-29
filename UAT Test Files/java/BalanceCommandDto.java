package com.code42.balance;

import java.util.Date;

public class BalanceCommandDto {

	private int dataBalanceCommandId;
	private Type type;
	private Status status = Status.ACTIVE;
	private Date creationDate;
	private Date endDate; // optional, refers to completed OR cancelled

	private int srcMountId;
	private String srcMountName;
	private int srcNodeId;
	private String srcNodeName = "";

	// all target info is optional
	private Integer tgtMountId;
	private String tgtMountName;
	private Integer tgtNodeId;
	private String tgtNodeName = "";

	private Long guid; // optional

	public BalanceCommandDto() {
		super();
	}

	public enum Type {
		ARCHIVE_MOVE, EMPTY_MOUNT_TO_DESTINATION, EMPTY_MOUNT_TO_MOUNT
	}

	public enum Status {
		ACTIVE, CANCELLED, COMPLETED
	}

	public int getDataBalanceCommandId() {
		return this.dataBalanceCommandId;
	}

	public void setDataBalanceCommandId(int dataBalanceCommandId) {
		this.dataBalanceCommandId = dataBalanceCommandId;
	}

	public Type getType() {
		return this.type;
	}

	public void setType(Type type) {
		this.type = type;
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

	public Integer getTgtMountId() {
		return this.tgtMountId;
	}

	public void setTgtMountId(Integer tgtMountId) {
		this.tgtMountId = tgtMountId;
	}

	public String getTgtMountName() {
		return this.tgtMountName;
	}

	public void setTgtMountName(String tgtMountName) {
		this.tgtMountName = tgtMountName;
	}

	public Integer getTgtNodeId() {
		return this.tgtNodeId;
	}

	public void setTgtNodeId(Integer tgtNodeId) {
		this.tgtNodeId = tgtNodeId;
	}

	public String getTgtNodeName() {
		return this.tgtNodeName;
	}

	public void setTgtNodeName(String tgtNodeName) {
		this.tgtNodeName = tgtNodeName;
	}

	public Long getGuid() {
		return this.guid;
	}

	public void setGuid(Long guid) {
		this.guid = guid;
	}

	public Date getCreationDate() {
		return this.creationDate;
	}

	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
	}

	public Date getEndDate() {
		return this.endDate;
	}

	public void setEndDate(Date endDate) {
		this.endDate = endDate;
	}

	@Override
	public String toString() {
		return "BalanceCommandDto [dataBalanceCommandId=" + this.dataBalanceCommandId + ", type=" + this.type + ", status="
				+ this.status + ", srcMountId=" + this.srcMountId + ", srcMountName=" + this.srcMountName + ", srcNodeId="
				+ this.srcNodeId + ", srcNodeName=" + this.srcNodeName + ", tgtMountId=" + this.tgtMountId + ", tgtMountName="
				+ this.tgtMountName + ", tgtNodeId=" + this.tgtNodeId + ", tgtNodeName=" + this.tgtNodeName + ", guid="
				+ this.guid + ", creationDate=" + this.creationDate + ", endDate=" + this.endDate + "]";
	}
}
