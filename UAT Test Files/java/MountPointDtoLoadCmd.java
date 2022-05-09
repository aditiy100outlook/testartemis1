package com.code42.server.mount;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.Session;

import com.code42.core.CommandException;
import com.code42.core.alert.ISystemAlertService;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;
import com.code42.core.relation.IRelationService;
import com.code42.core.space.ISpaceService;
import com.code42.core.space.SpaceException;
import com.code42.hierarchy.OrgsFindForGuidsCmd;
import com.code42.hierarchy.UsersFindForGuidsCmd;
import com.code42.io.Byte;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.server.ServerNotifySettings;
import com.code42.server.ServerNotifySettingsFindByServerIdQuery;
import com.code42.server.license.IUserLicenseService;
import com.code42.server.node.Node;
import com.code42.server.node.NodeFindAllQuery;
import com.code42.space.SpaceKeyUtils;
import com.code42.sql.SQLQuery;
import com.code42.sql.SQLUtils;
import com.code42.stats.AggregateStorageStats;
import com.code42.utils.ArrayUtils;
import com.code42.utils.LangUtils;
import com.code42.utils.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

/**
 * Load the statistics into the provided mount dtos.
 */
public class MountPointDtoLoadCmd extends DBCmd<Void> {

	/* ================= Dependencies ================= */
	@Inject
	private ISystemAlertService systemAlerts;
	@Inject
	private IUserLicenseService license;

	private ISpaceService space;
	private IRelationService relation;

	/* ================= DI injection points ================= */
	@Inject
	public void setSpace(ISpaceService space) {
		this.space = space;
	}

	@Inject
	public void setRelation(IRelationService relation) {
		this.relation = relation;
	}

	private static final Logger log = LoggerFactory.getLogger(MountPointDtoLoadCmd.class);

	private final List<MountPointDto> dtos;

	// Transient
	private Map<Integer, Boolean> mountPointsOnlineStatus;
	private Map<Integer, Boolean> serversOnlineStatus;

	public MountPointDtoLoadCmd(MountPointDto dto) {
		this(ArrayUtils.asList(dto));
	}

	public MountPointDtoLoadCmd(List<MountPointDto> dtos) {
		super();
		this.dtos = dtos;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		Stopwatch sw = new Stopwatch();

		// no additional auth call; we assume the finder already did that

		try {

			this.mountPointsOnlineStatus = this.space.getAsType(SpaceKeyUtils.getMountPointOnlineStatusKey(), Map.class);

			// Build a map of server statuses by serverId (instead of GUID)
			Map<Long, Boolean> serversOnlineStatusByGuid = this.space.getAsType(SpaceKeyUtils.getServerOnlineStatusKey(),
					Map.class);
			if (serversOnlineStatusByGuid == null) {
				serversOnlineStatusByGuid = new HashMap<Long, Boolean>();
			}

			this.serversOnlineStatus = Maps.newHashMap();
			List<Node> nodes = this.db.find(new NodeFindAllQuery(this.env.getMyClusterId()));
			for (Node node : nodes) {
				Boolean online = serversOnlineStatusByGuid.get(node.getNodeGuid());
				// Note that online may be null at this point
				this.serversOnlineStatus.put(node.getNodeId(), Boolean.TRUE.equals(online));
			}

			List<Integer> mpIds = Lists.newArrayList();
			for (MountPointDto dto : this.dtos) {
				mpIds.add(dto.getMountPointId());
			}
			Map<Integer, DbStatsDto> dbStatsMap = this.db.find(new MountPointDbStatsQuery(mpIds));

			for (MountPointDto dto : this.dtos) {
				DbStatsDto dbStats = dbStatsMap.get(dto.getMountPointId());
				this.load(dto, dbStats, session);
			}

			log.debug("MountPointDtoLoadCmd took {} ms for {} DTOs", sw.getElapsed(), this.dtos.size());
			return null;
		} catch (SpaceException se) {

			throw new CommandException("Exception performing space operations", se);
		}
	}

	/**
	 * Load a single dto.
	 */
	private void load(MountPointDto dto, DbStatsDto dbStats, CoreSession session) throws CommandException {

		/*
		 * For now these stats are derived from the database; in the future we may store them in a representation of mount
		 * point data in the space.
		 */
		if (dbStats != null) {
			dto.setColdBytes(dbStats.coldBytes);
			dto.setArchiveBytes(dbStats.archiveBytes);
			dto.setSelectedBytes(dbStats.selectedBytes);
			dto.setTodoBytes(dbStats.todoBytes);
		}

		Set<Long> assignedDevices = this.relation.getGuidsForMountPoint(dto.getMountPointId());
		Set<Long> devicesWithBackup = this.license.whichGuidsHaveArchive(assignedDevices);

		Set<Long> nonBackupDevices = Sets.difference(assignedDevices, devicesWithBackup);

		Set<Integer> licensedUsers = this.run(new UsersFindForGuidsCmd(devicesWithBackup), session);
		Set<Integer> nonLicensedUsers = this.run(new UsersFindForGuidsCmd(nonBackupDevices), session);
		Set<Integer> assignedOrgs = this.run(new OrgsFindForGuidsCmd(assignedDevices), session);

		dto.setAssignedComputerCount(devicesWithBackup.size() + nonBackupDevices.size());
		dto.setBackupComputerCount(devicesWithBackup.size());
		dto.setAssignedUserCount(licensedUsers.size() + nonLicensedUsers.size());
		dto.setLicensedUserCount(licensedUsers.size());
		dto.setAssignedOrgCount(assignedOrgs.size());

		int clusterServerId = this.env.getMyClusterId();
		ServerNotifySettings settings = this.db.find(new ServerNotifySettingsFindByServerIdQuery(clusterServerId));
		long warningBytes = settings.getWarningGigabytes() * Byte.Decimal.GIGABYTE;
		long criticalBytes = settings.getCriticalGigabytes() * Byte.Decimal.GIGABYTE;

		boolean online = false;
		if (this.mountPointsOnlineStatus != null) {
			online = this.mountPointsOnlineStatus.containsKey(dto.getMountPointId()) ? this.mountPointsOnlineStatus.get(dto
					.getMountPointId()) : false;
		}
		dto.setOnline(online);

		if (this.serversOnlineStatus != null) {
			online = this.serversOnlineStatus.containsKey(dto.getServerId()) ? this.serversOnlineStatus
					.get(dto.getServerId()) : false;
			dto.setServerOnline(online);
		}

		try {
			AggregateStorageStats storageStats = this.space.getAsType(SpaceKeyUtils.getMountPointStorageKey(dto
					.getMountPointId()), AggregateStorageStats.class);

			if (storageStats != null) {
				long freeBytes = storageStats.getFreeBytes();
				long totalBytes = storageStats.getTotalBytes();
				dto.setFreeBytes(freeBytes);
				dto.setTotalBytes(totalBytes);
				// TODO: This logic is replicating that in DiskSpaceAlerterCmd, but that only runs once per hour.
				// This runs when people are viewing store points and expect an up-to-date alert status.
				if (dto.isOnline() && dto.isAcceptingInboundBackup() && totalBytes > 0) {
					if (freeBytes < criticalBytes) {
						dto.setFreeBytesCritical();
						this.systemAlerts.clearStorePointSpaceAlert(dto.getMountPoint(), false); // clear warning
						this.systemAlerts.triggerStorePointSpaceAlert(dto.getMountPoint(), true); // set critical
					} else if (freeBytes < warningBytes) {
						dto.setFreeBytesWarning();
						this.systemAlerts.clearStorePointSpaceAlert(dto.getMountPoint(), true); // clear critical
						this.systemAlerts.triggerStorePointSpaceAlert(dto.getMountPoint(), false); // set warning
					} else {
						// remove any warning or critical alerts for this store point
						this.systemAlerts.clearStorePointSpaceAlert(dto.getMountPoint(), true); // clear critical
						this.systemAlerts.clearStorePointSpaceAlert(dto.getMountPoint(), false); // clear warning
					}
				} else {
					// remove any warning or critical alerts for this store point
					this.systemAlerts.clearStorePointSpaceAlert(dto.getMountPoint(), true); // clear critical
					this.systemAlerts.clearStorePointSpaceAlert(dto.getMountPoint(), false); // clear warning
				}
				dto.setBackupSessionCount(storageStats.getNumBackupSessions());
			}

		} catch (SpaceException e) {
			throw new CommandException("Unable to get storage stats for mountPoint: " + dto.getMountPointId(), e);
		}
	}

	/* ======================== Private query class used by this command ======================== */

	private static class MountPointDbStatsQuery extends FindQuery<Map<Integer, DbStatsDto>> {

		/*
		 * This query runs at least twice as fast as it's predecessor. Never change it without comparing the old query with
		 * the new one against a production-sized database. (not production itself :-) P.S. throw out the results the first
		 * time you run it. The first run is always a *lot* slower.
		 */
		private final static String SQL = "" //
				+ "select fcuRollup.mount_point_id,                                  \n" //
				+ "     fcuRollup.is_using,                                          \n" //
				+ "     coalesce(fcuRollup.archiveBytesTotal, 0) as archiveBytes,    \n" //
				+ "     coalesce(fcuRollup.selectedBytesTotal, 0) as selectedBytes,  \n" //
				+ "     coalesce(fcuRollup.todoBytesTotal, 0) as todoBytes           \n" //
				+ "from (select                                                      \n" //
				+ "        mount_point_id as mount_point_id,           \n" //
				+ "        is_using as is_using,                       \n" //
				+ "        sum(archive_bytes) as archiveBytesTotal,    \n" //
				+ "        sum(selected_bytes) as selectedBytesTotal,  \n" //
				+ "        sum(todo_bytes) as todoBytesTotal           \n" //
				+ "      from t_friend_computer_usage                  \n" //
				+ "      where mount_point_id IN (:mpIds)              \n" //
				+ "      group by mount_point_id, is_using             \n" //
				+ ") as fcuRollup                                      \n";

		private final Collection<Integer> mpIds;

		private MountPointDbStatsQuery(int mpId) {
			this(Collections.singletonList(mpId));
		}

		private MountPointDbStatsQuery(Collection<Integer> mpIds) {
			this.mpIds = mpIds;
		}

		@Override
		public Map<Integer, DbStatsDto> query(Session session) throws DBServiceException {
			if (this.mpIds.size() < 1) {
				return new HashMap<Integer, DbStatsDto>();
			}

			log.trace("MountPointDtoLoadQuery SQL: {}\n mpIds: {}", SQL, this.mpIds);

			if (!LangUtils.hasElements(this.mpIds)) {
				return Maps.newHashMap();
			}

			SQLQuery query = SQLUtils.createSQLQuery(session, SQL);
			query.setParameterList("mpIds", this.mpIds);
			List<Object[]> rows = query.list();

			/*
			 * Build the map of DbStatsDto instances. Up to two input rows will go into each DbStatsDto instance. The
			 * difference between the two rows is whether or not it represents "cold" storage.
			 */
			Map<Integer, DbStatsDto> map = Maps.newHashMap();
			try {
				for (Object[] row : rows) {
					DbStatsDto stats = map.get(SQLUtils.getInteger(row[0]));
					if (stats == null) {
						stats = new DbStatsDto(row);
						map.put(stats.mountPointId, stats);
					} else {
						stats.merge(row);
					}
				}
			} catch (DBServiceException e) {
				throw e;
			} catch (Exception e) {
				throw new DBServiceException(e);
			}

			return map;
		}
	}

	/**
	 * Provides archive stats from the database
	 */
	private static class DbStatsDto {

		int mountPointId;
		long coldBytes;
		long archiveBytes;
		long selectedBytes;
		long todoBytes;
		int rowCount;

		DbStatsDto(Object[] row) throws DBServiceException {
			this.merge(row);
		}

		void merge(Object[] row) throws DBServiceException {
			// Do some sanity checking... in case someone changes the query and breaks things.
			this.rowCount++;
			assert this.rowCount <= 2;
			assert row.length == 5;
			assert row[1] instanceof Boolean; // isUsing
			int mountPointId = SQLUtils.getint(row[0]);
			if (this.mountPointId > 0) {
				assert this.mountPointId == mountPointId;
			}

			// OK, start merging the data
			this.mountPointId = mountPointId;
			boolean isUsing = SQLUtils.getboolean(row[1]);
			long storedBytes = LangUtils.coalesce(SQLUtils.getLong(row[2]), 0L);
			if (!isUsing) {
				// coldBytes only get updated if *not* "using" (i.e. the computer is deactivated)
				this.coldBytes = storedBytes;
			} else {
				this.archiveBytes = storedBytes;
				this.selectedBytes = LangUtils.coalesce(SQLUtils.getLong(row[3]), 0L);
				this.todoBytes = LangUtils.coalesce(SQLUtils.getLong(row[4]), 0L);
			}
		}
	}

	public static void main(String[] args) {

		System.out.println("--MountPointDtoLoadQuery SQL:\n" + MountPointDbStatsQuery.SQL);
	}
}
