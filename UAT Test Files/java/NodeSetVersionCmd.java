package com.code42.server.node;

import com.backup42.common.CPVersion;
import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

public class NodeSetVersionCmd extends DBCmd<Void> {

	@Override
	public Void exec(CoreSession session) throws CommandException {
		// Must be admin user
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		final int myId = this.env.getMyNodeId();
		final Node myNode = this.run(new NodeFindByIdCmd(myId), session);
		myNode.setVersion(CPVersion.getProductVersion());

		this.run(NodeUpdateCmd.withNoBroadcast(myNode), session);

		return null;
	}

}
