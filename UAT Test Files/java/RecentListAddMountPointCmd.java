package com.code42.recent;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.server.mount.MountPointDto;

public class RecentListAddMountPointCmd extends AbstractCmd<Void> {

	private MountPointDto mountPoint;

	public RecentListAddMountPointCmd(MountPointDto mountPoint) {
		this.mountPoint = mountPoint;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		RecentMountPoint rm = new RecentMountPoint(this.mountPoint.getMountPointId(), this.mountPoint.getName());
		this.runtime.run(new RecentListAddItemCmd(rm), session);

		return null;
	}
}
