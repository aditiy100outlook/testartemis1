/*
 * Created on Feb 17, 2011 by Tony Lindquist <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.computer;

import com.code42.core.CommandException;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;

/**
 * 
 * Find the ClusterDto for this server
 * 
 * @author tlindqui
 */
public class ClusterDtoFindCmd extends AbstractCmd<ClusterDto> {

	@Override
	public ClusterDto exec(CoreSession session) throws CommandException {
		if (session == null) {
			throw new UnauthorizedException("Invalid user account");
		}

		return new ClusterDto(this.env);
	}

}
