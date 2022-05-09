package com.code42.user;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;

public interface UserEventCallback {

	public void userCreate(User user, CoreSession session) throws CommandException;

	public void userDeactivate(User user, CoreSession session) throws CommandException;

}
