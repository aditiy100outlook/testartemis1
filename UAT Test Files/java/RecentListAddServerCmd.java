package com.code42.recent;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.server.node.Node;

public class RecentListAddServerCmd extends AbstractCmd<Void> {

	private final int serverId;
	private final long guid;
	private final String name;

	public RecentListAddServerCmd(int serverId, long guid, String name) {
		this.serverId = serverId;
		this.guid = guid;
		this.name = name;
	}

	public RecentListAddServerCmd(Node server) {
		this.serverId = server.getNodeId();
		this.guid = server.getNodeGuid();
		this.name = server.getNodeName();
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		RecentServer rs = new RecentServer(this.serverId, this.guid, this.name);

		this.runtime.run(new RecentListAddItemCmd(rs), session);

		return null;
	}
}
