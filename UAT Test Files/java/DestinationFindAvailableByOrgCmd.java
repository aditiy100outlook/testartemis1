package com.code42.org.destination;

import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.code42.server.destination.Destination;
import com.code42.server.destination.DestinationFindAvailableByUserCmd;
import com.code42.server.destination.DestinationFindByOrgDestinationsCmd;

/**
 * Alternative API for those not interested in working with the base OrgDestination objects.
 * 
 * Consider using DestinationFindAvailableByUserCmd instead, if you are looking for per-user destination availability.
 * 
 * @see DestinationFindAvailableByUserCmd
 */
public class DestinationFindAvailableByOrgCmd extends DBCmd<List<Destination>> {

	private final int orgId;
	private final boolean inheritedOnly;

	public DestinationFindAvailableByOrgCmd(int orgId) {
		this(orgId, false);
	}

	public DestinationFindAvailableByOrgCmd(int orgId, boolean inheritedOnly) {
		super();
		this.orgId = orgId;
		this.inheritedOnly = inheritedOnly;
	}

	@Override
	public List<Destination> exec(CoreSession session) throws CommandException {

		// no auth required, we can defer to OrgDestinationFind..

		final List<OrgDestination> orgDestinations = CoreBridge.run(new OrgDestinationFindAvailableByOrgCmd(this.orgId,
				this.inheritedOnly));
		final List<Destination> destinations = this.run(new DestinationFindByOrgDestinationsCmd(orgDestinations), this.auth
				.getAdminSession());
		return destinations;
	}
}