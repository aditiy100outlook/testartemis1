package com.code42.computer;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.hibernate.type.StandardBasicTypes;

import com.code42.core.CommandException;
import com.code42.core.RequestTooLargeException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.geo.GeoLocation;
import com.code42.core.geo.IGeoService;
import com.code42.core.impl.DBCmd;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;

/**
 * Returns geographical location information for a set of computers
 */
public class GeoLocationFindByComputerIdsCmd extends DBCmd<Map<Long, GeoLocation>> {

	private static final int MAX_REQUEST_SIZE = 50;

	@Inject
	private IGeoService geo;

	private final Set<Long> computerIds;

	public GeoLocationFindByComputerIdsCmd(Collection<Long> computerIds) {
		this(new HashSet<Long>(computerIds));
	}

	public GeoLocationFindByComputerIdsCmd(Set<Long> computerIds) {
		this.computerIds = computerIds;
	}

	@Override
	public Map<Long, GeoLocation> exec(CoreSession session) throws CommandException {
		if (this.computerIds.size() > MAX_REQUEST_SIZE) {
			throw new RequestTooLargeException("Cannot request geolocation for more than " + MAX_REQUEST_SIZE
					+ " computers at a time");
		}

		if (this.computerIds.size() < 1) {
			return new HashMap<Long, GeoLocation>(0);
		}

		for (long computerId : this.computerIds) {
			this.run(new IsComputerManageableCmd(computerId, C42PermissionApp.Computer.READ), session);
		}

		Map<Long, String> ipAddresses = this.cleanseIpAddresses(this.db.find(new RemoteAddressFindByComputerIdsQuery()));

		Set<String> addressSet = new HashSet<String>(Collections2.filter(ipAddresses.values(), Predicates.notNull()));
		Map<String, GeoLocation> results = this.geo.findLocations(addressSet);

		Map<Long, GeoLocation> rv = new HashMap<Long, GeoLocation>();
		for (long computerId : this.computerIds) {
			String ipAddress = ipAddresses.get(computerId);
			GeoLocation location = results.get(ipAddress);
			rv.put(computerId, location);
		}

		return rv;
	}

	private Map<Long, String> cleanseIpAddresses(Map<Long, String> dirtyAddresses) {
		final Map<Long, String> cleansedAddresses = new HashMap<Long, String>();

		for (final Map.Entry<Long, String> entry : dirtyAddresses.entrySet()) {
			final String remoteAddress = entry.getValue();
			if (remoteAddress != null) {
				String[] parts = remoteAddress.split(":");
				String ipAddress = parts[0];
				cleansedAddresses.put(entry.getKey(), ipAddress);
			}
		}

		return cleansedAddresses;
	}

	private class RemoteAddressFindByComputerIdsQuery extends FindQuery<Map<Long, String>> {

		private static final String SQL = "select computer_id, remote_address from t_computer where computer_id in (:computerIds)";

		@Override
		public Map<Long, String> query(Session session) throws DBServiceException {

			SQLQuery query = session.createSQLQuery(SQL);
			query.setParameterList("computerIds", GeoLocationFindByComputerIdsCmd.this.computerIds);
			query.addScalar("computer_id", StandardBasicTypes.LONG);
			query.addScalar("remote_address", StandardBasicTypes.STRING);

			Map<Long, String> rv = new HashMap<Long, String>();
			List<Object[]> rows = query.list();
			for (Object[] row : rows) {
				rv.put((Long) row[0], (String) row[1]);
			}
			return rv;
		}

	}
}