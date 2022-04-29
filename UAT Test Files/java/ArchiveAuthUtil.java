package com.code42.archive;

import java.util.Set;

import com.code42.archive.IArchiveAuthHandler.ArchiveAuthorization;
import com.code42.archive.IArchiveAuthHandler.ArchivePermission;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;

public class ArchiveAuthUtil {

	private ArchiveAuthUtil() {
		// static only
	}

	public static void isArchiveAuthorized(Set<IArchiveAuthHandler> archiveAuth, long archiveGuid,
			ArchivePermission permission, CoreSession session) throws UnauthorizedException {
		for (final IArchiveAuthHandler handler : archiveAuth) {
			ArchiveAuthorization code = handler.getAuthorization(archiveGuid, session, permission);
			switch (code) {
			case ALLOWED:
				return;
			case DENIED:
				throw new UnauthorizedException("Not authorized");
			}
		}
		throw new UnauthorizedException(String.format("No one knew if %d was authorized for %s", archiveGuid, permission));
	}
}
