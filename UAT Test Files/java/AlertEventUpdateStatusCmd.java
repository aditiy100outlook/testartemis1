package com.code42.alert;

import org.hibernate.Query;
import org.hibernate.Session;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.alert.AlertEvent.Status;
import com.code42.core.CommandException;
import com.code42.core.alert.IAlertEventService;
import com.code42.core.annotation.CoreNamedQuery;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.db.impl.UpdateQuery;
import com.code42.core.impl.DBCmd;
import com.code42.hibernate.aftertx.AfterTxRunnable;
import com.code42.hibernate.aftertx.IAfterTxRunnable.Priority;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;

/**
 * Update the alert event identified by the provided identifier to the given status
 */
public class AlertEventUpdateStatusCmd extends DBCmd<Void> {

	private static final Logger log = LoggerFactory.getLogger(AlertEventUpdateStatusCmd.class);

	private final Long alertEventId;
	private final Status status;

	@Inject
	private IAlertEventService alertEventService;

	/**
	 * @param alertEventId identifier for alert event to update
	 * @param status new status of the alert event
	 * 
	 * @throws IllegalArgumentException if the provided status string cannot be converted to a valid alert event status
	 */
	public AlertEventUpdateStatusCmd(Long alertEventId, String status) {
		this.alertEventId = alertEventId;
		this.status = AlertEvent.Status.valueOf(status);
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		// Admin-only
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		final AlertEvent event = this.db.find(new AlertEventFindByIdQuery(this.alertEventId));
		if (event == null) {
			return null;
		}
		event.setStatus(this.status);

		this.db.beginTransaction();
		try {
			this.db.update(new AlertEventUpdateQuery(event));

			// After we've changed the status of an alert, notify the AlertEventService to recalculate the total counts and
			// push them to the client
			this.db.afterTransaction(new AfterTxRunnable(Priority.NORMAL) {

				public void run() {
					try {
						AlertEventUpdateStatusCmd.this.alertEventService.handleAlertEventCountsUpdate();
					} catch (CommandException e) {
						AlertEventUpdateStatusCmd.log.warn("Unable to update alert event counts: ", e);
					}
				}
			});

			this.db.commit();

			return null;
		} finally {
			this.db.endTransaction();
		}
	}

	@VisibleForTesting
	@CoreNamedQuery(name = "AlertEventFindById", query = "from AlertEvent where alertEventId = :eventId")
	static class AlertEventFindByIdQuery extends FindQuery<AlertEvent> {

		private final Long eventId;

		private AlertEventFindByIdQuery(Long eventId) {
			this.eventId = eventId;
		}

		@Override
		public AlertEvent query(Session session) throws DBServiceException {
			Query query = this.getNamedQuery(session);
			query.setLong("eventId", this.eventId);

			return (AlertEvent) query.uniqueResult();
		}

	}

	@VisibleForTesting
	static class AlertEventUpdateQuery extends UpdateQuery<AlertEvent> {

		private final AlertEvent event;

		public AlertEventUpdateQuery(AlertEvent event) {
			this.event = event;
		}

		@Override
		public AlertEvent query(Session session) throws DBServiceException {
			session.update(this.event);

			return this.event;
		}

	}
}
