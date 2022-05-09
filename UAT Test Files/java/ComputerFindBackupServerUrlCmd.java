package com.code42.computer;

import java.net.URL;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.server.ServerFindWebsiteHostByServerIdCmd;
import com.code42.server.mount.MountPoint;
import com.code42.server.mount.MountPointFindByIdQuery;
import com.code42.social.FriendComputerUsage;

/**
 * Command to retrieve the server url for the given source GUID.
 * 
 * @author mscorcio
 */
public class ComputerFindBackupServerUrlCmd extends DBCmd<String> {

	private final long srcGuid;
	private final long destGuid;

	public ComputerFindBackupServerUrlCmd(long srcGuid, long destGuid) {
		this.srcGuid = srcGuid;
		this.destGuid = destGuid;
	}

	@Override
	public String exec(CoreSession session) throws CommandException {
		ComputerSso c = this.runtime.run(new ComputerSsoFindByGuidCmd(this.srcGuid), session);

		if (c == null) {
			throw new CommandException("Unable to find computer with guid: " + this.srcGuid);
		}

		this.runtime.run(new IsComputerManageableCmd(c.getComputerId(), C42PermissionApp.Computer.READ), session);

		FriendComputerUsage fcu = this.db.find(new FriendComputerUsageFindBySourceGuidAndTargetGuidQuery(this.srcGuid,
				this.destGuid));

		if (fcu == null || fcu.getMountPointId() == null) {
			return null;
		}

		MountPoint mp = this.db.find(new MountPointFindByIdQuery(fcu.getMountPointId()));
		int serverId = mp.getServerId();
		URL url = this.runtime.run(new ServerFindWebsiteHostByServerIdCmd(serverId), session);
		return url.toString();
	}
}