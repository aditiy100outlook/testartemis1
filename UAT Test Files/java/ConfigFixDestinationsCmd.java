package com.code42.config;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Level;
import org.hibernate.Session;

import com.backup42.common.OrgType;
import com.backup42.common.config.ConfigUpgradeV2BackupSets;
import com.backup42.common.config.ConfigUpgradeV3;
import com.backup42.common.config.ServiceConfig;
import com.backup42.common.perm.C42PermissionPro;
import com.backup42.computer.ConfigServices;
import com.backup42.computer.data.ComputerLockByIdQuery;
import com.backup42.history.CpcHistoryLogger;
import com.beust.jcommander.internal.Lists;
import com.beust.jcommander.internal.Maps;
import com.code42.backup.config.BackupSetConfig;
import com.code42.computer.Computer;
import com.code42.computer.ComputerFindByGuidQuery;
import com.code42.computer.ComputerFindByIdQuery;
import com.code42.computer.ComputerForceUpdateQuery;
import com.code42.computer.ComputerUpdateCmd;
import com.code42.computer.Config;
import com.code42.computer.IComputer;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.businessobjects.IBusinessObjectsService;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.BackupOrg;
import com.code42.org.OrgFindByIdQuery;
import com.code42.org.destination.DestinationFindAvailableByOrgCmd;
import com.code42.server.destination.ClusterDestinationFindGuidsQuery;
import com.code42.server.destination.Destination;
import com.code42.sql.SQLQuery;
import com.code42.sql.SQLUtils;
import com.code42.user.IUser;
import com.code42.user.UserLockByIdQuery;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

/**
 * Loops through the default computer config for every org and the config for every computer in the system, removing
 * destinations that should not be there. This also creates configs for computers that do not have a configId.
 * 
 * This is not run on a regular basis, but is intended to be run from the master web CLI when considered necessary.
 * 
 * Only one of these can be running at a time.
 * 
 * See http://jira/browse/CP-6163
 */
public class ConfigFixDestinationsCmd extends DBCmd<Boolean> {

	private static final Logger log = LoggerFactory.getLogger(ConfigFixDestinationsCmd.class);

	private static final Object[] monitor = new Object[] {};

	// Note that this is static because we cannot
	private static boolean running = false;

	@Inject
	private IBusinessObjectsService busObjs;

	private boolean simulate = false;
	private Set<Long> allDestinationGuids = new HashSet<Long>();

	public ConfigFixDestinationsCmd() {
		this(false);
	}

	public ConfigFixDestinationsCmd(boolean simulate) {
		super();
		this.simulate = simulate;
	}

	@Override
	public Boolean exec(CoreSession session) throws CommandException {

		if (!this.env.isMaster()) {
			log.trace("config.fix:: skipping destinations job");
			return false;
		}

		synchronized (monitor) {
			if (running == false) {
				running = true;
			} else {
				// We cannot run because another instance of this job already running
				log.warn("config.fix:: ConfigFixDestinationsCmd is already running. Cannot run now.");
				return false;
			}
		}

		// Change the logging level on 2 classes temporarily
		org.apache.log4j.Logger logger1 = org.apache.log4j.Logger.getLogger(ConfigUpgradeV2BackupSets.class.getName());
		org.apache.log4j.Logger logger2 = org.apache.log4j.Logger.getLogger(ConfigUpgradeV3.class.getName());
		Level originalLevel1 = logger1.getLevel();
		Level originalLevel2 = logger2.getLevel();
		logger1.setLevel(Level.WARN);
		logger2.setLevel(Level.WARN);

		try {

			this.db.ensureNoTransaction();

			// Authorization
			this.auth.isAuthorized(session, C42PermissionPro.System.MANAGE_DATABASE);

			log.info("config.fix::{} Destinations START", (this.simulate ? " SIMULATION:" : ""));

			int orgConfigCheckCount = 0;
			int orgConfigChangeCount = 0;
			int computerConfigCheckCount = 0;
			int computerConfigChangeCount = 0;

			this.allDestinationGuids = this.db.find(new ClusterDestinationFindGuidsQuery());

			EnumSet<OrgType> clusterOrgTypes = CoreBridge.getEnvironment().getClusterOrgTypes();
			// Convert to strings
			List<String> orgTypes = Lists.newArrayList(clusterOrgTypes.size());
			for (final OrgType ot : clusterOrgTypes) {
				orgTypes.add(ot.toString());
			}

			// Create a map of the destinations for each org so we can verify when checking the computers.
			Map<Integer, OrgInfo> orgMap = this.db.find(new OrgInfoMapQuery(orgTypes)); // Map<orgId, orgInfo>

			// This would be a LOT less database hits if we read all the orgDestination information in one query and processed
			// it in memory. The downside is creating logic similar to what happens inside DestinationFindAvailableByOrgCmd
			for (Integer orgId : orgMap.keySet()) {
				OrgInfo info = orgMap.get(orgId);
				List<Destination> destinations = this.run(new DestinationFindAvailableByOrgCmd(orgId), session);
				info.destinationGuids = new HashSet<Long>();
				for (Destination d : destinations) {
					info.destinationGuids.add(d.getDestinationGuid());
				}
			}

			// Loop through each org, checking the destinations in their config
			// This is going to be somewhat slow regardless
			try {
				this.db.beginTransaction();
				this.db.manual();

				for (Integer orgId : orgMap.keySet()) {
					orgConfigCheckCount++;
					OrgInfo info = orgMap.get(orgId);
					if (this.checkOrgConfig(info, session)) {
						orgConfigChangeCount++;
					}
					this.db.restartTransaction(20);
				}
				this.db.commit();
			} finally {
				this.db.endTransaction();
			}

			log.info("config.fix::{} STEP: Org configs changed/checked:{}/{}", (this.simulate ? " SIMULATION:" : ""),
					orgConfigChangeCount, orgConfigCheckCount);

			long maxComputerId = this.db.find(new ComputerIdMaxQuery());
			long idInterval = 100;

			// Loop through each computer (both active and deactivated), checking the destinations in each config
			// Loop through up to 100 at a time
			for (long i = 0; i < maxComputerId; i = i + idInterval) {
				List<ComputerInfo> infos = this.db.find(new ComputerInfoQuery(i, i + idInterval));
				this.db.ensureNoTransaction(); // We cannot keep the table locked
				for (ComputerInfo cInfo : infos) {
					computerConfigCheckCount++;
					IUser u = null;
					try {
						u = this.busObjs.getUser(cInfo.userId);
						OrgInfo oInfo = orgMap.get(u.getOrgId());
						if (oInfo == null) {
							log.info("Ignoring GUID {}  It's org ({}) probably has no config.", cInfo.guid, u.getOrgId());
						} else if (this.checkComputerConfig(cInfo, oInfo, session)) {
							computerConfigChangeCount++;
						}
					} catch (Exception e) {
						log.error("config.fix:: Error checking computer config for GUID: {}", cInfo.guid, e);
					}
				}
			}

			log.info("config.fix::{} STEP: Computer configs changed/checked:{}/{}", (this.simulate ? " SIMULATION:" : ""),
					computerConfigChangeCount, computerConfigCheckCount);

			log.info("config.fix::{} Destinations COMPLETED!!!");

		} finally {
			running = false;
			// Set the logger back to what it should be
			logger1.setLevel(originalLevel1);
			logger2.setLevel(originalLevel2);
		}

		return true;
	}

	/**
	 * 
	 * @param cInfo
	 * @param oInfo
	 * @param session
	 * @throws Exception
	 */
	private boolean checkComputerConfig(ComputerInfo cInfo, OrgInfo oInfo, CoreSession session) throws Exception {

		if (cInfo.configId == null) {
			//
			// Create config for this computer and leave
			//
			try {
				// We need to create a dummy config... a bug kept one from being created in the first place.

				if (this.simulate) {
					log.info(
							"config.fix:: SIMULATION: Computer config for GUID {} is missing from database and would have been reset to default values.",
							cInfo.guid);
				} else {
					this.createComputerConfig(cInfo, session);

					// Always log the change in the history log which doesn't roll over as quickly
					CpcHistoryLogger.warn(cInfo.guid, session,
							"config.fix:: Computer config was missing from database and has been reset to default values.");
				}

				return true;
			} catch (Exception e) {
				log.error("config.fix:: Error creating config for GUID {}.", cInfo.guid, e);
			}
		}

		{
			boolean changed = false;
			Config config = this.run(new ConfigFindByComputerIdCmd(cInfo.computerId), session);
			ServiceConfig serviceConfig = null;

			try {
				serviceConfig = config.toServiceConfig();

				// for each backup set, remove destinations that are not offered to this org.
				for (final BackupSetConfig set : serviceConfig.serviceBackup.backup.backupSets.values()) {
					Set<Long> destinationGuids = new HashSet(set.destinations.keySet());
					// difference returns the autoStartGuids that are not in the destinationGuids
					Set<Long> toRemove = new HashSet(Sets.difference(destinationGuids, oInfo.destinationGuids));
					for (Long destinationGuid : toRemove) {
						// Only remove server/cluster destinations
						if (this.allDestinationGuids.contains(destinationGuid)) {
							this.removeComputerDestination(cInfo.guid, destinationGuid, set);
							changed = true;
						} else {
							// destinationGuid is either a client peer or it is old destination that's not in the DB anymore.
							// 2013-01 : Added check to distinguish between the two.
							// Check cache first, then the database
							IComputer c = this.busObjs.getComputerByGuid(destinationGuid);
							if (c == null) {
								c = this.db.find(new ComputerFindByGuidQuery(destinationGuid));
							}

							if (c == null || !c.getActive()) {
								// This destination doesn't even exist any more. Add it to the list of removables and remove it.
								toRemove.add(destinationGuid);
								this.removeComputerDestination(cInfo.guid, destinationGuid, set);
								changed = true;
							} else {
								log.trace(
										"config.fix:: Did not remove destination GUID {} from computer config backup set {} ({}) for computer GUID {}.",
										destinationGuid, set.name.getValue(), set.getId(), cInfo.guid);
							}
						}
					}
				}

				// Was the config changed? If so, save it.
				if (changed && !this.simulate) {
					this.db.ensureNoTransaction();
					try {
						this.db.beginTransaction();
						String configXml = serviceConfig.toXmlString();
						this.run(new ComputerUpdateCmd.Builder(cInfo.computerId).configXml(configXml).build(), session);
						this.db.commit();
					} finally {
						this.db.endTransaction();
					}
				}
			} catch (Exception e) {
				log.error("config.fix:: Skipping computer {} config check.", cInfo.guid, e);
			}

			return changed;
		}
	}

	/**
	 * Either log a simulation message or remove and log it
	 * 
	 * @param sourceGuid
	 * @param destinationGuid
	 * @param set
	 */
	private void removeComputerDestination(long sourceGuid, long destinationGuid, BackupSetConfig set) {
		if (this.simulate) {
			log.info(
					"config.fix:: SIMULATION: Would have removed destination GUID {} from computer config backup set {} ({}) for computer GUID {}.",
					destinationGuid, set.name.getValue(), set.getId(), sourceGuid);
		} else {
			set.destinations.remove(destinationGuid);
			CpcHistoryLogger.info(null,
					"config.fix:: Removed destination GUID {} from computer config backup set {} ({}) for computer GUID {}.",
					destinationGuid, set.name.getValue(), set.getId(), sourceGuid);
		}
	}

	/**
	 * 
	 * @param cInfo
	 * @param session
	 * @throws Exception
	 */
	private void createComputerConfig(ComputerInfo cInfo, CoreSession session) throws Exception {
		this.db.ensureNoTransaction();

		try {
			this.db.beginTransaction();
			CoreBridge.find(new UserLockByIdQuery(cInfo.userId));
			CoreBridge.find(new ComputerLockByIdQuery(cInfo.computerId));

			Config config = new Config();
			ServiceConfig sc = new ServiceConfig();

			// Set config date to 1 so the client config wins. This is a bad config put in place temporarily until computer
			// connects to avoid exceptions that may happen if config is null.
			sc.configDateMs.setValue(1L);
			config.setConfigDate(sc.configDateMs.getValue());

			// set the xml, do *not* format xml in DB - it uses more space, is slightly slower, and is not helpful.
			String xml = sc.toXmlString(new ConfigProperties().setFormat(false));
			config.setConfigXml(xml);

			this.run(new ConfigForceUpdateCmd(config), session);

			// Save config id in computer.
			Computer computer = this.db.find(new ComputerFindByIdQuery(cInfo.computerId));
			computer.setConfigId(config.getConfigId()); // set the new config id
			this.db.forceUpdate(new ComputerForceUpdateQuery(computer));
			this.db.commit();
		} catch (Exception e) {
			this.db.rollback();
		} finally {
			this.db.endTransaction();
		}
	}

	/**
	 * 
	 * @param info
	 * @param session
	 * @throws CommandException
	 */
	private boolean checkOrgConfig(OrgInfo info, CoreSession session) throws CommandException {
		if (info.configId == null) {
			// This should never happen because we filter those out in the query
			log.debug("config.fix:: Org has no configId: {}", info.orgId);
			return false;
		}

		final Config config = this.run(new ConfigFindByOrgIdCmd(info.orgId), session);
		ServiceConfig serviceConfig = null;

		boolean changed = false;

		try {
			serviceConfig = config.toServiceConfig();

			// for each backup set, remove destinations that are not offered to this org.
			for (final BackupSetConfig set : serviceConfig.serviceBackup.backup.backupSets.values()) {
				Set<Long> autoStartGuids = new HashSet(set.destinations.keySet());
				// difference returns the autoStartGuids that are not in the destinationGuids
				Set<Long> toRemove = Sets.difference(autoStartGuids, info.destinationGuids);
				for (Long destinationGuid : toRemove) {
					if (this.simulate) {
						log.info(
								"config.fix:: SIMULATION: Would have removed auto-start destination GUID {} from org default config backup set {} ({}) for orgId {}.",
								destinationGuid, set.name.getValue(), set.getId(), info.orgId);
					} else {
						set.destinations.remove(destinationGuid);
						CpcHistoryLogger
								.info(
										null,
										"config.fix:: Removed auto-start destination GUID {} from org default config backup set {} ({}) for orgId {}.",
										destinationGuid, set.name.getValue(), set.getId(), info.orgId);
					}
					changed = true;
				}
			}

			// Was the config changed? If so, save it.
			if (changed && !this.simulate) {

				BackupOrg org = this.db.find(new OrgFindByIdQuery(info.orgId));

				// Don't publish to all child orgs
				// Don't publish to inheriting child orgs
				// We do not need to publish to child orgs because this method will be run for each child org.
				ConfigServices.getInstance().saveConfigForOrgAndPublish(org, serviceConfig, false/* inherit */,
						false/* publishAll */, false/* publishToOrgs */, false/* publishToComputers */,
						session.getUser().getUserId());
			}
		} catch (Exception e) {
			log.error("config.fix:: Skipping org {} config check.", info.orgId, e);
		}

		return changed;
	}

	/**
	 * Finds and returns a map of OrgInfo objects keyed by orgId
	 */
	private static class OrgInfoMapQuery extends FindQuery<Map<Integer, OrgInfo>> {

		public static final String SQL = "" //
				+ "SELECT org_id, inherit_destinations, config_id \n" //
				+ "FROM t_org WHERE org_id > 1                    \n" //
				+ "AND (config_id IS NOT NULL)                    \n" //
				+ "AND type IN ( :orgTypes )";

		private List<String> orgTypes;

		public OrgInfoMapQuery(List<String> orgTypes) {
			this.orgTypes = orgTypes;
		}

		@Override
		public Map<Integer, OrgInfo> query(Session session) throws DBServiceException {
			Map<Integer, OrgInfo> map = Maps.newHashMap();
			SQLQuery query = new SQLQuery(session, SQL);
			query.setParameterList("orgTypes", this.orgTypes);
			List<Object[]> rows = query.list();
			for (Object[] row : rows) {
				OrgInfo orgInfo = new OrgInfo();
				orgInfo.orgId = SQLUtils.getint(row[0]);
				// orgInfo.inheritDestinations = SQLUtils.getboolean(row[1]);
				orgInfo.configId = SQLUtils.getLong(row[2]);
				map.put(orgInfo.orgId, orgInfo);
			}

			return map;
		}
	}

	/**
	 * Finds ComputerInfo objects for a range of computerIds
	 */
	private static class ComputerInfoQuery extends FindQuery<List<ComputerInfo>> {

		private long first;
		private long last;

		public ComputerInfoQuery(long first, long last) {
			super();
			this.first = first;
			this.last = last;
		}

		public static final String SQL = "" //
				+ "SELECT c.computer_id, c.guid, c.user_id, c.config_id FROM t_computer AS c \n" //
				+ "INNER JOIN t_user AS u ON (u.user_id = c.user_id)                         \n" //
				+ "-- Filter out computers in hosted orgs                                    \n" //
				+ "INNER JOIN t_org AS o ON (o.org_id = u.org_id AND o.master_guid IS NULL)  \n" //
				+ "WHERE c.user_id > 1                                                       \n" //
				+ "AND c.type = 'COMPUTER' AND c.parent_computer_id IS NULL                  \n" //
				+ "AND c.computer_id >= :first AND c.computer_id < :last                     \n";

		@Override
		public List<ComputerInfo> query(Session session) throws DBServiceException {
			List<ComputerInfo> list = Lists.newArrayList();
			SQLQuery query = new SQLQuery(session, SQL);
			query.setLong("first", this.first);
			query.setLong("last", this.last);
			List<Object[]> rows = query.list();
			for (Object[] row : rows) {
				ComputerInfo info = new ComputerInfo();
				info.computerId = SQLUtils.getlong(row[0]);
				info.guid = SQLUtils.getlong(row[1]);
				info.userId = SQLUtils.getint(row[2]);
				info.configId = SQLUtils.getLong(row[3]);
				list.add(info);
			}

			return list;
		}

	}

	/**
	 * Finds the largest computerId in the database
	 */
	private static class ComputerIdMaxQuery extends FindQuery<Long> {

		public static final String SQL = "SELECT COALESCE(MAX(computer_id),0) FROM t_computer";

		@Override
		public Long query(Session session) throws DBServiceException {
			SQLQuery query = new SQLQuery(session, SQL);
			Object o = query.uniqueResult();
			return SQLUtils.getlong(o);
		}

	}

	/**
	 * Data object holding information that we need grouped.
	 */
	private static class OrgInfo {

		int orgId;
		// boolean inheritDestinations;
		Long configId;
		Set<Long> destinationGuids;
	}

	/**
	 * Org information needed here.
	 */
	private static class ComputerInfo {

		long computerId;
		long guid;
		int userId;
		Long configId;
	}

}
