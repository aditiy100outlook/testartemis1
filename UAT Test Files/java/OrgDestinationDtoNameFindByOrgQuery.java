package com.code42.org.destination;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Session;

import com.code42.computer.Computer;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.server.destination.Destination;
import com.code42.sql.SQLQuery;

/**
 * Build the basics of a destination with a single query.
 * 
 * Protected to prevent general use.
 * 
 * @see OrgDestinationFindAvailableByOrgCmd
 */
class OrgDestinationDtoNameFindByOrgQuery extends FindQuery<List<OrgDestinationDtoName>> {

	private static final String SQL = ""
			+ " select {od.*}, {d.*}, {c.*}                                \n"
			+ " from t_org_destination od                                  \n"
			+ " join t_destination d on (d.server_id = od.destination_id)  \n"
			+ " join t_computer c on (c.computer_id = d.computer_id)       \n"
			+ " where od.org_destination_id in :orgDestinationIds          \n";

	private final List<Integer> orgDestinationIds;

	OrgDestinationDtoNameFindByOrgQuery(List<OrgDestination> orgDestinations) {
		super();

		this.orgDestinationIds = new ArrayList(orgDestinations.size());
		for (OrgDestination orgDestination : orgDestinations) {
			this.orgDestinationIds.add(orgDestination.getOrgDestinationId());
		}
	}

	@Override
	public List<OrgDestinationDtoName> query(Session session) throws DBServiceException {

		final SQLQuery q = new SQLQuery(session, SQL);
		q.addEntity("od", OrgDestination.class);
		q.addEntity("d", Destination.class);
		q.addEntity("c", Computer.class);

		q.setParameterList("orgDestinationIds", this.orgDestinationIds);

		final List<Object[]> rows = q.list();
		final List<OrgDestinationDtoName> dtos = new ArrayList<OrgDestinationDtoName>(rows.size());
		for (Object[] row : rows) {
			OrgDestination od = (OrgDestination) row[0];
			Destination d = (Destination) row[1];
			Computer c = (Computer) row[2];

			final OrgDestinationDtoName dto = new OrgDestinationDtoName(od, d, c);
			dtos.add(dto);
		}

		return dtos;
	}
}
