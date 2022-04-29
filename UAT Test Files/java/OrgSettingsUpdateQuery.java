package com.code42.org;

import org.hibernate.Session;

import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.UpdateQuery;

public class OrgSettingsUpdateQuery extends UpdateQuery<OrgSettings> {

	private final OrgSettings orgSettings;

	public OrgSettingsUpdateQuery(OrgSettings orgSettings) {
		this.orgSettings = orgSettings;
	}

	@Override
	public OrgSettings query(Session session) throws DBServiceException {
		if (this.orgSettings != null) {
			session.update(this.orgSettings);
			return this.orgSettings;
		} else {
			throw new DBServiceException("Error updating OrgSettings; null argument: ");
		}
	}
}