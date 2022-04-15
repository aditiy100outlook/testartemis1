/*
 * Created on Feb 16, 2011 by Tony Lindquist
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 *
 */
package com.code42.archive;

import java.util.List;

import com.backup42.social.data.ext.FriendComputerUsageDataProvider;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.server.StorageFindByGuidCmd;
import com.code42.server.cluster.IStorageCluster;
import com.code42.social.FriendComputerUsage;

/**
 * 
 * Determines whether or not the given source computer has an archive on any slave or provider
 * 
 * @author tlindqui
 */
public class ArchiveExistsSlaveCmd extends DBCmd<Boolean> {

	// Properties
	private final long sourceComputerId;

	public ArchiveExistsSlaveCmd(long sourceComputerId) {
		this.sourceComputerId = sourceComputerId;
	}

	@Override
	public Boolean exec(CoreSession session) throws CommandException {
		boolean hasArchive = false;

		List<FriendComputerUsage> friends = FriendComputerUsageDataProvider.findBySource(this.sourceComputerId);
		for (FriendComputerUsage friend : friends) {
			IStorageCluster cluster = this.runtime.run(new StorageFindByGuidCmd(friend.getTargetComputerGuid()), session);
			if (cluster != null) {
				hasArchive = true;
				break;
			}
		}
		return hasArchive;
	}

}
