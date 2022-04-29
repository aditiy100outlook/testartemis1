package com.code42.computer;

import com.backup42.common.CPErrors;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.org.OrgSso;
import com.code42.org.OrgSsoFindByUserIdCmd;
import com.code42.user.UserSso;
import com.code42.user.UserSsoFindByComputerIdCmd;

/**
 * Command to find a computer object by its GUID
 */
public class ComputerDtoFindByIdCmd extends DBCmd<ComputerDto> {

	private long computerId;

	public ComputerDtoFindByIdCmd(long computerId) {
		this.computerId = computerId;
	}

	@Override
	public ComputerDto exec(CoreSession session) throws CommandException {

		Computer c = this.db.find(new ComputerFindByIdQuery(this.computerId));

		if (c == null) {
			return null;
		}

		// Authorize: Make sure the subject is allowed to view/read this computer
		this.runtime.run(new IsComputerManageableCmd(this.computerId, C42PermissionApp.Computer.READ), session);

		UserSso user = this.run(new UserSsoFindByComputerIdCmd(this.computerId), session);
		OrgSso org = this.run(new OrgSsoFindByUserIdCmd(user.getUserId()), session);

		// Include hosted computers?
		if (!session.isSystem() && org.isHosted()) {
			throw new CommandException(CPErrors.Cluster.HOSTED_UNAVAILABLE, "Hosted computers unavailable");
		}

		ComputerDto dto = new ComputerDto(c, user, org);
		return dto;
	}

}