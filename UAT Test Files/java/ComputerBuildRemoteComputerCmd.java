package com.code42.computer;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.crypto.MD5Value;
import com.code42.org.OrgSso;
import com.code42.org.OrgSsoFindByUserIdCmd;
import com.code42.server.BaseServer;
import com.code42.server.BaseServerFindByGuidQuery;
import com.code42.user.UserSso;
import com.code42.user.UserSsoFindByComputerIdCmd;
import com.code42.utils.ByteArray;
import com.code42.utils.LangUtils;
import com.code42.utils.Validate;

/**
 * NOTE: This **DOES NOT** return a Computer with availability and/or usage determined!!!
 * 
 * @param computer
 * @return the remote Computer
 */
public class ComputerBuildRemoteComputerCmd extends DBCmd<com.backup42.common.Computer> {

	private final Computer computer;
	private final boolean self;
	private final boolean own;
	private final Computer connectComputer;

	public ComputerBuildRemoteComputerCmd(Computer computer, boolean self, boolean own) {
		this(computer, self, own, null);
	}

	// Private
	public ComputerBuildRemoteComputerCmd(Computer computer, boolean self, boolean own, Computer connectComputer) {
		this.computer = computer;
		this.self = self;
		this.own = own;
		this.connectComputer = connectComputer;
	}

	@Override
	public com.backup42.common.Computer exec(CoreSession session) throws CommandException {

		final com.backup42.common.Computer remoteComputer = new com.backup42.common.Computer();

		// NOTE: The 'authority' boolean is TRUE for ANY servers in the authority cluster

		// POPULATE ADDRESSES
		// kind of a hack, use the "connect computer" info if present
		this.populateNetworkAddresses(this.connectComputer != null ? this.connectComputer : this.computer, remoteComputer);

		remoteComputer.setGuid(this.computer.getGuid());
		remoteComputer.setName(Validate.filterNoEncode(this.computer.getName()));
		remoteComputer.setUserId(this.computer.getUserId());
		remoteComputer.setSelf(this.self);
		remoteComputer.setOwn(this.own);
		if (this.own) {
			remoteComputer.setBackupCode(this.computer.getBackupCode());
		}

		// If building a computer of your own, it's considered "blocked" if its org, user or computer is blocked
		if (this.self) {
			UserSso user = this.run(new UserSsoFindByComputerIdCmd(this.computer.getComputerId()), session);
			OrgSso org = this.run(new OrgSsoFindByUserIdCmd(user.getUserId()), session);
			boolean blocked = org.isBlocked() || user.isBlocked() || this.computer.getBlocked();
			remoteComputer.setBlocked(blocked);
		} else {
			remoteComputer.setBlocked(this.computer.getBlocked());
		}

		// PRIVATE KEY CHECKSUM
		final String checksumValueHex = this.computer.getDataKeyChecksum();
		if (LangUtils.hasValue(checksumValueHex)) {
			final byte[] bytes = ByteArray.fromHex(checksumValueHex);
			final MD5Value checksum = new MD5Value(bytes);
			remoteComputer.setDataKeyChecksum(checksum);
		}

		// child?
		if (this.computer.isChild()) {
			long parentComputerId = this.computer.getParentComputerId().longValue();
			Computer parent = this.run(new ComputerFindByIdCmd(parentComputerId), session);
			remoteComputer.setParentGuid(new Long(parent.getGuid()));
		}

		return remoteComputer;
	}

	/**
	 * Helper to populate a computer's network addresses based on various rules
	 * 
	 * @param computer
	 * @param remoteComputer
	 */
	private void populateNetworkAddresses(final Computer computer, final com.backup42.common.Computer remoteComputer)
			throws CommandException {
		final boolean isServer = this.serverService.getCache().contains(computer.getGuid());
		if (isServer) {
			/*
			 * SERVER
			 */
			// RULES:
			// CPC/Cluster
			// Not assigned to a server/mount point:
			// Use Cluster's primary address as the Computer's address
			//
			// Assigned (or restore server):
			// If assigned to a specific server, use Physical server's primary address as the Computer's address
			// (THIS IS DONE ABOVE!)
			//
			// PROe Server
			// If both primary & secondary are null, do set an address in the authority's Computer
			// If primary is present, use it to set the Computer's address
			// Then if secondary is present, use it to set the Computer's public address

			// End result of above rules is simply set the values if they exist, the node assignment is handled above
			final BaseServer server = this.db.find(new BaseServerFindByGuidQuery(computer.getGuid()));

			// primary
			final String primaryAddress = server.getPrimaryAddress();
			if (LangUtils.hasValue(primaryAddress)) {
				remoteComputer.setAddress(primaryAddress);

				// secondary
				final String secondaryAddress = server.getSecondaryAddress();
				if (LangUtils.hasValue(secondaryAddress)) {
					remoteComputer.setPublicAddress(secondaryAddress);
				}
			}

		} else {
			/*
			 * COMPUTER
			 */
			remoteComputer.setAddress(computer.getAddress());
			remoteComputer.setPublicAddress(computer.getRemoteAddress());
		}
	}

}
