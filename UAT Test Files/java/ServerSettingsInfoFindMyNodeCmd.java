package com.code42.server;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;

/**
 * Return the ServerSettingsInfo object for the current server.
 * 
 * @author mscorcio
 */
public class ServerSettingsInfoFindMyNodeCmd extends AbstractCmd<ServerSettingsInfo> {

	@Override
	public ServerSettingsInfo exec(CoreSession session) throws CommandException {
		return this.run(new ServerSettingsInfoFindByServerCmd(this.env.getMyNodeId()), session);
	}
}
