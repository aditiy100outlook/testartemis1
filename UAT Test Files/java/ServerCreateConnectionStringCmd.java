/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.server;

import java.security.KeyPair;
import java.util.List;

import com.backup42.app.license.MasterLicenseService;
import com.backup42.common.perm.C42PermissionPro;
import com.backup42.computer.TransportKeyServices;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.crypto.X509PublicKey;
import com.code42.server.cluster.DatabaseIsEmptyCmd;
import com.code42.server.mount.MountPoint;
import com.code42.server.mount.MountPointFindByServerQuery;
import com.code42.server.node.BaseConnectionInfo;
import com.code42.server.node.Node;
import com.code42.server.node.NodeFindByIdQuery;
import com.code42.server.node.StorageNodeConnectionInfo;

/**
 * Generate the connection string for this master server; the string is a Base64 encoded String suitable for copy/paste
 * or an email. This string is ONLY used for converting an existing empty Master into a StorageNode attached to another
 * Master and that master's Cluster.
 */
public class ServerCreateConnectionStringCmd extends DBCmd<String> {

	@Override
	public String exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		boolean isMaster = this.env.isMaster();

		if (!isMaster || !this.run(new DatabaseIsEmptyCmd(), session)) {
			throw new DatabaseNotEmptyException("The database is not empty; conversion to StorageNode is not allowed.");
		}

		// Get my ServerNode
		int myNodeId = this.env.getMyNodeId();
		Node myNode = this.db.find(new NodeFindByIdQuery(myNodeId));

		// Get the regKey for this installation (i.e. the one from the MLK)
		String masterRegKey = MasterLicenseService.getInstance().getMasterLicense().getRegistrationKey();

		KeyPair keyPair;
		try {
			keyPair = TransportKeyServices.getInstance().getTransportKeyPair(this.env.getMyClusterComputerId());
		} catch (Exception e) {
			throw new CommandException("Error creating connection string; crypto issue", e);
		}
		X509PublicKey publicKey = new X509PublicKey(keyPair.getPublic());

		List<MountPoint> mPoints = this.db.find(new MountPointFindByServerQuery(myNode.getNodeId()));
		BaseConnectionInfo info = new StorageNodeConnectionInfo(myNode, masterRegKey, publicKey, mPoints);
		return info.encode();
	}
}
