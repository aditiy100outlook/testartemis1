package com.code42.server;

import org.hibernate.Session;

import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;

public class ServerSettingsFindByIdQuery extends FindQuery<ServerSettings> {

	private int serverId;

	public ServerSettingsFindByIdQuery(int serverId) {
		this.serverId = serverId;
	}

	@Override
	public ServerSettings query(Session session) throws DBServiceException {
		return (ServerSettings) session.get(ServerSettings.class, this.serverId);
	}

}
