package com.code42.directory;

import com.code42.core.CommandException;

/**
 * Exception indicating that we could not identify a unique DirectoryEntry based on the parameters provided. Additional
 * constraints on these parameters may produce better results.
 * 
 * @author bmcguire
 */
public class NoUniqueDirectoryEntryException extends CommandException {

	private static final long serialVersionUID = 1819563527664969050L;

	public NoUniqueDirectoryEntryException(String str) {
		super(str);
	}

	public NoUniqueDirectoryEntryException(String str, Throwable t) {
		super(str, t);
	}

}
