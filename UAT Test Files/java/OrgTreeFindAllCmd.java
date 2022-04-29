package com.code42.org;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.hibernate.Session;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;
import com.code42.sql.SQLQuery;
import com.code42.sql.SQLUtils;

/**
 * Supplies a Collection of top-level OrgTreeDto instances that you can walk down for their descendants.
 */
public class OrgTreeFindAllCmd extends DBCmd<OrgTree<OrgTreeDto>> {

	private static String SQL = "" + "SELECT o.parent_org_id, o.org_id, o.org_name, o.active   \n"
			+ "FROM t_org AS o                                          \n"
			+ "WHERE true    -- makes it easier to add more filters     \n"
			+ "--excludeHosted    AND master_guid IS NULL               \n";

	private final boolean excludeHosted;

	public OrgTreeFindAllCmd() {
		super();
		this.excludeHosted = false;
	}

	public OrgTreeFindAllCmd(boolean excludeHosted) {
		super();
		this.excludeHosted = excludeHosted;
	}

	@Override
	public OrgTree exec(CoreSession session) throws CommandException {

		// Authorize this call
		this.auth.isAuthorized(session, C42PermissionApp.AllOrg.READ);

		return this.db.find(new OrgTreeFindAllQuery());
	}

	public class OrgTreeFindAllQuery extends FindQuery<OrgTree> {

		@Override
		public OrgTree<OrgTreeDto> query(Session session) throws DBServiceException {
			SQLQuery query = new SQLQuery(session, SQL);

			if (OrgTreeFindAllCmd.this.excludeHosted) {
				query.activate("--excludeHosted");
			}

			List<Object[]> rows = query.list();
			Collection<OrgTreeDto> dtos = new ArrayList<OrgTreeDto>();

			for (Object[] row : rows) {
				OrgTreeDto dto = new OrgTreeDto<OrgTreeDto>();
				int i = 0;
				dto.parentOrgId = SQLUtils.getInteger(row[i++]);
				dto.orgId = SQLUtils.getint(row[i++]);
				dto.orgName = SQLUtils.getString(row[i++]);
				dto.active = SQLUtils.getBoolean(row[i++]);
				dtos.add(dto);
			}

			// Relate the orgs to each other
			return new OrgTree(dtos);
		}

	}

}
