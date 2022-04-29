package com.code42.server;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.server.cluster.IStorageCluster;
import com.code42.server.cluster.ProviderCluster;
import com.code42.server.cluster.ProviderClusterFindByGuidQuery;
import com.code42.server.node.NodeFindStorageByGuidQuery;

/**
 * Get the matching slave cluster (either SlaveCluster or SlaveProviderCluster)
 */
public class StorageFindByGuidCmd extends DBCmd<IStorageCluster> {

	private final long slaveClusterGuid;

	public StorageFindByGuidCmd(long slaveClusterGuid) {
		this.slaveClusterGuid = slaveClusterGuid;
	}

	@Override
	public IStorageCluster exec(CoreSession session) throws CommandException {

		IStorageCluster storageNode = this.db.find(new NodeFindStorageByGuidQuery(this.slaveClusterGuid));
		if (storageNode != null) {
			return storageNode;

		} else {
			// do we have slave provider records?
			ProviderCluster providerCluster = this.db.find(new ProviderClusterFindByGuidQuery(this.slaveClusterGuid));
			return providerCluster;
		}
	}
}
