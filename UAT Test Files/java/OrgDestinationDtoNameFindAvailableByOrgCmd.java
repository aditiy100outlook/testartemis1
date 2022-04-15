package com.code42.org.destination;

import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

/**
 * Find the available dtos for this org.
 */
public class OrgDestinationDtoNameFindAvailableByOrgCmd extends DBCmd<List<OrgDestinationDtoName>> {

	private final int orgId;

	public OrgDestinationDtoNameFindAvailableByOrgCmd(int orgId) {
		super();
		this.orgId = orgId;
	}

	@Override
	public List<OrgDestinationDtoName> exec(CoreSession session) throws CommandException {

		// no auth is necessary; we're merely composing the more full-featured command

		final List<OrgDestination> orgDestinations = this.run(new OrgDestinationFindAvailableByOrgCmd(this.orgId, false),
				session);
		final List<OrgDestinationDtoName> dtos = this.db.find(new OrgDestinationDtoNameFindByOrgQuery(orgDestinations));
		return dtos;
	}
}
