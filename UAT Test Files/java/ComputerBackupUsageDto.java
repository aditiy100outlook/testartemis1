package com.code42.computer;

import java.util.Collection;
import java.util.Date;

import com.backup42.common.ComputerType;
import com.backup42.computer.BaseArchiveRecord;

public class ComputerBackupUsageDto {

	protected long computerId;
	protected long computerGuid;
	protected long targetComputerId;
	protected long targetComputerGuid;
	protected String targetComputerName;
	protected String targetComputerOsName;
	protected ComputerType targetComputerType;
	protected boolean provider;
	protected long archiveBytes;
	protected long selectedBytes;
	protected long selectedFiles;
	protected long todoBytes;
	protected long todoFiles;
	protected long sendRateAverage;
	protected Integer mountPointId;
	protected String mountPointName;
	protected Integer serverId;
	protected String serverName;
	protected String serverHostName;
	protected Date lastActivity;
	protected Date lastCompletedBackup;
	protected Date lastConnected;
	protected boolean using;
	protected int alertState;
	protected Long friendComputerUsageId;
	protected Long billableBytes;
	protected Double percentComplete;
	protected ComputerActivityDto activity; // PROTOBUF: added field activity

	protected Collection<ArchiveRecordDto> history;

	public long getComputerId() {
		return this.computerId;
	}

	public long getComputerGuid() {
		return this.computerGuid;
	}

	public long getTargetComputerGuid() {
		return this.targetComputerGuid;
	}

	public long getTargetComputerId() {
		return this.targetComputerId;
	}

	public long getArchiveBytes() {
		return this.archiveBytes;
	}

	public long getBillableBytes() {
		if (this.billableBytes != null) {
			return this.billableBytes.longValue();
		} else {
			return BaseArchiveRecord.getBillableBytes(this.archiveBytes, this.selectedBytes, this.getPercentComplete());
		}
	}

	public long getSelectedBytes() {
		return this.selectedBytes;
	}

	public long getSelectedFiles() {
		return this.selectedFiles;
	}

	public long getTodoBytes() {
		return this.todoBytes;
	}

	public long getTodoFiles() {
		return this.todoFiles;
	}

	public Integer getMountPointId() {
		return this.mountPointId;
	}

	public String getMountPointName() {
		return this.mountPointName;
	}

	public Integer getServerId() {
		return this.serverId;
	}

	public String getServerName() {
		return this.serverName;
	}

	public String getServerHostName() {
		return this.serverHostName;
	}

	public Collection<ArchiveRecordDto> getHistory() {
		return this.history;
	}

	public double getPercentComplete() {
		if (this.percentComplete != null) {
			return this.percentComplete.doubleValue();
		} else {
			return BaseArchiveRecord.getPercentComplete(this.getSelectedBytes(), this.getTodoBytes(), this.getArchiveBytes());
		}
	}

	public Date getLastActivity() {
		return this.lastActivity;
	}

	public Date getLastCompletedBackup() {
		return this.lastCompletedBackup;
	}

	public Date getLastConnected() {
		return this.lastConnected;
	}

	public long getSendRateAverage() {
		return this.sendRateAverage;
	}

	public boolean isUsing() {
		return this.using;
	}

	public int getAlertState() {
		return this.alertState;
	}

	public String getTargetComputerName() {
		return this.targetComputerName;
	}

	public String getTargetComputerOsName() {
		return this.targetComputerOsName;
	}

	public ComputerType getTargetComputerType() {
		return this.targetComputerType;
	}

	public boolean isProvider() {
		return this.provider;
	}

	public Long getFriendComputerUsageId() {
		return this.friendComputerUsageId;
	}

	public ComputerActivityDto getActivity() {
		return this.activity;
	}

	public void setComputerId(long computerId) {
		this.computerId = computerId;
	}

	public void setComputerGuid(long computerGuid) {
		this.computerGuid = computerGuid;
	}

	public void setTargetComputerId(long targetComputerId) {
		this.targetComputerId = targetComputerId;
	}

	public void setTargetComputerGuid(long targetComputerGuid) {
		this.targetComputerGuid = targetComputerGuid;
	}

	public void setTargetComputerName(String targetComputerName) {
		this.targetComputerName = targetComputerName;
	}

	public void setTargetComputerOsName(String targetComputerOsName) {
		this.targetComputerOsName = targetComputerOsName;
	}

	public void setTargetComputerType(ComputerType targetComputerType) {
		this.targetComputerType = targetComputerType;
	}

	public void setIsProvider(boolean provider) {
		this.provider = provider;
	}

	public void setArchiveBytes(long archiveBytes) {
		this.archiveBytes = archiveBytes;
	}

	public void setSelectedBytes(long selectedBytes) {
		this.selectedBytes = selectedBytes;
	}

	public void setSelectedFiles(long selectedFiles) {
		this.selectedFiles = selectedFiles;
	}

	public void setTodoBytes(long todoBytes) {
		this.todoBytes = todoBytes;
	}

	public void setTodoFiles(long todoFiles) {
		this.todoFiles = todoFiles;
	}

	public void setMountPointId(Integer mountPointId) {
		this.mountPointId = mountPointId;
	}

	public void setMountPointName(String mountPointName) {
		this.mountPointName = mountPointName;
	}

	public void setServerId(Integer serverId) {
		this.serverId = serverId;
	}

	public void setServerName(String serverName) {
		this.serverName = serverName;
	}

	public void setServerHostName(String serverHostName) {
		this.serverHostName = serverHostName;
	}

	public void setLastActivity(Date lastActivity) {
		this.lastActivity = lastActivity;
	}

	public void setLastCompletedBackup(Date lastCompletedBackup) {
		this.lastCompletedBackup = lastCompletedBackup;
	}

	public void setLastConnected(Date lastConnected) {
		this.lastConnected = lastConnected;
	}

	public void setUsing(boolean using) {
		this.using = using;
	}

	public void setAlertState(int alertState) {
		this.alertState = alertState;
	}

	public void setFriendComputerUsageId(Long friendComputerUsageId) {
		this.friendComputerUsageId = friendComputerUsageId;
	}

	public void setHistory(Collection<ArchiveRecordDto> history) {
		this.history = history;
	}

}