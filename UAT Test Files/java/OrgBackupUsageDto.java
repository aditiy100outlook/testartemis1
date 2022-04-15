package com.code42.org;

import java.io.Serializable;

class OrgBackupUsageDto implements Serializable {

	private static final long serialVersionUID = -1402218519322357573L;

	protected int orgId;
	protected long targetComputerGuid;
	protected long archiveBytes;
	protected long billableBytes;
	protected long selectedBytes;
	protected long selectedFiles;
	protected long todoBytes;
	protected long todoFiles;
	protected long archiveBytesDeltaMonth;
	protected int connectedCount;
	protected int backupSessionCount;

	public int getOrgId() {
		return this.orgId;
	}

	public long getTargetComputerGuid() {
		return this.targetComputerGuid;
	}

	public long getArchiveBytes() {
		return this.archiveBytes;
	}

	public long getBillableBytes() {
		return this.billableBytes;
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

	public long getArchiveBytesDeltaMonth() {
		return this.archiveBytesDeltaMonth;
	}

	public int getConnectedCount() {
		return this.connectedCount;
	}

	public int getBackupSessionCount() {
		return this.backupSessionCount;
	}
}