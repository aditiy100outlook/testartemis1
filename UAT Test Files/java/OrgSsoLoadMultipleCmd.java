package com.code42.org;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.Query;
import org.hibernate.Session;

import com.code42.core.CommandException;
import com.code42.core.annotation.CoreNamedQuery;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBRequestTooLargeException;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;
import com.code42.utils.SystemProperties;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;

/**
 * Load a set of user SSOs directly from the database if their user IDs can be found in the input set. <br>
 * <br>
 * Note that this is an expensive operation! If you have an IBusinessObjectsService instance available you're almost
 * certainly better off using that. In nearly all cases you want {@link OrgSsoFindMultipleByOrgIdCmd} rather than this
 * command.
 * 
 * @author marshall
 */
public class OrgSsoLoadMultipleCmd extends DBCmd<Map<Integer, Option<OrgSso>>> {

	private final Set<Integer> orgIds;

	public OrgSsoLoadMultipleCmd(Set<Integer> orgIds) {

		this.orgIds = orgIds;
	}

	@Override
	public Map<Integer, Option<OrgSso>> exec(CoreSession session) throws CommandException {
		this.db.manual();
		return this.db.find(new OrgSsoPopulateByUserIdQuery());
	}

	@CoreNamedQuery(name = "loadMultipleOrgsByOrgId", query = "select o from Org o where org_id in (:orgIds)")
	private class OrgSsoPopulateByUserIdQuery extends FindQuery<Map<Integer, Option<OrgSso>>> {

		@Override
		public Map<Integer, Option<OrgSso>> query(Session session) throws DBServiceException {

			if (OrgSsoLoadMultipleCmd.this.orgIds.size() > SystemProperties.getMaxQueryInClauseSize()) {
				throw new DBRequestTooLargeException("REQUEST_TOO_LARGE, orgIds: {}", OrgSsoLoadMultipleCmd.this.orgIds.size());
			}

			Map<Integer, Option<OrgSso>> rv = new HashMap<Integer, Option<OrgSso>>();

			Query query = this.getNamedQuery(session);
			query.setParameterList("orgIds", OrgSsoLoadMultipleCmd.this.orgIds);
			for (Org org : (List<Org>) query.list()) {

				rv.put(org.getOrgId(), new Some<OrgSso>(new OrgSso(org)));
			}

			return rv;
		}
	}
}
