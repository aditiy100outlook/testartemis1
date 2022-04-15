package com.code42.balance;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.server.destination.ClusterDestination;
import com.code42.server.destination.DestinationFindByIdQuery;

/**
 * Find the (currently global) balancer settings.
 */
public class BalanceSettingsFindCmd extends DBCmd<BalanceSettingsDto> {

	private final int destinationId;
	private ClusterDestination clusterDestination;

	public BalanceSettingsFindCmd(int destinationId) {
		super();
		this.destinationId = destinationId;
	}

	public BalanceSettingsFindCmd(ClusterDestination clusterDestination) {
		super();
		this.destinationId = clusterDestination.getDestinationId();
		this.clusterDestination = clusterDestination;
	}

	@Override
	public BalanceSettingsDto exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		if (this.clusterDestination == null) {
			this.clusterDestination = (ClusterDestination) this.db.find(new DestinationFindByIdQuery(this.destinationId));
		}

		final BalanceSettingsDto d = new BalanceSettingsDto();

		d.setEnabled(this.clusterDestination.isBalanceData());
		d.setAllowedDiskVariancePerc(this.clusterDestination.getVariance());
		d.setLocalCopyPriority(this.clusterDestination.getLocalPriority());
		d.setRemoteCopyPriority(this.clusterDestination.getRemotePriority());

		return d;
	}
}
