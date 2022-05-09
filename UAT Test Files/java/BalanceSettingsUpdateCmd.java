package com.code42.balance;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.balance.engine.DataBalancer;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.core.server.IServerService;
import com.code42.hibernate.aftertx.AfterTxRunnable;
import com.code42.hibernate.aftertx.IAfterTxRunnable.Priority;
import com.code42.server.destination.ClusterDestination;
import com.code42.server.destination.DestinationFindByIdQuery;
import com.code42.server.destination.DestinationUpdateCmd;
import com.code42.utils.LangUtils;
import com.google.inject.Inject;

/**
 * Update system settings relating to balancing.
 */
public class BalanceSettingsUpdateCmd extends DBCmd<Void> {

	private final int destinationId;
	private final BalanceSettingsDto settings;

	@Inject
	IServerService serverService;

	public BalanceSettingsUpdateCmd(int destinationId, BalanceSettingsDto settings) {
		super();
		this.destinationId = destinationId;
		this.settings = settings;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		try {
			this.db.beginTransaction();

			final ClusterDestination dest = (ClusterDestination) this.db.find(new DestinationFindByIdQuery(this.destinationId));

			// ENABLED
			final boolean enabled = this.settings.isEnabled();
			// changing to disabled?
			final boolean disableBalancing = dest.isBalanceData() && !enabled;
			dest.setBalanceData(enabled);

			// ALLOWED VARIANCE: ui is restricted to 5-50; you can override this using prop.set if needed
			final int allowedVariance = LangUtils.boundValue(this.settings.getAllowedDiskVariancePerc(), 5, 50);
			dest.setVariance(allowedVariance);

			// LOCAL COPY PRIORITY
			final int localPriority = LangUtils.boundValue(this.settings.getLocalCopyPriority(), 1, 100);
			dest.setLocalPriority(localPriority);

			// REMOTE COPY PRIORITY
			final int remotePriority = LangUtils.boundValue(this.settings.getRemoteCopyPriority(), 1, 100);
			dest.setRemotePriority(remotePriority);

			// UPDATE
			this.run(new DestinationUpdateCmd(dest), session);

			// reset server caches
			final IServerService fServerService = this.serverService;
			this.db.afterTransaction(new AfterTxRunnable(Priority.HIGH) {

				public void run() {
					fServerService.clearCaches();
					if (disableBalancing) { // balancing is being disabled
						DataBalancer.getInstance().disableBalancing();
					}
				}
			});

			this.db.commit();
		} finally {
			this.db.endTransaction();
		}

		return null;
	}
}
