package com.code42.server.node;

import com.code42.computer.ComputerSso;
import com.code42.computer.ComputerSsoFindByGuidCmd;
import com.code42.computer.FriendComputerUsageFindBySourceGuidAndTargetGuidQuery;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.social.FriendComputerUsage;

/**
 * Find the Node that actually holds the archive for the given source and Destination GUID pair.
 */
public class NodeFindBySourceAndDestinationCmd extends DBCmd<Node> {

	private final long sourceGuid;
	private final long destinationGuid;

	public NodeFindBySourceAndDestinationCmd(long sourceGuid, long destinationGuid) {
		this.sourceGuid = sourceGuid;
		this.destinationGuid = destinationGuid;
	}

	@Override
	public Node exec(CoreSession session) throws CommandException {
		ComputerSso c = this.run(new ComputerSsoFindByGuidCmd(this.sourceGuid), session);
		this.run(new IsComputerManageableCmd(c.getComputerId(), C42PermissionApp.Computer.READ), session);

		FriendComputerUsage fcu = this.db.find(new FriendComputerUsageFindBySourceGuidAndTargetGuidQuery(this.sourceGuid,
				this.destinationGuid));
		if (fcu == null) {
			return null;
		}
		return this.db.find(new NodeFindByMountQuery(fcu.getMountPointId()));
	}

}
