/**
 * <a href="http://www.code42.com">(c)Code 42 Software, Inc.</a> $Id: $
 */
package com.code42.property;

import java.util.ArrayList;
import java.util.List;

import com.backup42.common.perm.C42PermissionPro;
import com.backup42.history.CpcHistoryLogger;
import com.code42.core.CommandException;
import com.code42.core.IRuntimeConfiguration;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.impl.DBCmd;
import com.code42.hibernate.aftertx.AfterTxRunnable;
import com.code42.hibernate.aftertx.IAfterTxRunnable.Priority;
import com.code42.logging.Logger;
import com.code42.messaging.entityupdate.PropertyDeleteMessage;
import com.code42.server.destination.Destination;
import com.code42.server.destination.DestinationFindByGuidQuery;
import com.code42.server.node.Node;
import com.code42.server.node.NodeFindAllByDestinationIdCmd;
import com.code42.server.node.NodeFindAllQuery;
import com.code42.server.node.NodeFindServerByClusterQuery;
import com.code42.server.node.ServerNode;
import com.code42.server.sync.IEntityNotificationController;
import com.google.inject.Inject;

/**
 * Delete a property from the database and configuration; send out a notification to relevant nodes, depending on the
 * syncCode of the row in the database, if any. At a minimum, tell all authority nodes to remove it.
 */
public class PropertyDeleteCmd extends DBCmd<String> {

	private static final Logger log = Logger.getLogger(PropertyDeleteCmd.class);

	@Inject
	private IRuntimeConfiguration runtimeConfig;
	@Inject
	private IEntityNotificationController entityController;

	private final String name;

	public PropertyDeleteCmd(String name) {
		super();
		this.name = name;
	}

	@Override
	public String exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		try {
			this.db.beginTransaction();

			Property prop = this.deleteProperty(session, this.name);

			if (this.env.isMaster()) {
				List<Node> nodes = this.getNodes(session, prop);
				this.updateNodes(nodes, prop.getValue());
			}

			this.db.commit();

			return prop.getValue();

		} catch (CommandException e) {
			log.error("Unable to remove property: {}", this.name);
			throw e;

		} catch (Throwable t) {
			log.error("Unable to remove property: {}" + this.name);
			throw new CommandException("Exception removing property: name={}", this.name, t);
		} finally {
			this.db.endTransaction();
		}
	}

	private Property deleteProperty(final CoreSession session, final String name) throws CommandException {
		int myClusterId = this.env.getMyClusterId();
		Property prop = this.db.find(new PropertyFindByNameQuery(myClusterId, name));
		if (prop == null) {
			prop = new Property(myClusterId, name);
		} else {
			// Delete it from the database.
			this.db.delete(new PropertyDeleteQuery(prop));
		}

		final String value = (String) this.runtimeConfig.remove(this.name);

		prop.setValue(value);

		this.db.afterTransaction(new AfterTxRunnable(Priority.NORMAL) {

			public void run() {
				CpcHistoryLogger.info(session, "removed system property: {}={}", name, value);
			}
		});

		return prop;
	}

	/**
	 * Get a list of nodes that should get this updated property.
	 * 
	 * @param session
	 * @return
	 * @throws DBServiceException
	 * @throws CommandException
	 */
	private List<Node> getNodes(CoreSession session, Property prop) throws DBServiceException, CommandException {
		List<Node> nodes = new ArrayList<Node>();
		switch (prop.getSyncCode()) {
		case ALL:
			nodes = this.db.find(new NodeFindAllQuery(this.env.getMyClusterId()));
			break;
		case DESTINATION:
			Destination destination = this.db.find(new DestinationFindByGuidQuery(prop.getDestinationGuid()));
			if (destination == null) {
				throw new CommandException("Invalid Destination; guid: {}", prop.getDestinationGuid());
			}
			nodes = this.run(new NodeFindAllByDestinationIdCmd(destination.getDestinationId()), session);
			break;
		case NONE:
			List<ServerNode> serverNodes = this.db.find(new NodeFindServerByClusterQuery(this.env.getMyClusterId()));
			for (ServerNode serverNode : serverNodes) {
				if (serverNode.getNodeId().intValue() != this.env.getMyNodeId()) {
					nodes.add(serverNode);
				}
			}
			break;

		default:
			throw new CommandException("Unrecognized SyncCode: {}", prop.getSyncCode());
		}
		return nodes;
	}

	/**
	 * Send the message to update the nodes.
	 * 
	 * @param nodes
	 * @param finalValue
	 */
	private void updateNodes(final List<Node> nodes, final String finalValue) {
		final String finalName = this.name;

		this.db.afterTransaction(new AfterTxRunnable(Priority.NORMAL) {

			public void run() {
				PropertyDeleteMessage msg = new PropertyDeleteMessage();
				msg.setName(finalName);
				PropertyDeleteCmd.this.entityController.send(msg, nodes);
			}
		});
	}

	// /////////////////////////////////
	// MESSAGE HANDLER IMPLEMENTATIONS
	// /////////////////////////////////
	/**
	 * Another node deleted the property; delete from the config.
	 */
	public static class PropertyDeleteCmdAuthorityImpl extends DBCmd<Void> {

		@Inject
		private IRuntimeConfiguration runtimeConfig;

		private final PropertyDeleteMessage msg;

		public PropertyDeleteCmdAuthorityImpl(PropertyDeleteMessage msg) {
			this.msg = msg;
		}

		@Override
		public Void exec(CoreSession session) throws CommandException {
			String value = (String) this.runtimeConfig.remove(this.msg.getName());
			CpcHistoryLogger.info(session, "REMOTE: delete system property: {}={}", this.msg.getName(), value);
			return null;
		}
	}

	/**
	 * Our master deleted a property; handle it on the storage node.
	 */
	public static class PropertyDeleteCmdStorageImpl extends DBCmd<Void> {

		private final PropertyDeleteMessage msg;

		public PropertyDeleteCmdStorageImpl(PropertyDeleteMessage msg) {
			this.msg = msg;
		}

		@Override
		public Void exec(CoreSession session) throws CommandException {

			String name = this.msg.getName();

			this.run(new PropertyDeleteCmd(name), session);
			return null;
		}
	}

}
