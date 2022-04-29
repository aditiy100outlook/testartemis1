package com.code42.alert;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Session;

import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;
import com.code42.exception.DebugRuntimeException;
import com.code42.sql.SQLQuery;

/**
 * Retrieve a list of {@link AlertEventDto AlertEventDtos}, filtered and/or sorted by the included criteria. Use
 * {@link AlertEventDtoFindByCriteriaCmd.Builder} to build an appropriately constructed instance.
 */
public class AlertEventDtoFindByCriteriaCmd extends DBCmd<List<AlertEventDto>> {

	private final StatusFilter statusFilter;
	private final SortKey sortKey;
	private boolean sortAsc = true;
	private final Integer limit;
	private final Integer offset;

	private AlertEventDtoFindByCriteriaCmd(AlertEventDtoFindByCriteriaCmd.Builder builder) {
		this.statusFilter = builder.statusFilter;
		this.sortKey = builder.srtKey;
		this.sortAsc = builder.srtAscending;
		this.limit = builder.limit;
		this.offset = builder.offset;
	}

	@Override
	public List<AlertEventDto> exec(CoreSession session) throws CommandException {
		final AlertEventFindByCriteriaQuery query = new AlertEventFindByCriteriaQuery(this.statusFilter, this.sortKey,
				this.sortAsc, this.limit, this.offset);
		return this.db.find(query);
	}

	/**
	 * Allows for sorting and determining whether or not archived alert events should be included in the results. Default
	 * is no sorting and do not include archived events from the log.
	 */
	public static class Builder {

		private SortKey srtKey = SortKey.none;
		private boolean srtAscending;
		private StatusFilter statusFilter = StatusFilter.NONE;
		private Integer limit;
		private Integer offset;

		public AlertEventDtoFindByCriteriaCmd build() throws BuilderException {
			this.validate();
			return new AlertEventDtoFindByCriteriaCmd(this);
		}

		public Builder limit(final int n) {
			this.limit = n;
			return this;
		}

		public Builder offset(final int n) {
			this.offset = n;
			return this;
		}

		/**
		 * Sort by the specified sort key, ascending
		 */
		public Builder sort(final String srtKey) {
			return this.sort(srtKey, "ASC");
		}

		/**
		 * Sort by the specified sort key in the given direction (anything other than case insensitive DESC sorts ascending)
		 */
		public Builder sort(final String srtKey, final String srtDir) {
			this.srtKey = SortKey.valueOf(srtKey.toLowerCase());
			this.srtAscending = !srtDir.equalsIgnoreCase("DESC");

			return this;
		}

		public Builder filterStatus(final String statusValue) {
			this.statusFilter = StatusFilter.valueOf(statusValue.toUpperCase());
			return this;
		}

		public void validate() throws BuilderException {
			if (this.limit == null && this.offset != null) {
				throw new BuilderException("Limit is required when offset used.");
			}
		}
	}

	private enum StatusFilter {
		NEW, ARCHIVED, NONE;

		private StatusFilter() {
		}
	}

	private enum SortKey {
		server("ae.server_name"), //
		type("ae.type"), //
		status("ae.status"), //
		severity("ae.severity"), //
		timestamp("ae.alert_event_id"), //
		none("");

		private String property;

		private SortKey(String property) {
			this.property = property;
		}
	}

	private static class AlertEventFindByCriteriaQuery extends FindQuery<List<AlertEventDto>> {

		private final StatusFilter statusFilter;

		private final SortKey sortKey;
		private final boolean sortAscending;
		private final Integer limit;
		private final Integer offset;

		public AlertEventFindByCriteriaQuery(final StatusFilter statusFilter, final SortKey sortKey,
				final boolean sortAscending, final Integer limit, final Integer offset) {
			this.statusFilter = statusFilter;
			this.sortKey = sortKey;
			this.sortAscending = sortAscending;
			this.limit = limit;
			this.offset = offset;
		}

		private final static String SQL = "" //
				+ "                                                                                                                                                                "
				+ "                             select distinct {ae.*} \n                                                                                                          "
				+ "                             from t_alert_event ae \n                                                                                                           "
				+ "                             where true \n                                                                                                                      "
				+ "--statusNew                  and ae.status = 'NEW' \n                                                                                                           "
				+ "--statusArchived             and ae.status = 'ARCHIVED' \n                                                                                                      "
				+ "--sort                       order by {orderBy} \n                                                                                                              "
				+ "--orderDesc                  desc \n                                                                                                                            "
				+ "--orderAsc                   asc \n                                                                                                                             "
				+ "--limit                      limit :limit \n                                                                                                                    "
				+ "--offset                     offset :offset \n                                                                                                                  ";

		@Override
		public List<AlertEventDto> query(Session session) {

			final SQLQuery q = new SQLQuery(session, SQL);
			q.addEntity("ae", AlertEvent.class);

			if (this.sortKey == SortKey.none) {
				// not sorting by anything in particular
			} else {
				q.activate("--sort");
				q.orderBy(this.sortKey.property);
				if (this.sortAscending) {
					q.activate("--orderAsc");
				} else {
					q.activate("--orderDesc");
				}
			}

			switch (this.statusFilter) {
			case NEW:
				q.activate("--statusNew");
				break;
			case ARCHIVED:
				q.activate("--statusArchived");
				break;
			case NONE:
				break;
			default:
				throw new DebugRuntimeException("Unfamiliar statusFilter: {}", this.statusFilter);
			}

			if (this.limit != null) {
				q.activate("--limit");
				q.setInteger("limit", this.limit);
			}

			if (this.offset != null) {
				q.activate("--offset");
				q.setInteger("offset", this.offset);
			}

			String qs = q.getQueryString();

			List<AlertEvent> alerts = q.list();
			List<AlertEventDto> alertDtos = new ArrayList<AlertEventDto>();
			for (AlertEvent alert : alerts) {
				alertDtos.add(new AlertEventDto(alert));
			}

			return alertDtos;
		}
	}

}
