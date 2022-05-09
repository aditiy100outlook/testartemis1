package com.code42.computer;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.hibernate.Persistence;

/**
 * Delete all placeholder records that were created for the worker. Maintain the parent records for history's sake.
 */
public class PublishedConfigDeleteFinishedRecordsCmd extends DBCmd<Void> {

	@Override
	public Void exec(CoreSession session) throws CommandException {
		try {
			this.db.beginTransaction();
			Persistence.manual();
			this.db.delete(new PublishedConfigDeleteFinishedRecordsQuery());
			this.db.commit();
		} finally {
			this.db.endTransaction();
		}
		return null;
	}

}
