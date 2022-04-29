package com.code42.org;

import java.util.Collection;

import org.hibernate.Session;

import com.code42.core.CommandException;
import com.code42.core.annotation.CoreNamedQuery;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;

/**
 * Simple utility command to identify all orgs that have a non-null master GUID field
 * 
 * @author bmcguire
 */
public class OrgFindAllWithMasterGuidCmd extends DBCmd<Collection<BackupOrg>> {

	@Override
	public Collection<BackupOrg> exec(CoreSession session) throws CommandException {

		return this.db.find(new OrgFindAllWithMasterGuidQuery());
	}

	@CoreNamedQuery(name = "OrgFindAllWithMasterGuidQuery", query = "from BackupOrg o where o.masterGuid is not null")
	private class OrgFindAllWithMasterGuidQuery extends FindQuery<Collection<BackupOrg>> {

		@Override
		public Collection<BackupOrg> query(Session session) throws DBServiceException {

			return this.getNamedQuery(session).list();
		}
	}
}
