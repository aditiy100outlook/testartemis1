package com.code42.balance;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.balance.admin.BalanceCommandCancelCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

/**
 * Cancel any active data balance command.
 */
public class BalanceCancelCmd extends DBCmd<Void> {

	private final Set<Integer> dataBalanceCommandIds = new HashSet();

	public BalanceCancelCmd(int dataBalanceCommandId) {
		super();
		this.dataBalanceCommandIds.add(dataBalanceCommandId);
	}

	public BalanceCancelCmd(List<BalanceCommandDto> dtos) {
		for (BalanceCommandDto dto : dtos) {
			this.dataBalanceCommandIds.add(dto.getDataBalanceCommandId());
		}
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		try {
			this.db.beginTransaction();

			for (Integer dbCmdId : this.dataBalanceCommandIds) {
				final DataBalanceCommand cmd = this.db.find(new BalanceCommandFindByIdQuery(dbCmdId));
				if (cmd.isActive()) {
					this.run(new BalanceCommandCancelCmd(cmd), session);
				}
			}

			this.db.commit();
		} finally {
			this.db.endTransaction();
		}

		return null;
	}
}
