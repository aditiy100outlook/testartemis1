package com.code42.alert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.backup42.CpcConstants;
import com.backup42.EmailPaths;
import com.backup42.history.CpcHistoryLogger;
import com.backup42.server.CpcEmailContext;
import com.code42.core.CommandException;
import com.code42.core.alert.SystemAlert;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.content.IContentService;
import com.code42.core.content.IResource;
import com.code42.core.impl.DBCmd;
import com.code42.core.space.CoreSpace;
import com.code42.core.space.DefaultSpaceNamespace;
import com.code42.core.space.ISpaceService;
import com.code42.core.space.InvalidSpaceKeyException;
import com.code42.core.space.NameValueKeys;
import com.code42.core.space.SpaceException;
import com.code42.core.space.SpaceNamingStrategy;
import com.code42.core.space.mapreduce.Mapper;
import com.code42.core.space.mapreduce.Reducer;
import com.code42.email.Emailer;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.server.SystemAlertRecipientsFindCmd;
import com.code42.server.node.Node;
import com.code42.server.node.NodeFindByGuidQuery;
import com.code42.space.SpaceKeyUtils;
import com.code42.utils.LangUtils;
import com.code42.utils.Pair;
import com.code42.utils.SystemProperties;
import com.code42.utils.SystemProperty;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

/**
 * Receives system alerts from a each destination and aggregates them into space. Only the primary master does this.
 */
public class SystemAlertReceiveCmd extends DBCmd<Void> {

	private static final Logger log = LoggerFactory.getLogger(SystemAlertReceiveCmd.class);

	private static final Object[] monitor = new Object[0];

	@Inject
	private IContentService contentService;

	private static final Set<String> EMAILABLE_TYPES = new HashSet<String>();

	static {
		EMAILABLE_TYPES.add(SystemAlert.Type.DATABASE_EXPORT_ALERT.name());
		EMAILABLE_TYPES.add(SystemAlert.Type.DIRECTORY_SYNC_DEACTIVATE_ALERT.name());
		EMAILABLE_TYPES.add(SystemAlert.Type.LDAP_CONNECTION_ALERT.name());
		EMAILABLE_TYPES.add(SystemAlert.Type.RADIUS_CONNECTION_ALERT.name());
		EMAILABLE_TYPES.add(SystemAlert.Type.SERVER_OFFLINE_ALERT.name());
		EMAILABLE_TYPES.add(SystemAlert.Type.SERVER_OUTOFDATE_ALERT.name());
		EMAILABLE_TYPES.add(SystemAlert.Type.STORE_POINT_ARCHIVE_CORRUPTION.name()); // email, test,
		EMAILABLE_TYPES.add(SystemAlert.Type.STORE_POINT_OFFLINE_ALERT.name());
		EMAILABLE_TYPES.add(SystemAlert.Type.STORE_POINT_SPACE_CRITICAL_ALERT.name());
		EMAILABLE_TYPES.add(SystemAlert.Type.STORE_POINT_SPACE_WARNING_ALERT.name());
	}

	@Inject
	private ISpaceService space;

	private SystemAlertsMessage message;

	public SystemAlertReceiveCmd(SystemAlertsMessage msg) {
		this.message = msg;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		try {
			// This should only be run on an authority server
			if (!this.env.isMaster()) {
				return null;
			}

			// Multiple destinations will be sending this message to the master every n seconds.
			// It would be better if they did not step on each other.
			synchronized (monitor) {

				try {
					this.publishIncoming();
					this.publishGlobal();
				} catch (SpaceException se) {
					throw new CommandException("Exception performing space operation", se);
				}
			}

			return null;
		} catch (CommandException e) {
			log.error("Exception while handling system alert message", e);
			throw e;
		} catch (RuntimeException e) {
			log.error("Exception while handling system alert message", e);
			throw e;
		}
	}

	private void publishIncoming() throws CommandException, SpaceException {

		/*
		 * This used to publish destination-bound system alerts, but according to Marshall we don't actually reference these
		 * in the UI. As a result all we need to do is publish something here that the map-reduce can find when it runs.
		 * Since we're now keying everything by spaceId the natural choice there is a key based on spaceId.
		 */
		/*
		 * TODO: I'm pretty sure this model of "publish data then do map-reduce on it immediately" could be problematic.
		 * Seems like there's a fair number of race conditions around published data being delivered to the remote nodes
		 * that are responsible for it. May want to re-think the overall design here; it's probably better modeled as a
		 * remote-execution model.
		 */
		long spaceId = this.message.getSpaceId();

		// Using a set to remove duplicates
		ArrayList<SystemAlert> incomingAlerts = new ArrayList(new HashSet(this.message.getAlerts()));

		this.space.put(SpaceKeyUtils.getSystemAlertsKey(spaceId), incomingAlerts);
		log.debug("SysAlert:: Stored {} system alerts for space {}", incomingAlerts.size(), spaceId);
	}

	private void publishGlobal() throws CommandException, SpaceException {
		Pair<Integer, Set<SystemAlert>> allDestinationAlerts;
		try {
			allDestinationAlerts = this.space.mapReduce(CoreSpace.DEFAULT, new AlertMapper(), new AlertReducer());
		} catch (Exception e) {
			throw new CommandException("Error performing map/reduce", e);
		}

		int destinationCount = allDestinationAlerts.getOne();
		Set<SystemAlert> incomingAlertSet = allDestinationAlerts.getTwo();

		// Find the existing global alerts list so we can "compare and save" (yuk, yuk)
		String globalKey = SpaceKeyUtils.getSystemAlertsKey();
		List<SystemAlert> existingAlertsList = (List<SystemAlert>) this.space.get(globalKey);
		Set<SystemAlert> existingAlerts = existingAlertsList == null ? new HashSet() : new HashSet(existingAlertsList);

		// Discover the differences
		Set<SystemAlert> removed = Sets.difference(existingAlerts, incomingAlertSet);
		Set<SystemAlert> added = Sets.difference(incomingAlertSet, existingAlerts);

		if (removed.size() > 0 || added.size() > 0) {
			// We have changes, so update the global space value
			this.space.put(globalKey, new ArrayList(incomingAlertSet));
			log.debug("SysAlert:: Stored {} system alerts from {} destinations", incomingAlertSet.size(), destinationCount);
		}

		this.run(new AlertEventCreateFromSystemAlertsCmd(added), this.auth.getAdminSession());

		if (added.size() > 0) {
			Set<SystemAlert> emailableAlerts = new HashSet<SystemAlert>();
			for (SystemAlert newAlert : added) {
				if (EMAILABLE_TYPES.contains(newAlert.getType())) {
					emailableAlerts.add(newAlert);
					// Add nodeName to each one
					Node serverNode = this.db.find(new NodeFindByGuidQuery(newAlert.getNodeGuid()));
					newAlert.putNonIdentifyingObject("nodeName", serverNode == null ? "???" : serverNode.getNodeName());
					if (serverNode != null) {
						newAlert.putNonIdentifyingObject("nodeName", serverNode.getNodeName());
					} else {
						newAlert.putNonIdentifyingObject("nodeName", "???");
						log.warn("Received system alert from unknown nodeGuid:{}, type:{}", newAlert.getNodeGuid(), newAlert
								.getType());
					}
				}
			}

			if (emailableAlerts.size() > 0) {
				if (SystemProperties.getOptionalBoolean(SystemProperty.SYSTEMALERT_EMAIL_ENABLED, true)) {
					this.sendAlertEmail(emailableAlerts);
				} else {
					log.info("SysAlert:: System alert email property is disabled: {}", SystemProperty.SYSTEMALERT_EMAIL_ENABLED);
				}
			}
		}
	}

	private void sendAlertEmail(Collection<SystemAlert> alerts) throws CommandException {
		if (alerts.isEmpty()) {
			return;
		}

		Collection<String> alertRecipientList = this.run(new SystemAlertRecipientsFindCmd(), this.auth.getAdminSession());

		if (alertRecipientList.size() > 0) {
			String[] recipients = alertRecipientList.toArray(new String[alertRecipientList.size()]);
			IResource resource = this.contentService.getServiceInstance().getResourceByName(
					EmailPaths.EMAIL_SYSTEM_ALERT_ADMIN);
			if (resource == null) {
				// This is an important email so it is important this gets fixed!
				log.error("!!!!!!! The content service could not find this very important email: {}",
						EmailPaths.EMAIL_SYSTEM_ALERT_ADMIN);
				return;
			}
			CpcEmailContext ctx = new CpcEmailContext(CpcConstants.Orgs.ADMIN_ID /* This is a system alert */);
			ctx.put("alerts", alerts);
			try {
				Emailer.enqueueEmail(resource, recipients, ctx);
				CpcHistoryLogger.info(null, "Notified these addresses of system alerts: {}  {}", LangUtils
						.toString(alertRecipientList), LangUtils.toString(alerts));
			} catch (Exception re) {
				CpcHistoryLogger.warn(null, "Error sending email regarding system alerts: {}", LangUtils.toString(alerts));
			}

		} else {
			CpcHistoryLogger.warn(null, "There is no one setup to be notified about these system alerts: {}", LangUtils
					.toString(alerts));
		}
	}

	private static class AlertMapper implements Mapper<Long, List<SystemAlert>> {

		private static final long serialVersionUID = -9154872206496907034L;

		public Map<Long, List<SystemAlert>> map(Map<Object, Object> locals) {
			final Map<Long, List<SystemAlert>> rv = new HashMap<Long, List<SystemAlert>>();
			final String alertsNamespace = DefaultSpaceNamespace.SYSTEM_ALERTS.toString();

			for (Object key : locals.keySet()) {
				if (!(key instanceof String)) {
					continue;
				}

				String strKey = (String) key;
				try {

					if (!SpaceNamingStrategy.getNamespace(strKey).equals(alertsNamespace)) {
						continue;
					}

					Map<String, String> nameValue = SpaceNamingStrategy.getNameValue(strKey);
					if (nameValue == null) {
						continue;
					}

					/* If it doesn't have a space ID we're not interested */
					String spaceIdStr = nameValue.get(NameValueKeys.SPACE_ID.toString());
					if (spaceIdStr == null) {
						continue;
					}

					long spaceId;
					try {
						spaceId = Long.parseLong(spaceIdStr);
					} catch (NumberFormatException e) {
						log.warn("Encountered invalid, non-numeric destinationGuid {} in key {}", spaceIdStr, key);
						continue;
					}

					List<SystemAlert> alerts = (List<SystemAlert>) locals.get(key);
					rv.put(spaceId, alerts);
				} catch (InvalidSpaceKeyException e) {
					log.warn("Unable to parse key with invalid name: {}", key);
				}
			}

			return rv;
		}
	}

	private static class AlertReducer implements Reducer<Long, List<SystemAlert>, Pair<Integer, Set<SystemAlert>>> {

		public Pair<Integer, Set<SystemAlert>> reduce(Map<Long, List<SystemAlert>> alertsByDest) {
			Set<SystemAlert> alertSet = Sets.newTreeSet();
			for (List<SystemAlert> alerts : alertsByDest.values()) {
				alertSet.addAll(alerts);
			}
			return new Pair<Integer, Set<SystemAlert>>(alertsByDest.size(), alertSet);
		}
	}
}
