package com.code42.server;

import org.hibernate.Session;

import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.ForceUpdateQuery;

public class ServerSettingsForceUpdateQuery extends ForceUpdateQuery<Void> {

	private ServerSettings serverSettings;

	public ServerSettingsForceUpdateQuery(ServerSettings serverSettings) {
		this.serverSettings = serverSettings;
	}

	@Override
	public Void query(Session session) throws DBServiceException {
		try {
			this.db.beginTransaction();
			session.saveOrUpdate(this.serverSettings);
			this.db.commit();
		} catch (final Exception e) {
			this.db.rollback();
			throw new DBServiceException("Failed to save.", e);
		} finally {
			this.db.endTransaction();
		}

		return null;
	}

}
