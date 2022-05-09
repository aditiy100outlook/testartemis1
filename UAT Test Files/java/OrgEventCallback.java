package com.code42.org;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;

public interface OrgEventCallback {

	public void orgDeactivate(Org org, CoreSession session) throws CommandException;

	public void orgMigrateToPRO(Org org, CoreSession session) throws CommandException;

	public void orgCreatePRO(Org org, CoreSession session) throws CommandException;

}
