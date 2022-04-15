package com.code42.user;

import com.code42.core.CommandException;

/**
 * Thrown when a unique user (either the User Hibernate entity or a UserSso) was expected
 * but there was insufficient information available to uniquely identify one entity. The
 * "user version" of NoUniqueDirectoryEntryException.
 * 
 * @author bmcguire
 */
public class NoUniqueUserException extends CommandException {

	private static final long serialVersionUID = -3335738367640072878L;

	public NoUniqueUserException(String str) {
		super(str);
	}

	public NoUniqueUserException(String str, Throwable t) {
		super(str, t);
	}
}
