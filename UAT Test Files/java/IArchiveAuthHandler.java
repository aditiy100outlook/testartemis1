package com.code42.archive;

import com.code42.core.auth.impl.CoreSession;

public interface IArchiveAuthHandler {

	enum ArchiveAuthorization {
		ALLOWED, DENIED, UNKNOWN
	}

	enum ArchivePermission {
		READ, WRITE
	}

	ArchiveAuthorization getAuthorization(long archiveGuid, CoreSession session, ArchivePermission permission);

}
