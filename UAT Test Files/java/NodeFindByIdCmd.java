package com.code42.server.node;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

/**
 * Wrapper for NodeFindByIdQuery
 * 
 * @author mharper
 */
public class NodeFindByIdCmd extends DBCmd<Node> {

	private final int nodeId;

	public NodeFindByIdCmd(int nodeId) {
		this.nodeId = nodeId;
	}

	@Override
	public Node exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);
		return this.db.find(new NodeFindByIdQuery(this.nodeId));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + this.nodeId;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (this.getClass() != obj.getClass()) {
			return false;
		}
		NodeFindByIdCmd other = (NodeFindByIdCmd) obj;
		if (this.nodeId != other.nodeId) {
			return false;
		}
		return true;
	}

}
