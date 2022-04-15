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
import com.code42.messaging.entityupdate.PropertyUpdateMessage;
import com.code42.property.Property.C42Sync;
import com.code42.server.destination.Destination;
import com.code42.server.destination.DestinationFindByGuidQuery;
import com.code42.server.node.Node;
import com.code42.server.node.NodeFindAllByDestinationIdCmd;
import com.code42.server.node.NodeFindAllQuery;
import com.code42.server.node.NodeFindServerByClusterQuery;
import com.code42.server.node.ServerNode;
import com.code42.server.sync.IEntityNotificationController;
import com.code42.server.sync.SyncProperty;
import com.google.inject.Inject;

/**
 * Publish the new property and update all interested nodes, according to the following chart:
 * 
 * <ol>
 * <li>persist=false: local only; no other node is informed of change</li>
 * <li>persist=true, sync=ALL; all nodes are informed of change</li>
 * <li>persist=true, sync=DESTINATION; all destination nodes are informed of change (ClusterDestination only)</li>
 * <li>persist=true, sync=NONE; all authority nodes are informed of change</li>
 * </ol>
 */
public class PropertySetCmd extends DBCmd<Void> {

	private static Logger log = Logger.getLogger(PropertySetCmd.class);

	@Inject
	private IRuntimeConfiguration runtimeConfig;
	@Inject
	private IEntityNotificationController entityController;

	private final String name;
	private final String value;
	private final boolean persist;
	private final C42Sync syncCode;
	private final Long destinationGuid;

	public PropertySetCmd(String name, String value, boolean persist) {
		this(name, value, persist, C42Sync.NONE, null);
	}

	public PropertySetCmd(String name, String value, boolean persist, C42Sync syncCode) {
		this(name, value, persist, syncCode, null);
	}

	public PropertySetCmd(String name, String value, boolean persist, C42Sync syncCode, Long destinationGuid) {
		this.name = name;
		this.value = value;
		this.syncCode = syncCode;
		this.destinationGuid = destinationGuid;
		this.persist = persist;
	}

	public PropertySetCmd(SyncProperty p) {
		this.name = p.getName();
		this.value = p.getValue();
		this.syncCode = p.getSyncCode();
		this.destinationGuid = p.getDestinationGuid();
		this.persist = true;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		if (C42Sync.DESTINATION == this.syncCode && this.destinationGuid == null) {
			throw new CommandException("Invalid Command; SyncCode=DESTINATION, but no destination provided");
		}

		try {
			this.db.beginTransaction();

			this.persistProperty(this.name, this.value, this.syncCode, this.destinationGuid, session);

			if (this.env.isMaster() && this.persist) {
				List<Node> nodes = this.getNodes(session);
				this.updateNodes(session, nodes);
			}

			this.db.commit();
		} catch (CommandException e) {
			PropertySetCmd.logHistory(session, "Unable to set", this.name, this.value);
			throw e;

		} catch (Throwable t) {
			log.error("Unable to set property: {}={}", this.name, this.value);
			throw new CommandException("Exception setting property", t);
		} finally {
			this.db.endTransaction();
		}

		return null;
	}

	private static void logHistory(CoreSession session, String msgPrefix, String name, String value) {
		if (Property.isSensitive(name)) {
			CpcHistoryLogger.info(session, "{} system property: {}=******* (obscured)", msgPrefix, name);
		} else {
			CpcHistoryLogger.info(session, "{} system property: {}={}", msgPrefix, name, value);
		}
	}

	/**
	 * Persist the property in the config and database, if so indicated.
	 * 
	 * @param name
	 * @param value
	 * @throws CommandException
	 */
	private void persistProperty(final String name, final String value, C42Sync syncCode, Long destinationGuid,
			final CoreSession session) throws CommandException {

		if (this.persist) {

			final int myClusterId = this.env.getMyClusterId();
			Property prop = this.db.find(new PropertyFindByNameQuery(myClusterId, name));
			boolean create = false;
			if (prop == null) {
				create = true;
				prop = new Property(myClusterId, name);
			}
			prop.setValue(value);
			prop.setSyncCode(syncCode);
			prop.setDestinationGuid(destinationGuid);

			if (create) {
				this.db.create(new PropertyCreateQuery(prop));
			} else {
				this.db.update(new PropertyUpdateQuery(prop));
			}
		}

		this.db.afterTransaction(new AfterTxRunnable(Priority.NORMAL) {

			public void run() {
				PropertySetCmd.this.runtimeConfig.put(name, value);
				PropertySetCmd.logHistory(session, "Set", name, value);
			}
		});
	}

	/**
	 * Get a list of nodes that should get this updated property.
	 * 
	 * @param session
	 * @return
	 * @throws DBServiceException
	 * @throws CommandException
	 */
	private List<Node> getNodes(CoreSession session) throws DBServiceException, CommandException {
		List<Node> nodes = new ArrayList<Node>();
		switch (this.syncCode) {
		case ALL:
			List<Node> allNodes = this.db.find(new NodeFindAllQuery(this.env.getMyClusterId()));
			for (Node node : allNodes) {
				if (node.getNodeId().intValue() != this.env.getMyNodeId()) {
					nodes.add(node);
				}
			}
			break;
		case DESTINATION:
			Destination destination = this.db.find(new DestinationFindByGuidQuery(this.destinationGuid));
			if (destination == null) {
				throw new CommandException("Invalid Destination; guid: {}", this.destinationGuid);
			}
			List<Node> dNodes = this.run(new NodeFindAllByDestinationIdCmd(destination.getDestinationId()), session);
			for (Node node : dNodes) {
				if (node.getNodeId().intValue() != this.env.getMyNodeId()) {
					nodes.add(node);
				}
			}
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
			throw new CommandException("Unrecognized SyncCode: {}", this.syncCode);
		}
		return nodes;
	}

	/**
	 * Send the message to update the nodes.
	 * 
	 * @param session
	 * @param nodes
	 */
	private void updateNodes(final CoreSession session, final List<Node> nodes) {
		final String finalName = this.name;
		final String finalValue = this.value;

		this.db.afterTransaction(new AfterTxRunnable(Priority.NORMAL) {

			public void run() {
				PropertyUpdateMessage msg = new PropertyUpdateMessage();
				msg.setName(PropertySetCmd.this.name);
				msg.setValue(PropertySetCmd.this.value);
				msg.setSyncCode(PropertySetCmd.this.syncCode);
				msg.setDestinationGuid(PropertySetCmd.this.destinationGuid);
				PropertySetCmd.this.entityController.send(msg, nodes);
				PropertySetCmd.logHistory(session, "Saved", finalName, finalValue);
			}
		});
	}

	// /////////////////////////////////
	// MESSAGE HANDLER IMPLEMENTATIONS
	// /////////////////////////////////
	/**
	 * Another node updated the property; update the config.
	 */
	public static class PropertySetCmdAuthorityImpl extends DBCmd<Void> {

		@Inject
		private IRuntimeConfiguration runtimeConfig;

		private final PropertyUpdateMessage msg;

		public PropertySetCmdAuthorityImpl(PropertyUpdateMessage msg) {
			this.msg = msg;
		}

		@Override
		public Void exec(CoreSession session) throws CommandException {
			this.runtimeConfig.put(this.msg.getName(), this.msg.getValue());
			PropertySetCmd.logHistory(session, "REMOTE: set", this.msg.getName(), this.msg.getValue());
			return null;
		}
	}

	/**
	 * Our master updated a property; handle it on the storage node.
	 */
	public static class PropertySetCmdStorageImpl extends DBCmd<Void> {

		private final PropertyUpdateMessage msg;

		public PropertySetCmdStorageImpl(PropertyUpdateMessage msg) {
			this.msg = msg;
		}

		@Override
		public Void exec(CoreSession session) throws CommandException {

			String name = this.msg.getName();
			String value = this.msg.getValue();
			C42Sync syncCode = this.msg.getSyncCode();
			Long destinationGuid = this.msg.getDestinationGuid();
			boolean persist = true;

			this.run(new PropertySetCmd(name, value, persist, syncCode, destinationGuid), session);
			return null;
		}
	}
}
