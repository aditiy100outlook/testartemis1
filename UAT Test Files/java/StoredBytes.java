package com.code42.stats;

import java.io.Serializable;

/**
 * Simple struct to represent two different pictures of actual bytes stored. Could've been modeled as a Pair<Long,Long>
 * but the use of named fields/accessors here removes any potential confusion.
 * 
 * @author marshall
 */
public class StoredBytes implements Serializable {

	private static final long serialVersionUID = -3527860944843111575L;

	private final long archiveBytes;
	private final long billableBytes;

	public StoredBytes(long archiveBytes, long billableBytes) {
		this.archiveBytes = archiveBytes;
		this.billableBytes = billableBytes;
	}

	public long getArchiveBytes() {
		return this.archiveBytes;
	}

	public long getBillableBytes() {
		return this.billableBytes;
	}

	@Override
	public String toString() {
		return "StoredBytes [archiveBytes=" + this.archiveBytes + ", billableBytes=" + this.billableBytes + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (this.archiveBytes ^ (this.archiveBytes >>> 32));
		result = prime * result + (int) (this.billableBytes ^ (this.billableBytes >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (this.getClass() != obj.getClass()) {
			return false;
		}
		StoredBytes other = (StoredBytes) obj;
		if (this.archiveBytes != other.archiveBytes) {
			return false;
		}
		if (this.billableBytes != other.billableBytes) {
			return false;
		}
		return true;
	}
}
