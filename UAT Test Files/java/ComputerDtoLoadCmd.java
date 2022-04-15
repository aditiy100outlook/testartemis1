/*
 * Created on April 6, 2011 by Jon Carlson
 * 
 * <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.computer;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.code42.archiverecord.ArchiveRecord;
import com.code42.archiverecord.ArchiveRecordFindBySourceQuery;
import com.code42.config.ConfigFindByComputerIdCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.core.server.impl.CachedDestination;
import com.code42.core.server.impl.CachedServerNode;
import com.code42.io.Serializer;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.destination.DestinationFindAvailableByOrgCmd;
import com.code42.server.BaseServer;
import com.code42.server.BaseServerFindByGuidQuery;
import com.code42.server.destination.Destination;
import com.code42.server.node.Node;
import com.code42.server.node.NodeFindByMountQuery;
import com.code42.user.DataEncryptionKeyFindByUserQuery;
import com.code42.utils.SystemProperties;
import com.code42.utils.Time;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Special 2nd-tier command used only to load up the transient, yet important, components of a computer.
 */
class ComputerDtoLoadCmd extends DBCmd<Void> {

	private static Logger log = LoggerFactory.getLogger(ComputerDtoLoadCmd.class);

	private Collection<ComputerDto> computers;
	private final ComputerDtoFindByCriteriaBuilder builder;

	protected ComputerDtoLoadCmd(ComputerDto computer, ComputerDtoFindByCriteriaBuilder builder) {
		this.computers = new ArrayList<ComputerDto>();
		this.computers.add(computer);
		this.builder = builder;
	}

	protected ComputerDtoLoadCmd(Collection<ComputerDto> computers, ComputerDtoFindByCriteriaBuilder builder) {
		this.computers = computers;
		this.builder = builder;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		if (this.computers.size() > SystemProperties.getMaxQueryInClauseSize()) {
			throw new CommandException("This command is not optimized for more than 250 computers");
		}

		// Create a map keyed by computerId
		// ... and map keyed by GUID
		Map<Long, ComputerDto> idMap = Maps.newHashMap();
		Map<Long, ComputerDto> guidMap = Maps.newHashMap();
		for (ComputerDto c : this.computers) {
			idMap.put(c.getComputerId(), c);
			guidMap.put(c.getGuid(), c);
		}

		if (this.builder.isIncludeBackupUsage()) {
			/*
			 * Find and add backup destination information
			 */
			List<ComputerBackupUsageDto> fcuList = this.db.find(new ComputerBackupUsageDtoFindByComputerQuery(idMap.keySet(),
					this.builder.getTargetComputerGuid()));
			this.mergeDestinations(idMap, fcuList);

			if (this.builder.isIncludeActivity()) {
				/*
				 * Find and add backup activity statistics
				 */
				Map<Long, Map<Long, ComputerActivityDto>> activities = this.runtime.run(new ComputerActivityDtoFindCmd(guidMap
						.keySet(), this.builder.getTargetComputerGuid()), session);
				for (Long guid : guidMap.keySet()) {
					ComputerDto computer = guidMap.get(guid);
					Map<Long, ComputerActivityDto> activitiesByDest = activities.get(guid);
					if (activitiesByDest == null) {
						continue;
					}
					for (ComputerBackupUsageDto dest : computer.getDestinations()) {
						ComputerActivityDto activity = activitiesByDest.get(dest.getTargetComputerGuid());
						if (activity == null) {
							activity = new ComputerActivityDto();
						}
						dest.activity = activity;
					}
				}
			}
		}

		if (this.builder.isIncludeAuthority()) {
			this.addAuthorityDestination(idMap, session);
		}

		if (this.builder.isIncludeSettings()) {
			if (this.computers.size() > 1) {
				throw new CommandException("Can only request computer settings for a single computer.");
			} else if (!this.computers.isEmpty()) {
				final ComputerDto c = this.computers.iterator().next();
				final ConfigFindByComputerIdCmd cmd = new ConfigFindByComputerIdCmd(c.getComputerId());
				final Config config = this.runtime.run(cmd, session);
				c.setSettings(config);

				// Look up the archive encryption key type.
				final DataEncryptionKey key = this.db.find(new DataEncryptionKeyFindByUserQuery(c.getUserId()));
				if (key != null) {
					c.setSecurityKeyType(key.getSecurityKeyType());
				}

				// Look up available destinations.
				List<Destination> myOrgDestinations = this.run(new DestinationFindAvailableByOrgCmd(c.getOrgId()), session);
				c.setAvailableDestinations(myOrgDestinations);
			}
		}

		if (this.builder.isIncludeHistory()) {
			/*
			 * Find and add archive record statistics (30 days of backup history by target)
			 */
			int HISTORY_LENGTH = 30;
			Date thirtyDaysAgo = Time.add(new Date(), Calendar.DATE, -HISTORY_LENGTH);
			ArchiveRecordFindBySourceQuery historyQuery = new ArchiveRecordFindBySourceQuery(idMap.keySet(), thirtyDaysAgo);
			List<ArchiveRecord> archiveHistory = this.db.find(historyQuery);

			ComputerBackupUsageDto fcu = null;
			for (ArchiveRecord ar : archiveHistory) {
				ComputerDto c = idMap.get(ar.getSourceComputerId());
				if (c == null) {
					log.warn("No source computer found for ArchiveRecord: " + ar); // This should *never* happen
					break;
				}
				// Loop through the computers destinations only if we need to
				if (fcu == null || fcu.getComputerId() != ar.getSourceComputerId()
						|| fcu.getTargetComputerId() != ar.getTargetComputerId()) {
					fcu = null;
					for (ComputerBackupUsageDto dest : c.backupUsage) {
						if (dest.getTargetComputerId() == ar.getTargetComputerId()) {
							fcu = dest;
							break;
						}
					}
				}
				if (fcu != null) {
					fcu.getHistory().add(new ArchiveRecordDto(ar));
				}
			}
		}

		return null;
	}

	private void mergeDestinations(Map<Long, ComputerDto> cMap, List<ComputerBackupUsageDto> destinations) {
		for (ComputerDto c : cMap.values()) {
			c.backupUsage = new ArrayList<ComputerBackupUsageDto>(5);
		}

		// Loop through destinations and add them to the appropriate computer
		for (ComputerBackupUsageDto fcu : destinations) {
			ComputerDto c = cMap.get(fcu.getComputerId());
			c.backupUsage.add(fcu);

			if (this.builder.isIncludeHistory()) {
				/*
				 * Make sure FCU has a history list (to be populated later) even if there are no ArchiveRecord rows for it yet.
				 * An empty list means the history was requested, but none were found.
				 */
				fcu.history = Lists.newArrayList();
			}
		}
	}

	private void addAuthorityDestination(Map<Long, ComputerDto> cMap, CoreSession session) throws CommandException {
		/*
		 * Maps guids to computers
		 */
		Map<Long, Computer> authorities = new HashMap<Long, Computer>();
		this.initThisDestination(authorities);

		for (ComputerDto c : cMap.values()) {
			for (ComputerBackupUsageDto fcu : c.backupUsage) {

				if (this.isDestination(fcu.getTargetComputerGuid())) {
					long authorityGuid = this.getAuthorityDestinationGuid(fcu, session);
					if (!authorities.containsKey(authorityGuid)) {
						/*
						 * NOTE: Use a *COPY* of the returned Hibernate POJO because setting the address will cause a database
						 * update upon session flush.
						 */
						Computer authority = this.getClonedAuthorityComputer(authorityGuid);
						this.setAuthorityServerAddress(authority);
						authorities.put(authorityGuid, authority);
					}

					// always use the authority from the map, has corrected computer entry for THIS server
					Computer auth = authorities.get(authorityGuid);
					c.authority = new AuthorityTargetUsageDto(auth, fcu.getFriendComputerUsageId());
				}
			}
		}
	}

	private void setAuthorityServerAddress(Computer authority) {
		try {
			final BaseServer server = this.db.find(new BaseServerFindByGuidQuery(authority.getGuid()));
			if (server != null) {
				authority.setAddress(server.getPrimaryAddress());
			}
		} catch (final Exception e) {
			log.warn("Unable to determine correct mount point server address, computer=" + authority + ", " + e, e);
		}

	}

	private void initThisDestination(Map<Long, Computer> authorities) throws CommandException {

		CachedDestination myDestination = this.serverService.getMyDestination();
		long authorityGuid = myDestination.getDestinationGuid();

		/*
		 * NOTE: Use a *COPY* of the returned Hibernate POJO because setting the address will cause a database update upon
		 * session flush.
		 */
		Computer authority = this.getClonedAuthorityComputer(authorityGuid);

		// Get the actual server handling our mount point (not the cluster computer)
		CachedServerNode myServer = this.serverService.getMyNode();

		authority.setAddress(myServer.getPrimaryAddress());

		authorities.put(authorityGuid, authority);
	}

	private Computer getClonedAuthorityComputer(long authorityGuid) throws CommandException {
		Computer authority = this.runtime.run(new ComputerFindByGuidCmd(authorityGuid), this.auth.getAdminSession());
		try {
			return (Computer) Serializer.copy(authority);
		} catch (Exception e) {
			throw new CommandException(e);
		}
	}

	private long getAuthorityDestinationGuid(ComputerBackupUsageDto fcu, CoreSession session) throws CommandException {

		// Only works if the target is itself an authority; if the target is NOT an authority, then the GUID
		// returned will be for a member of some other destination, which would be incorrect. Logic should
		// probably be changed to reflect this.
		Integer mountPointId = fcu.getMountPointId();
		if (mountPointId != null && !this.serverService.isMyMountPoint(mountPointId)) {
			Node node = this.db.find(new NodeFindByMountQuery(mountPointId));
			if (node != null) {
				return node.getNodeGuid();
			}
		}

		return fcu.getTargetComputerGuid();

	}

	private boolean isDestination(long guid) {
		return this.serverService.getCache().isDestination(guid);
	}

}
