/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.org;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import com.backup42.app.license.MasterLicenseService;
import com.backup42.computer.TransportKeyServices;
import com.code42.core.CommandException;
import com.code42.core.IEnvironment;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.crypto.X509PublicKey;
import com.code42.org.destination.OrgDestination;
import com.code42.org.destination.OrgDestinationFindAvailableByOrgCmd;
import com.code42.server.DatabaseNotEmptyException;
import com.code42.server.destination.DestinationFindByIdCmd;
import com.code42.server.destination.IDestination;
import com.code42.server.license.MasterLicense;
import com.code42.server.node.Node;
import com.code42.server.node.NodeFindByIdQuery;
import com.code42.server.node.ProviderConnectionInfo;
import com.code42.utils.LangUtils;
import com.google.inject.Inject;

/**
 * Generate the connection string for this Hosted Org (HostedParentOrg); the string is a Base64 encoded String suitable
 * for copy/paste or an email. This is string is generated on a given Master for the purpose of passing it to another
 * master to setup a Provider relationship. The string is generated on the "Provider-to-be", based on an existing
 * HostedParentOrg, which must already be configured, saved to the database and have at least one Destination assigned.
 */
public class HostedParentOrgCreateConnectionStringCmd extends DBCmd<String> {

	public enum Error {
		NOT_HOSTED, NO_DESTINATIONS
	}

	@Inject
	private IEnvironment environment;

	private final int orgId;

	public HostedParentOrgCreateConnectionStringCmd(int orgId) {
		this.orgId = orgId;
	}

	@Override
	public String exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionApp.AllOrg.ALL);

		boolean isMaster = this.env.isMaster();

		if (!isMaster) {
			throw new DatabaseNotEmptyException("The server must be a Master in order to perform as a Provider.");
		}

		// Get my ServerNode
		int myNodeId = this.environment.getMyNodeId();
		Node myNode = this.db.find(new NodeFindByIdQuery(myNodeId));

		BackupOrg org = this.db.find(new OrgFindByIdQuery(this.orgId));

		if (!(org instanceof HostedParentOrg)) {
			throw new CommandException(Error.NOT_HOSTED,
					"Invalid Org; only Hosted orgs can be used to setup a Provider relationship.: " + org);
		}

		// Get list of destinations.
		final List<IDestination> destinations = new ArrayList<IDestination>();
		{
			final OrgDestinationFindAvailableByOrgCmd cmd = new OrgDestinationFindAvailableByOrgCmd(this.orgId);
			final List<OrgDestination> orgDestinations = this.runtime.run(cmd, session);
			if (!LangUtils.hasElements(orgDestinations)) {
				throw new CommandException(Error.NO_DESTINATIONS, "Invalid Setup: hosted org has no destinations");
			}
			for (OrgDestination orgDestination : orgDestinations) {
				final DestinationFindByIdCmd destCmd = new DestinationFindByIdCmd(orgDestination.getDestinationId());
				final IDestination destination = this.runtime.run(destCmd, session);
				destinations.add(destination);
			}
		}

		KeyPair keyPair;
		try {
			keyPair = TransportKeyServices.getInstance().getTransportKeyPair(this.env.getMyClusterComputerId());
		} catch (Exception e) {
			throw new CommandException("Error creating connection string; crypto issue", e);
		}

		MasterLicense masterLicense = MasterLicenseService.getInstance().getMasterLicense();
		String masterRegKey = masterLicense.getRegistrationKey();
		String orgRegKey = org.getRegistrationKey();
		Long maxBytes = org.getMaxBytes();
		X509PublicKey publicKey = new X509PublicKey(keyPair.getPublic());
		ProviderConnectionInfo info = new ProviderConnectionInfo(myNode, masterRegKey, orgRegKey, maxBytes, publicKey,
				destinations);
		return info.encode();
	}
}
