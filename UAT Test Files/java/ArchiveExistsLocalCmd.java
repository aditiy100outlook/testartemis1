/*
 * Created on Feb 16, 2011 by Tony Lindquist <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.archive;

import com.code42.computer.FriendComputerUsageFindBySourceGuidAndTargetGuidQuery;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.core.server.IServerService;
import com.code42.social.FriendComputerUsage;
import com.google.inject.Inject;

/**
 * 
 * Command to determine whether or not the given source computer has an archive on this server
 * 
 * @author tlindqui
 */
public class ArchiveExistsLocalCmd extends DBCmd<Boolean> {

	@Inject
	private IServerService serverService;

	// Properties
	private final long sourceGuid;

	public ArchiveExistsLocalCmd(long sourceGuid) {
		this.sourceGuid = sourceGuid;
	}

	@Override
	public Boolean exec(CoreSession session) throws CommandException {
		boolean hasArchive = false;
		// is there a FCU for the source and this server's destination?
		final long destinationGuid = this.serverService.getMyDestination().getDestinationGuid();
		FriendComputerUsage fcu = this.db.find(new FriendComputerUsageFindBySourceGuidAndTargetGuidQuery(this.sourceGuid,
				destinationGuid));
		hasArchive = (fcu != null) && fcu.isUsing();
		return hasArchive;
	}
}
