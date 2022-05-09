package com.code42.computer;

import java.net.URL;

import com.code42.backup.central.ICentralService;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.peer.Peer;
import com.code42.server.ServerFindWebsiteHostByServerIdCmd;
import com.code42.server.node.Node;
import com.code42.server.node.NodeFindByGuidQuery;
import com.google.inject.Inject;

public class ComputerFindConnectedServerUrlCmd extends DBCmd<String> {

	@Inject
	private ICentralService centralService;

	private final long srcGuid;

	public ComputerFindConnectedServerUrlCmd(long srcGuid) {
		this.srcGuid = srcGuid;
	}

	@Override
	public String exec(CoreSession session) throws CommandException {
		ComputerSso c = this.runtime.run(new ComputerSsoFindByGuidCmd(this.srcGuid), session);

		if (c == null) {
			throw new CommandException("Unable to find computer with guid: " + this.srcGuid);
		}

		this.runtime.run(new IsComputerManageableCmd(c.getComputerId(), C42PermissionApp.Computer.READ), session);

		Peer peer = this.centralService.getPeer().getPeer();
		long superPeerGuid = peer.getSuperPeerForRemotePeer(this.srcGuid);
		if (superPeerGuid == -1) {
			return null;
		}
		Node server = this.db.find(new NodeFindByGuidQuery(superPeerGuid));
		Integer serverId = server.getNodeId();
		if (serverId == null) {
			return null;
		}

		URL url = this.runtime.run(new ServerFindWebsiteHostByServerIdCmd(serverId), session);
		return url.toString();
	}

}