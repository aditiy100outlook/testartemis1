package com.code42.org;

import com.backup42.account.AccountServices;
import com.backup42.account.exception.InvalidOrgIdException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;

/**
 * Business logic to extract an org ID from a registration key. This functionality used to live in
 * {@link AccountServices}. <br>
 * <br>
 * At the moment this is just a cut-through to AccountService.discoverRegistrationOrg(). Ideally the actual contents of
 * that method (and resolveRegistrationKey()) will be migrated to this command, but for now all callers take different
 * action based on the InvalidOrgIdException thrown by the AccountServices method... so we leave it alone for now. <br>
 * <br>
 * Sigh.
 * 
 * @author bmcguire
 */
public class OrgDiscoverFromRegistrationKeyCmd extends AbstractCmd<Integer> {

	private String registrationKey;

	public OrgDiscoverFromRegistrationKeyCmd(String registrationKey) {

		this.registrationKey = registrationKey;
	}

	@Override
	public Integer exec(CoreSession session) throws CommandException {

		try {

			return AccountServices.getInstance().discoverRegistrationOrg(this.registrationKey);
		} catch (InvalidOrgIdException ioe) {

			throw new CommandException("Invalid registration key", ioe);
		}
	}
}
