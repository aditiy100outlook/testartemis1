/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.server;

import com.code42.core.CommandException;

public class DatabaseNotEmptyException extends CommandException {

	private static final long serialVersionUID = 2777918744555766304L;

	public DatabaseNotEmptyException(String str) {
		super(str);
	}

}
