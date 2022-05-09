package com.code42.computer;

import com.backup42.common.Computer;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;

/**
 * Builds a common Computer definition from a db Computer entries
 * 
 */
public class CommonComputerFindByGuidCmd extends AbstractCmd<Computer> {

	private final long guid;

	public CommonComputerFindByGuidCmd(long guid) {
		this.guid = guid;
	}

	@Override
	public Computer exec(CoreSession session) throws CommandException {
		com.code42.computer.Computer src = this.run(new ComputerFindByGuidCmd(this.guid), session);
		return build(src);
	}

	private static Computer build(com.code42.computer.Computer src) {
		Computer c = new Computer(src.getGuid());
		c.setName(src.getName());

		if (src.getAddress() != null) {
			c.setAddress(src.getAddress());
		}

		return c;
	}
}
