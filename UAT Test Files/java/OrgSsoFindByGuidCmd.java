package com.code42.org;

import com.code42.computer.ComputerSso;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.businessobjects.BusinessObjectsException;
import com.code42.core.businessobjects.IBusinessObjectsService;
import com.code42.core.impl.AbstractCmd;
import com.code42.user.UserSso;
import com.google.inject.Inject;

/**
 * Convert a GUID to an org SSO (Space Storge Object). Re-use the caching commands in order to maximize our use of the
 * space cache.
 */
public class OrgSsoFindByGuidCmd extends AbstractCmd<OrgSso> {

	@Inject
	private IBusinessObjectsService bos;

	// Properties
	private final long guid;

	public OrgSsoFindByGuidCmd(long guid) {
		this.guid = guid;
	}

	@Override
	public OrgSso exec(CoreSession session) throws CommandException {

		try {
			ComputerSso computer = this.bos.getComputerByGuid(this.guid);
			if (computer == null) {
				throw new CommandException("Computer not found; guid: {}", this.guid);
			}

			UserSso user = this.bos.getUser(computer.getUserId());
			if (user == null) {
				throw new CommandException("User not found for this computer; computer: {}", computer);
			}

			return this.runtime.run(new OrgSsoFindByOrgIdCmd(user.getOrgId()), session);
		} catch (BusinessObjectsException e) {
			throw new CommandException("Exception while getting user SSO", e);
		}

	}
}
