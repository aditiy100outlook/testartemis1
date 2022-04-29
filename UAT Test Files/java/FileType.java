package com.code42.transport;

import com.code42.os.file.FileStat;

/**
 * Maps to supported archive types in {@link FileStat.Type}, without the dependency baggage of FileStat.
 * 
 * @author <a href="mailto:brada@code42.com">Brad Armstrong</a>
 * 
 * 
 */
public enum FileType {

	UNKNOWN, FILE, DIR, WIN_NDS, MAC_RSRC, SYMLINK, FIFO, BLOCK_DEVICE, CHAR_DEVICE, SOCKET;

	/** Create from FileStat.Type byte value */
	public static FileType fromByte(byte fileType) {
		switch (fileType) {
		case FileStat.Type.UNKNOWN:
			return UNKNOWN;
		case FileStat.Type.FILE:
			return FILE;
		case FileStat.Type.DIR:
			return DIR;
		case FileStat.Type.WIN_NDS:
			return WIN_NDS;
		case FileStat.Type.MAC_RSRC:
			return MAC_RSRC;
		case FileStat.Type.SYMLINK:
			return SYMLINK;
		case FileStat.Type.FIFO:
			return FIFO;
		case FileStat.Type.BLOCK_DEVICE:
			return BLOCK_DEVICE;
		case FileStat.Type.CHAR_DEVICE:
			return CHAR_DEVICE;
		case FileStat.Type.SOCKET:
			return SOCKET;
		default:
			throw new IllegalArgumentException("Unknown type: " + fileType);
		}
	}
}
