package com.code42.archive;

import java.util.List;

import com.code42.crypto.MD5Value;

public class FileInfoDto {

	public enum FileType {
		FILE, FOLDER;
	}

	private final String filename;
	private final String path;
	private final FileType type;
	private final long lastModified;
	private final MD5Value checksum;
	private final boolean deleted;
	private final List<FileInfoDto> children;

	FileInfoDto(String filename, String path, FileType type, long lastModified, MD5Value checksum, boolean deleted,
			List<FileInfoDto> children) {
		this.filename = filename;
		this.path = path;
		this.type = type;
		this.lastModified = lastModified;
		this.checksum = checksum;
		this.deleted = deleted;
		this.children = children;
	}

	public String getFilename() {
		return this.filename;
	}

	public String getPath() {
		return this.path;
	}

	public FileType getType() {
		return this.type;
	}

	public long getLastModified() {
		return this.lastModified;
	}

	public boolean isDeleted() {
		return this.deleted;
	}

	public List<FileInfoDto> getChildren() {
		return this.children;
	}

	public MD5Value getChecksum() {
		return this.checksum;
	}
}
