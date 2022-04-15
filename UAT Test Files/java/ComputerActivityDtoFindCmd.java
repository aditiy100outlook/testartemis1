package com.code42.computer;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.backup42.CpcConstants;
import com.code42.backup.central.ICentralService;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.hierarchy.IHierarchyService;
import com.code42.core.impl.AbstractCmd;
import com.code42.core.relation.IRelationService;
import com.code42.core.server.IServerService;
import com.code42.core.space.CoreSpace;
import com.code42.core.space.ISpaceService;
import com.code42.stats.AggregateBackupStats;
import com.code42.stats.SpaceBoundModelUtils;
import com.code42.utils.Pair;
import com.code42.utils.Triple;
import com.google.inject.Inject;

/**
 * Find computer connection/activity information.
 * 
 * @author mscorcio
 * 
 */
class ComputerActivityDtoFindCmd extends AbstractCmd<Map<Long, Map<Long, ComputerActivityDto>>> {

	private ISpaceService space;
	private IServerService server;
	private ICentralService central;
	private IRelationService relation;
	private IHierarchyService hier;

	@Inject
	public void setSpaceService(ISpaceService space) {
		this.space = space;
	}

	@Inject
	public void setServerService(IServerService server) {
		this.server = server;
	}

	@Inject
	public void setCentralService(ICentralService central) {
		this.central = central;
	}

	@Inject
	public void setRelationService(IRelationService relation) {
		this.relation = relation;
	}

	@Inject
	public void setHierarchyService(IHierarchyService hier) {
		this.hier = hier;
	}

	private final Set<Long> guids;
	private final Long targetComputerGuid;

	public ComputerActivityDtoFindCmd(Collection<Long> guids, Long targetComputerGuid) {
		this.guids = new HashSet(guids);
		this.targetComputerGuid = targetComputerGuid;
	}

	@Override
	public Map<Long, Map<Long, ComputerActivityDto>> exec(CoreSession session) throws CommandException {
		try {
			final Set<Integer> userIds = new HashSet<Integer>();
			for (long guid : this.guids) {
				Triple<Integer, Integer, Long> hierarchy = this.hier.getHierarchyByGUID(guid);
				userIds.add(hierarchy.getTwo());
			}

			Map<Long, Long> spaceToDestination = this.relation.getSpaceDestinationMappings();

			Map<Long, Map<Long, ComputerActivityDto>> rv = this.space.mapReduce(CoreSpace.USER_BACKUP_STATS_MODEL,
					new Mapper(this.guids, this.targetComputerGuid, userIds, spaceToDestination), new Reducer(this.guids));

			this.addConnectedStatusFromCentral(rv);

			return rv;
		} catch (Exception e) {
			throw new CommandException("Error retrieving usage information", e);
		}
	}

	// If could not find activity from stats stored in the space check for connected status in ICentralService
	private void addConnectedStatusFromCentral(Map<Long, Map<Long, ComputerActivityDto>> activityMap) {
		long myDestinationGuid = this.server.getMyDestination().getDestinationGuid();
		long destinationGuid = this.targetComputerGuid == null ? myDestinationGuid : this.targetComputerGuid.longValue();

		if (destinationGuid != myDestinationGuid && destinationGuid != CpcConstants.Computer.ROLLUP_TARGET_GUID) {
			return;
		}

		for (long guid : activityMap.keySet()) {
			Map<Long, ComputerActivityDto> activityByDest = activityMap.get(guid);
			ComputerActivityDto dto = activityByDest.get(destinationGuid);
			if (dto == null) {
				boolean connected = this.central.isConnected(guid);
				if (connected) {
					dto = new ComputerActivityDto();
					dto.connected = connected;
					activityByDest.put(destinationGuid, dto);
				}
			}
		}
	}

	private static class Mapper implements com.code42.core.space.mapreduce.Mapper<Pair<Long, Long>, ComputerActivityDto> {

		private static final long serialVersionUID = 2103890655173309265L;

		private final Set<Long> guids;
		private final Long targetComputerGuid;
		private final Set<Integer> userIds;
		private final Map<Long, Long> spaceToDestination;

		public Mapper(Set<Long> guids, Long targetComputerGuid, Set<Integer> userIds, Map<Long, Long> spaceToDestination) {
			this.guids = guids;
			this.targetComputerGuid = targetComputerGuid;
			this.userIds = userIds;
			this.spaceToDestination = spaceToDestination;
		}

		public Map<Pair<Long, Long>, ComputerActivityDto> map(Map<Object, Object> locals) {
			Map<Pair<Long, Long>, ComputerActivityDto> rv = new HashMap<Pair<Long, Long>, ComputerActivityDto>();

			for (Object key : locals.keySet()) {
				if (!(key instanceof Integer)) {
					continue;
				}

				Integer userId = (Integer) key;
				if (!this.userIds.contains(userId)) {
					continue;
				}

				Map<Pair<Long, Long>, AggregateBackupStats> userModel = (Map) locals.get(key);
				Map<Long, Map<Long, AggregateBackupStats>> deviceDestModel = SpaceBoundModelUtils
						.createComputerToDestinationModel(userModel, this.spaceToDestination);

				for (long guid : deviceDestModel.keySet()) {
					if (!this.guids.contains(guid)) {
						continue;
					}

					Map<Long, AggregateBackupStats> results = new HashMap<Long, AggregateBackupStats>();

					Map<Long, AggregateBackupStats> destModel = deviceDestModel.get(guid);
					if (this.targetComputerGuid == null) {
						results.putAll(destModel);
					} else if (this.targetComputerGuid.longValue() == CpcConstants.Computer.ROLLUP_TARGET_GUID) {
						AggregateBackupStats stats = SpaceBoundModelUtils.sumDestinations(destModel.values());
						results.put(CpcConstants.Computer.ROLLUP_TARGET_GUID, stats);
					} else {
						AggregateBackupStats stats = destModel.get(this.targetComputerGuid);
						results.put(this.targetComputerGuid, stats);
					}

					for (long destGuid : results.keySet()) {
						ComputerActivityDto activity = buildItem(results.get(destGuid));
						rv.put(new Pair<Long, Long>(guid, destGuid), activity);
					}
				}
			}
			return rv;
		}
	}

	private static ComputerActivityDto buildItem(AggregateBackupStats stats) {
		ComputerActivityDto activity = new ComputerActivityDto();
		activity.connected = stats.getConnectedCount() > 0;
		activity.backingUp = stats.getBackupSessionCount() > 0;
		activity.restoring = stats.getRestoreSessionCount() > 0;
		activity.timeRemainingInMs = stats.getTimeRemainingInMs();
		activity.remainingBytes = stats.getTodoBytes();
		activity.remainingFiles = stats.getTodoFiles();
		return activity;
	}

	private static class Reducer
			implements
			com.code42.core.space.mapreduce.Reducer<Pair<Long, Long>, ComputerActivityDto, Map<Long, Map<Long, ComputerActivityDto>>> {

		private Set<Long> guids;

		public Reducer(Set<Long> guids) {
			this.guids = guids;
		}

		public Map<Long, Map<Long, ComputerActivityDto>> reduce(Map<Pair<Long, Long>, ComputerActivityDto> map) {
			Map<Long, Map<Long, ComputerActivityDto>> rv = new HashMap<Long, Map<Long, ComputerActivityDto>>();
			for (Long guid : this.guids) {
				rv.put(guid, new HashMap<Long, ComputerActivityDto>());
			}
			for (Pair<Long, Long> key : map.keySet()) {
				rv.get(key.getOne()).put(key.getTwo(), map.get(key));
			}
			return rv;
		}
	}
}
