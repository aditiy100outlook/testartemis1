package com.code42.computer;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;

public interface ComputerEventCallback {

	public void computerActivate(Computer computer, CoreSession session) throws CommandException;

	public void computerDeactivate(Computer computer, CoreSession session) throws CommandException;

	public void computerCreate(Computer computer, CoreSession session) throws CommandException;

}
