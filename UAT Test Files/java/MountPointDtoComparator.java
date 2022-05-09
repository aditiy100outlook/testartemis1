package com.code42.server.mount;

import java.util.Comparator;

public class MountPointDtoComparator implements Comparator<MountPointDto> {

	private MountPointDtoFindByCriteriaBuilder.SortKey sortKey;
	private MountPointDtoFindByCriteriaBuilder.SortDir sortDir;

	MountPointDtoComparator(MountPointDtoFindByCriteriaBuilder.SortKey sortKey,
			MountPointDtoFindByCriteriaBuilder.SortDir sortDir) {
		this.sortKey = sortKey;
		this.sortDir = sortDir;
	}

	public int compare(MountPointDto dto1, MountPointDto dto2) {
		int rv = 0;
		if (this.sortKey == MountPointDtoFindByCriteriaBuilder.SortKey.backupSessionCount) {
			rv = new Integer(dto1.getBackupSessionCount()).compareTo(dto2.getBackupSessionCount());

		} else if (this.sortKey == MountPointDtoFindByCriteriaBuilder.SortKey.deviceCount) {
			rv = new Integer(dto1.getAssignedComputerCount()).compareTo(dto2.getAssignedComputerCount());

		} else if (this.sortKey == MountPointDtoFindByCriteriaBuilder.SortKey.storedSize) {
			rv = new Long(dto1.getArchiveBytes()).compareTo(dto2.getArchiveBytes());

		} else if (this.sortKey == MountPointDtoFindByCriteriaBuilder.SortKey.freeSize) {
			rv = new Long(dto1.getFreeBytes()).compareTo(dto2.getFreeBytes());

		} else if (this.sortKey == MountPointDtoFindByCriteriaBuilder.SortKey.coldSize) {
			rv = new Long(dto1.getColdBytes()).compareTo(dto2.getColdBytes());

		} else if (this.sortKey == MountPointDtoFindByCriteriaBuilder.SortKey.balancingData) {
			rv = new Boolean(dto1.isBalancingData()).compareTo(dto2.isBalancingData());

		} else if (this.sortKey == MountPointDtoFindByCriteriaBuilder.SortKey.acceptingNewComputers) {
			rv = new Boolean(dto1.isAcceptingNewComputers()).compareTo(dto2.isAcceptingNewComputers());

		} else if (this.sortKey == MountPointDtoFindByCriteriaBuilder.SortKey.acceptingInboundBackup) {
			rv = new Boolean(dto1.isAcceptingInboundBackup()).compareTo(dto2.isAcceptingInboundBackup());

		} else if (this.sortKey == MountPointDtoFindByCriteriaBuilder.SortKey.online) {
			rv = new Boolean(dto1.isOnline()).compareTo(dto2.isOnline());
		}

		if (this.sortDir == MountPointDtoFindByCriteriaBuilder.SortDir.DESC) {
			rv *= -1; // Reverse the outcome
		}

		return rv;
	}
}
