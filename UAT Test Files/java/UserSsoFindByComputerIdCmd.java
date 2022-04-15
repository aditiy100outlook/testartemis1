package com.code42.user;

import com.code42.computer.ComputerSso;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.businessobjects.BusinessObjectsException;
import com.code42.core.businessobjects.IBusinessObjectsService;
import com.code42.core.impl.AbstractCmd;
import com.google.inject.Inject;

/**
 * Convert a computer ID to a user SSO. Re-use the caching commands in order to maximize our use of the space cache.
 */
public class UserSsoFindByComputerIdCmd extends AbstractCmd<UserSso> {

	@Inject
	private IBusinessObjectsService bos;

	// Properties
	private final long computerId;

	public UserSsoFindByComputerIdCmd(long computerId) {
		this.computerId = computerId;
	}

	@Override
	public UserSso exec(CoreSession session) throws CommandException {

		try {
			ComputerSso computer = this.bos.getComputer(this.computerId);
			if (computer == null) {
				throw new CommandException("Computer not found; computerId: {}", this.computerId);
			}

			return this.runtime.run(new UserSsoFindByUserIdCmd(computer.getUserId()), session);

		} catch (BusinessObjectsException e) {
			throw new CommandException("Exception while getting user SSO", e);
		}

	}
}
