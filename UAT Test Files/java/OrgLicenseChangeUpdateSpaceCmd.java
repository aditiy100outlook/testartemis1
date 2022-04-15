package com.code42.license;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.core.space.ISpaceService;
import com.code42.core.space.SpaceException;
import com.code42.space.SpaceKeyUtils;
import com.google.inject.Inject;

public class OrgLicenseChangeUpdateSpaceCmd extends AbstractCmd<Void> {

	private int orgId;

	@Inject
	private ISpaceService space;

	public OrgLicenseChangeUpdateSpaceCmd(int orgId) {
		this.orgId = orgId;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		//
		// This is something of a hack. We are using Hazelcast for a distributed notification system. IOW, we really don't
		// care about what is stored in the space, just about notifying anyone in the cluster listening for changes on a
		// key.
		//
		try {

			this.space.put(SpaceKeyUtils.getOrgLicenseKey(this.orgId), new Long(System.currentTimeMillis()));

		} catch (SpaceException e) {
			throw new CommandException(e);
		}

		return null;
	}
}
