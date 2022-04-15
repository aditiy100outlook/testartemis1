/*
 * Created on Feb 17, 2011 by Tony Lindquist <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.code42.org.HostedParentOrg;
import com.code42.org.HostedParentOrgFindAllCmd;
import com.code42.server.cluster.IMasterCluster;
import com.code42.server.cluster.MasterCluster;

/**
 * 
 * Get all masters (i.e. the one and only MasterCluster or the HostedParentOrgs)
 * 
 * @author tlindqui
 */
public class ServerFindAllMastersCmd extends DBCmd<List<IMasterCluster>> {

	@Override
	public List<IMasterCluster> exec(CoreSession session) throws CommandException {
		List<IMasterCluster> masters = new ArrayList<IMasterCluster>();
		MasterCluster master = this.db.find(new ServerFindMasterClusterQuery());
		if (master != null) {
			masters.add(master);
		} else {
			// do we have slave parent org?
			Collection<HostedParentOrg> orgs = CoreBridge.runNoException(new HostedParentOrgFindAllCmd());
			for (HostedParentOrg org : orgs) {
				masters.add(org);
			}
		}
		return masters;
	}

}
