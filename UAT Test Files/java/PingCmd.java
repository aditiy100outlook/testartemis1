package com.code42.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.backup42.app.cpc.clusterpeer.PeerCommunicationException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.messaging.MessagingTransport;
import com.code42.messaging.PingServerRequestMessage;
import com.code42.messaging.PingServerResponseMessage;
import com.code42.server.node.Node;
import com.code42.server.node.NodeFindStorageNodesByAssignedMasterQuery;
import com.code42.utils.Time;
import com.google.inject.Inject;

/**
 * Ping a set of nodes to determine if they are available for communication.
 * 
 * Storage nodes will only ever ping their authority node.
 * 
 */
public class PingCmd extends DBCmd<List<Long>> {

	final private static Logger log = LoggerFactory.getLogger(PingCmd.class);

	final private static long TIMEOUT = 10 * Time.SECOND;

	private List<Node> nodes = null;

	@Inject
	private MessagingTransport messaging;

	public PingCmd() {
	}

	public PingCmd(List<Node> nodes) {
		if (nodes != null) {
			this.nodes = new ArrayList<Node>(nodes);
		}
	}

	@Override
	public List<Long> exec(CoreSession session) throws CommandException {

		List<Long> successfulGuids = new LinkedList<Long>();

		if (this.env.isMaster()) {

			if (this.nodes == null) {
				// If there isn't a list of nodes then ping all storage nodes assigned to this master
				this.nodes = new ArrayList<Node>(this.db.find(new NodeFindStorageNodesByAssignedMasterQuery(this.env
						.getMyNodeGuid())));
			}

			if (!this.nodes.isEmpty()) {
				Map<Long, Future<Long>> futures = new HashMap<Long, Future<Long>>(this.nodes.size());
				for (Node node : this.nodes) {
					if (node != null) {
						long guid = node.getNodeGuid();
						futures.put(guid, this.runtime.runAsync(new SendPingCmd(node), session));
					}
				}

				for (Entry<Long, Future<Long>> future : futures.entrySet()) {

					try {
						Long result = future.getValue().get(15, TimeUnit.SECONDS);
						if (result != null) {
							successfulGuids.add(result);
						}
					} catch (TimeoutException e) {
						log.warn("PING:: No response from node " + future.getKey());
					} catch (Exception e) {
						// Do nothing, just don't add it to the list.
					}
				}

			}

		} else {
			// On storage nodes just send a message directly to the authority
			try {
				if (null != this.messaging.sendToAuthorityAndBlock(new PingServerRequestMessage(),
						PingServerResponseMessage.class, TIMEOUT)) {
					log.info("PING:: Received ping response from authority");
					successfulGuids.add(this.serverService.getCache().getMasterClusterGuid());
				}
			} catch (PeerCommunicationException pce) {
				log.warn("PING:: No response from authority");
			}
		}

		return successfulGuids;
	}

	private class SendPingCmd extends AbstractCmd<Long> {

		private final Node node;

		SendPingCmd(Node node) {
			this.node = node;
		}

		@Override
		public Long exec(CoreSession session) throws CommandException {
			try {
				if (null != PingCmd.this.messaging.sendToNodePeerAndBlock(this.node, new PingServerRequestMessage(),
						PingServerResponseMessage.class, TIMEOUT)) {
					log.info("PING:: Received ping response from node " + this.node.getNodeGuid());
				}
			} catch (PeerCommunicationException pce) {
				log.warn("PING:: No response from node " + this.node.getNodeGuid());
				return null;
			}

			return this.node.getNodeGuid();
		}

	}
}
