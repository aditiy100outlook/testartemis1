package com.code42.org;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.businessobjects.BusinessObjectsException;
import com.code42.core.businessobjects.IBusinessObjectsService;
import com.code42.core.impl.AbstractCmd;
import com.code42.user.UserSso;
import com.google.inject.Inject;

/**
 * Convert a user ID to an org SSO (Space Storge Object).
 */
public class OrgSsoFindByUserIdCmd extends AbstractCmd<OrgSso> {

	@Inject
	private IBusinessObjectsService bos;

	// Properties
	private final int userId;

	public OrgSsoFindByUserIdCmd(int userId) {
		this.userId = userId;
	}

	@Override
	public OrgSso exec(CoreSession session) throws CommandException {

		try {
			UserSso user = this.bos.getUser(this.userId);
			if (user == null) {
				throw new CommandException("User not found; userId: {}", this.userId);
			}

			return this.runtime.run(new OrgSsoFindByOrgIdCmd(user.getOrgId()), session);
		} catch (BusinessObjectsException e) {
			throw new CommandException("Exception while getting user SSO", e);
		}

	}
}
