package com.code42.recent;

import com.code42.computer.Computer;
import com.code42.computer.ComputerDto;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;

public class RecentListAddComputerCmd extends AbstractCmd<Void> {

	private final long computerId;
	private final long guid;
	private final String name;

	public RecentListAddComputerCmd(Computer computer) {

		this.computerId = computer.getComputerId();
		this.guid = computer.getGuid();
		this.name = computer.getName();
	}

	public RecentListAddComputerCmd(ComputerDto computer) {

		this.computerId = computer.getComputerId();
		this.guid = computer.getGuid();
		this.name = computer.getName();
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		RecentComputer rc = new RecentComputer(this.computerId, this.guid, this.name);

		this.runtime.run(new RecentListAddItemCmd(rc), session);

		return null;
	}

}
