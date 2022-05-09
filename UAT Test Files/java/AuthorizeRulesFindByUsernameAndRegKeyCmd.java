package com.code42.account;

import java.util.List;

import com.backup42.account.AccountServices;
import com.backup42.account.exception.InvalidOrgIdException;
import com.backup42.common.AuthorizeRules;
import com.backup42.server.MasterServices;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.crypto.SaltStrategy;
import com.code42.crypto.StringHasher;
import com.code42.logging.Logger;
import com.code42.server.ServerFindMasterClusterQuery;
import com.code42.server.cluster.MasterCluster;
import com.code42.user.User;
import com.code42.user.UserFindByUsernameCmd;
import com.code42.utils.LangUtils;

public class AuthorizeRulesFindByUsernameAndRegKeyCmd extends DBCmd<AuthorizeRules> {

	private final static Logger log = Logger.getLogger(AuthorizeRulesFindByUsernameAndRegKeyCmd.class);

	private String username;
	private String regkey;

	public AuthorizeRulesFindByUsernameAndRegKeyCmd(String username, String regkey) {

		this.username = username;
		this.regkey = regkey;
	}

	@Override
	public AuthorizeRules exec(CoreSession session) throws CommandException {

		/*
		 * TODO: Authorization... what does it look like here? Caller identity could be coming from the user (if provided)
		 * or org (if the user doesn't exist)... but there's no way to know that until later.
		 */

		// this server MUST be acting as a master
		final boolean masterCluster = MasterServices.getInstance().isMasterCluster();
		if (!masterCluster) {

			MasterCluster mc = this.db.find(new ServerFindMasterClusterQuery());
			if (mc == null) {

				/* Throw the exception with the limited info we have */
				throw new SlaveClusterException("INVALID PW/SALT LOGIN TO SLAVE CLUSTER");
			}

			/* We got a master cluster record so add a bit of detail to the exception we're throwing */
			String masterPrimaryAddress = mc.getPrimaryAddress();
			throw new SlaveClusterException("INVALID PW/SALT LOGIN TO SLAVE CLUSTER - Redirect to masterPrimaryAddress="
					+ masterPrimaryAddress, masterPrimaryAddress);
		}

		/* Try to lookup the requested user... we still need to take action even if we don't find them */
		User user = null;
		try {

			UserFindByUsernameCmd.Builder cmdData = new UserFindByUsernameCmd.Builder(this.username);
			List<User> userList = this.runtime.run(cmdData.build(), this.auth.getSystemSession());
			user = (LangUtils.hasElements(userList)) ? userList.get(0) : null;
		} catch (BuilderException e) {
			log.error("Failed to get user from username: " + e.getMessage());
		}

		/* We didn't find the user, so lookup the registration key and take action on it */
		if (user == null) {

			try {

				/*
				 * Generate a random server-side salt here and add it to the outgoing rules. Per CP-6367 we add a salt to ALL
				 * outgoing messages.
				 * 
				 * Old code ran the block below (minus the salt generation) only if the incoming message isn't a "login"
				 * message, but that distinction doesn't mean anything to us... we need to generate the salt anyway (and not
				 * return any kind of failure message) if we can't find the user.
				 */
				final int orgId = AccountServices.getInstance().discoverRegistrationOrg(this.regkey);
				final AuthorizeRules rules = this.runtime.run(new AuthorizeRulesFindByOrgIdCmd(orgId), this.auth
						.getSystemSession());

				/* Org-level authorization rules shouldn't have a salt */
				assert (rules.getSalt() == null);

				/*
				 * TODO: StringHasher.C42 uses the output of SaltStrategy.buildC42() for it's salting implementation. Ideally
				 * these two things would be more tightly coupled; I'd like to call a method on StringHasher.C42 in order to get
				 * the salt strategy (or generate a new salt). I'm reluctant to make such a modification now, however... to be
				 * revisited later.
				 */
				return new AuthorizeRules.Builder(rules).salt(SaltStrategy.buildC42().generateSalt()).build();
			} catch (InvalidOrgIdException ioie) {
				throw new RegistrationOrgDiscoveryException(ioie.getMessage());
			}
		}

		assert (user != null);

		/* If a regkey is specified validate that the org we'd register this user for maps to the current user's org. */
		if (this.regkey != null && (!this.regkey.isEmpty())) {

			try {

				final int orgId = AccountServices.getInstance().discoverRegistrationOrg(this.regkey);
				if (orgId != user.getOrgId()) {
					throw new OrgMismatchException(orgId, user.getOrgId());
				}
			} catch (InvalidOrgIdException ioie) {
				throw new RegistrationOrgDiscoveryException(ioie.getMessage());
			}
		}

		/*
		 * Old code used to only do the following if response.isSuccess() was true. That statement is true when there are
		 * exactly zero errors in the response object, and since in this code we throw exceptions whenever those errors
		 * occur we know we have to execute this code block if we're still here.
		 */
		final AuthorizeRules rules = this.runtime.run(new AuthorizeRulesFindByOrgIdCmd(user.getOrgId()), this.auth
				.getSystemSession());
		if (!rules.isLdap()) {
			final String password = user.getPassword();

			// they must have a valid hash/salt to use the salt
			// (i.e. a place-holder user has an empty password, they are still considered new users)
			if (StringHasher.C42.isValidHash(password)) {

				return new AuthorizeRules.Builder(rules).salt(StringHasher.C42.getSalt(password)).build();
			}
		}
		return rules;
	}

	/* =============================== Inner exception classes =============================== */
	/*
	 * Exception indicating that auth rules was requested from a slave cluster. Ideally exception will include a pointer
	 * to the appropriate master server.
	 */
	public static class SlaveClusterException extends CommandException {

		private static final long serialVersionUID = 1851269585651563240L;

		private String masterPrimaryAddress;

		public SlaveClusterException(String msg) {

			super(msg);
		}

		public SlaveClusterException(String msg, String masterPrimaryAddress) {

			super(msg);
			this.masterPrimaryAddress = masterPrimaryAddress;
		}

		public String getMasterPrimaryAddress() {
			return this.masterPrimaryAddress;
		}
	}

	/* Exception indicating that a user was found and that his org didn't match the org extracted from the reg key */
	public static class OrgMismatchException extends CommandException {

		private static final long serialVersionUID = 5003065700205480612L;

		private int expected;
		private int actual;

		public OrgMismatchException(int expected, int actual) {

			super("OrgMismatchException");
			this.expected = expected;
			this.actual = actual;
		}

		public int getExpected() {
			return this.expected;
		}

		public int getActual() {
			return this.actual;
		}
	}

	/* Exception indicating that an org couldn't be extracted from an otherwise accurate reg key */
	public static class RegistrationOrgDiscoveryException extends CommandException {

		private static final long serialVersionUID = -6426659300953568658L;

		public RegistrationOrgDiscoveryException(String msg) {
			super(msg);
		}
	}
}
