package com.code42.archive;

import java.util.Set;

import com.code42.archive.IArchiveAuthHandler.ArchivePermission;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.google.inject.Inject;

public class IsArchiveAuthorizedCmd extends DBCmd<Void> {

	@Inject
	private Set<IArchiveAuthHandler> archiveAuth;

	private final long archiveGuid;
	private final ArchivePermission permission;

	public IsArchiveAuthorizedCmd(final long archiveGuid, final ArchivePermission permission) {
		this.archiveGuid = archiveGuid;
		this.permission = permission;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		ArchiveAuthUtil.isArchiveAuthorized(this.archiveAuth, this.archiveGuid, this.permission, session);
		return null;
	}
}
