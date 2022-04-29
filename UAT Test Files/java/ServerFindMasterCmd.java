/*
 * Created on Feb 17, 2011 by Tony Lindquist <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.server;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.org.HostedParentOrg;
import com.code42.org.HostedParentOrgFindByGuidQuery;
import com.code42.server.cluster.IMasterCluster;
import com.code42.server.cluster.MasterCluster;

/**
 * 
 * Get the matching master cluster (either MasterCluster or HostedParentOrg)
 * 
 * @author tlindqui
 */
public class ServerFindMasterCmd extends DBCmd<IMasterCluster> {

	private final long guid;

	public ServerFindMasterCmd(long guid) {
		this.guid = guid;
	}

	@Override
	public IMasterCluster exec(CoreSession session) throws CommandException {
		MasterCluster master = this.db.find(new ServerFindMasterClusterQuery());
		if (master != null) {
			if (master.getClusterGuid() == this.guid) {
				return master;
			} else {
				// if there is a master cluster then this server is in slave mode, there cannot be any master provider records
				return null;
			}
		} else {
			// do we have slave parent org?
			HostedParentOrg hostedParentOrg = this.db.find(new HostedParentOrgFindByGuidQuery(this.guid));
			return hostedParentOrg;
		}
	}

}
