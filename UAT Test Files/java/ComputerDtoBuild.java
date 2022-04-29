package com.code42.computer;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.org.OrgSso;
import com.code42.org.OrgSsoFindByUserIdCmd;
import com.code42.user.UserSso;
import com.code42.user.UserSsoFindByComputerIdCmd;

public class ComputerDtoBuild extends AbstractCmd<ComputerDto> {

	private Computer computer;

	public ComputerDtoBuild(Computer c) {
		this.computer = c;
	}

	@Override
	public ComputerDto exec(CoreSession session) throws CommandException {

		if (this.computer == null) {
			throw new CommandException("Cannot operate on null computer");
		}

		UserSso user = this.run(new UserSsoFindByComputerIdCmd(this.computer.getComputerId()), session);
		OrgSso org = this.run(new OrgSsoFindByUserIdCmd(user.getUserId()), session);

		return new ComputerDto(this.computer, user, org);
	}
}
