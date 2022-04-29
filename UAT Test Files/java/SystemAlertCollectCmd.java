package com.code42.alert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.backup42.app.cpc.clusterpeer.PeerCommunicationException;
import com.code42.core.CommandException;
import com.code42.core.alert.ISystemAlertService;
import com.code42.core.alert.SystemAlert;
import com.code42.core.annotation.CoreRuntimeSingleton;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.core.security.CryptoKeystoreLockedException;
import com.code42.core.security.ICryptoService;
import com.code42.core.server.IServerService;
import com.code42.core.space.ISpaceService;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.logging.ThrottledLogger;
import com.code42.messaging.MessagingTransport;
import com.code42.utils.SystemProperties;
import com.code42.utils.SystemProperty;
import com.code42.utils.Time;
import com.google.inject.Inject;
import com.hazelcast.core.MemberLeftException;

/**
 * Collects system alerts from every node in the destination, then sends them to an authority server (if they have
 * changed since the last collection).
 */
@CoreRuntimeSingleton
public class SystemAlertCollectCmd extends AbstractCmd<Void> {

	private static final Logger log = LoggerFactory.getLogger(SystemAlertCollectCmd.class);

	@Inject
	private ISpaceService space;

	@Inject
	private IServerService serverService;

	@Inject
	private ICryptoService crypto;

	@Inject
	private ISystemAlertService systemAlertService;

	@Inject
	private MessagingTransport messaging;

	private final ThrottledLogger throttledLog = new ThrottledLogger(log, 1 * Time.HOUR);

	/** Note the static list here */
	private static List<SystemAlert> previousAlerts;

	@Override
	public Void exec(CoreSession session) throws CommandException {
		if (!this.isEnabled()) {
			return null;
		}

		if (this.crypto.isLocked()) {
			log.warn("Unable to send system alerts to authority, server locked.", new CryptoKeystoreLockedException());
			return null;
		}

		ArrayList<SystemAlert> destinationAlerts = new ArrayList<SystemAlert>();
		try {

			// This ensures that only one member in a destination
			// ever runs this.
			if (!this.space.isOldestMember()) {
				return null;
			}

			Collection<List<SystemAlert>> allAlerts = this.space.runEverywhere(new SystemAlertSpaceCallable()).get(45,
					TimeUnit.SECONDS);
			for (List<SystemAlert> alerts : allAlerts) {
				destinationAlerts.addAll(alerts);
			}

		} catch (MemberLeftException e) {
			log.warn("Unable to collect system alerts. Hazelcast member left: {}", e.toString());

		} catch (Exception e) {
			throw new CommandException(e);
		}

		if (!this.systemAlertService.matches(destinationAlerts, previousAlerts)) {

			// Send server alerts to an authority/master server
			long spaceId = this.serverService.getMyNode().getSpaceId();
			SystemAlertsMessage msg = new SystemAlertsMessage(spaceId, destinationAlerts);
			try {
				this.messaging.sendToAuthoritySpace(msg);
			} catch (PeerCommunicationException e) {
				this.throttledLog.warn("Unable to send system alerts to authority server: " + e.getMessage());
			}
			previousAlerts = destinationAlerts;

			log.info("SysAlert:: Sending {} alerts to the master for spaceId: {}", destinationAlerts.size(), spaceId);
		} else {
			log.info("SysAlert:: Nothing new to send");
		}

		return null;
	}

	private boolean isEnabled() {
		return SystemProperties.getOptionalBoolean(SystemProperty.REALTIME_ALL_ENABLED, true)
				&& SystemProperties.getOptionalBoolean("c42.realtime.systemalertcollect.enabled", true);
	}
}
