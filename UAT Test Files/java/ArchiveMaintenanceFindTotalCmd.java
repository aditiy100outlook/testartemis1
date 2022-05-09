package com.code42.archive.maintenance;

import java.util.List;

import org.hibernate.Session;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import com.backup42.app.cpc.backup.CPCArchiveMaintenanceManager;
import com.backup42.common.perm.C42PermissionPro;
import com.code42.archive.maintenance.ArchiveMaintenanceFindTotalCmd.ArchiveMaintenanceTotalDto;
import com.code42.backup.manifest.maintenance.MaintQueueJob;
import com.code42.core.CommandException;
import com.code42.core.IEnvironment;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;
import com.code42.social.FriendComputerUsage;
import com.code42.sql.SQLQuery;
import com.code42.sql.SQLUtils;
import com.code42.stats.AggregateType;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

/**
 * Find summary totals and current configuration for archive maintenance jobs. Returns a mix of in-memory information
 * (my server only) and db information from t_friend_computer_usage.
 * 
 * @author mharper
 */
public class ArchiveMaintenanceFindTotalCmd extends DBCmd<ArchiveMaintenanceTotalDto> {

	// private final static Logger log = LoggerFactory.getLogger(ArchiveMaintenanceFindCmd.class);

	@Inject
	private IEnvironment environment;

	private Integer serverId = null;
	private Integer mountPointId = null;

	/**
	 * 
	 * @param serverId The serverId who is asking for data. If this is not my server, then we'll return null.
	 */
	public ArchiveMaintenanceFindTotalCmd(AggregateType aggregateType, int id) {
		super();
		switch (aggregateType) {
		case SERVER:
			this.serverId = id;
			break;
		case MOUNT_POINT:
			this.mountPointId = id;
			break;
		default:
			throw new IllegalArgumentException("Unexpected AggregateType: " + aggregateType);
		}
	}

	// this constructor may be invoked if serverId=="system"
	public ArchiveMaintenanceFindTotalCmd(AggregateType aggregateType, String alias) {
		super();
		switch (aggregateType) {
		case SERVER:
			if (alias.equals("system")) {
				// this is an expected value, but will not return in-memory stats
				break;
			}
			//$FALL-THROUGH$
		default:
			throw new IllegalArgumentException("Unexpected alias for " + aggregateType + ": " + alias);
		}
	}

	@Override
	public ArchiveMaintenanceTotalDto exec(CoreSession session) throws CommandException {
		this.auth.isAuthorizedAny(session, C42PermissionPro.System.SYSTEM_SETTINGS,
				C42PermissionPro.System.ARCHIVE_MAINTENANCE);

		FriendComputerUsage fcu = this.db.find(new FriendComputerUsageFindMostRecentlyMaintainedQuery());
		DateTime lastMaintained = (fcu == null) ? null : new DateTime(fcu.getLastMaintenanceDate());

		if (this.serverId == null || this.serverId != this.environment.getMyNodeId()) {
			// not my server, return db info but not in-memory info
			return new ArchiveMaintenanceTotalDto(lastMaintained);
		}

		// my server - fetch in-memory info
		CPCArchiveMaintenanceManager manager = ArchiveMaintenanceFindCmd.getManager();

		List<MaintQueueJob> allJobs = Lists.newArrayList();
		allJobs.addAll(manager.getSystemQueue().getCompletedJobs());
		allJobs.addAll(manager.getUserQueue().getCompletedJobs());

		int numJobs = allJobs.size();
		boolean isRunning = manager.getSystemQueue().isStarted() && manager.getUserQueue().isStarted();
		int maintenanceRateSystem = manager.getSystemQueue().getRate();
		int maintenanceRateUser = manager.getUserQueue().getRate();

		return new ArchiveMaintenanceTotalDto(lastMaintained, numJobs, isRunning, maintenanceRateSystem,
				maintenanceRateUser);
	}

	/**
	 * Return the most recent FCU, by server or mount point. Finding by server requires a join.
	 * 
	 * @author mharper
	 */
	private class FriendComputerUsageFindMostRecentlyMaintainedQuery extends FindQuery<FriendComputerUsage> {

		private final static String SQL = "" //
				+ "                   select {fcu.*}                                                              \n" //
				+ "                   from t_friend_computer_usage as fcu                                         \n" //
				+ "--useServerId      inner join t_mount_point as mp on (fcu.mount_point_id = mp.mount_point_id)  \n" //
				+ "                   where true                                                                  \n" //
				+ "--useMountPointId  and fcu.mount_point_id = :mountPointId                                      \n" //
				+ "--useServerId      and mp.server_id = :serverId                                                \n" //
				+ "                   order by fcu.last_maintenance_date desc                                     \n" //
		;

		private FriendComputerUsageFindMostRecentlyMaintainedQuery() {
			super();
		}

		@Override
		public FriendComputerUsage query(Session session) throws DBServiceException {
			SQLQuery query = SQLUtils.createSQLQuery(session, SQL);
			query.addEntity("fcu", FriendComputerUsage.class);
			query.setMaxResults(1);

			if (ArchiveMaintenanceFindTotalCmd.this.serverId != null) {
				query.activate("--useServerId");
				query.setInteger("serverId", ArchiveMaintenanceFindTotalCmd.this.serverId);
			}
			if (ArchiveMaintenanceFindTotalCmd.this.mountPointId != null) {
				query.activate("--useMountPointId");
				query.setInteger("mountPointId", ArchiveMaintenanceFindTotalCmd.this.mountPointId);
			}

			Object result = query.uniqueResult();
			return (result == null) ? null : (FriendComputerUsage) result;
		}

	}

	/**
	 * Summary stats
	 */
	public static class ArchiveMaintenanceTotalDto {

		// db info
		private final String lastCompletedOn;

		// in-memory info, my server only
		private final Integer numJobs;
		private final boolean isRunning;
		private final Integer maintenanceRateSystem;
		private final Integer maintenanceRateUser;

		ArchiveMaintenanceTotalDto(DateTime lastCompletedOn) {
			this(lastCompletedOn, null, false, null, null);
		}

		ArchiveMaintenanceTotalDto(DateTime lastCompletedOn, Integer numJobs, boolean isRunning,
				Integer maintenanceRateSystem, Integer maintenanceRateUser) {
			this.lastCompletedOn = (lastCompletedOn == null) ? null : ISODateTimeFormat.dateTime().print(lastCompletedOn);
			this.numJobs = numJobs;
			this.isRunning = isRunning;
			this.maintenanceRateSystem = maintenanceRateSystem;
			this.maintenanceRateUser = maintenanceRateUser;
		}

		public String getLastCompletedOn() {
			return this.lastCompletedOn;
		}

		public Integer getNumJobs() {
			return this.numJobs;
		}

		public boolean isRunning() {
			return this.isRunning;
		}

		public Integer getMaintenanceRateSystem() {
			return this.maintenanceRateSystem;
		}

		public Integer getMaintenanceRateUser() {
			return this.maintenanceRateUser;
		}

	}
}