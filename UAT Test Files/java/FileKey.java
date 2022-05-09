package com.code42.transport;

import static com.google.common.base.Preconditions.checkNotNull;

import com.code42.io.path.FileId;

/**
 * Immutable struct representing a collection of transport-related data that's commonly used together.
 */
public class FileKey {

	private final long transactionId;
	private final FileId fileId;
	private final long archiveId;
	private final long peerId;

	public FileKey(long transactionId, FileId fileId, long archiveId, long peerId) {
		this.transactionId = transactionId;
		this.fileId = fileId;
		this.archiveId = archiveId;
		this.peerId = peerId;
	}

	private FileKey(Builder builder) {
		this.transactionId = builder.transactionId;
		this.fileId = builder.fileId;
		this.archiveId = builder.archiveId;
		this.peerId = builder.peerId;
	}

	public long getTransactionId() {
		return this.transactionId;
	}

	public FileId getFileId() {
		return this.fileId;
	}

	public long getArchiveId() {
		return this.archiveId;
	}

	public long getPeerId() {
		return this.peerId;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (this.transactionId ^ (this.transactionId >>> 32));
		result = prime * result + (int) (this.archiveId ^ (this.archiveId >>> 32));
		result = prime * result + ((this.fileId == null) ? 0 : this.fileId.hashCode());
		result = prime * result + (int) (this.peerId ^ (this.peerId >>> 32));
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
		FileKey other = (FileKey) obj;

		if (this.transactionId != other.transactionId) {
			return false;
		}
		if (this.archiveId != other.archiveId) {
			return false;
		}
		if (this.fileId == null) {
			if (other.fileId != null) {
				return false;
			}
		} else if (!this.fileId.equals(other.fileId)) {
			return false;
		}
		if (this.peerId != other.peerId) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "FileKey [transactionId=" + this.transactionId + ", fileId=" + this.fileId + ", archiveId=" + this.archiveId
				+ ", peerId=" + this.peerId + "]";
	}

	/** A reusable FileKey builder */
	public static class Builder {

		private Long transactionId;
		private FileId fileId;
		private Long archiveId;
		private Long peerId;

		public Builder transactionId(long arg) {
			this.transactionId = arg;
			return this;
		}

		public Builder fileId(FileId arg) {
			this.fileId = arg;
			return this;
		}

		public Builder archiveId(long arg) {
			this.archiveId = arg;
			return this;
		}

		public Builder peerId(long arg) {
			this.peerId = arg;
			return this;
		}

		private void validate() {
			checkNotNull(this.transactionId);
			checkNotNull(this.fileId);
			checkNotNull(this.archiveId);
			checkNotNull(this.peerId);
		}

		public FileKey build() {
			this.validate();
			return new FileKey(this);
		}
	}
}
