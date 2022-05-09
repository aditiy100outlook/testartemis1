package com.code42.org;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.backup42.CpcConstants;
import com.backup42.common.ComputerType;
import com.backup42.server.MasterServices;
import com.code42.computer.ComputerSso;
import com.code42.computer.ComputerSsoFindMultipleByGuidCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.hierarchy.AggregateHierarchyStats;
import com.code42.core.hierarchy.HierarchyNotFoundException;
import com.code42.core.hierarchy.IHierarchyService;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.utils.Pair;
import com.google.inject.Inject;

/**
 * Command to find the counts of the org's hierarchy
 * 
 * @author jlundberg
 */
public class OrgConfigInheritanceStatsFindCmd extends DBCmd<List<AggregateHierarchyStats>> {

	/* ================= Dependencies ================= */
	@Inject
	private IHierarchyService hierarchy;

	private static final Logger log = LoggerFactory.getLogger(OrgConfigInheritanceStatsFindCmd.class);

	private int orgId;

	public OrgConfigInheritanceStatsFindCmd(int orgId) {
		this.orgId = orgId;
	}

	@Override
	public List<AggregateHierarchyStats> exec(CoreSession session) throws CommandException {
		List<AggregateHierarchyStats> list = new ArrayList<AggregateHierarchyStats>();
		list.add(new AggregateHierarchyStats(0, 0, 0, 0, 0)); // All Stats
		list.add(new AggregateHierarchyStats(0, 0, 0, 0, 0)); // Inherited Stats

		Pair<AggregateHierarchyStats, AggregateHierarchyStats> totalStats = new Pair<AggregateHierarchyStats, AggregateHierarchyStats>(
				list.get(0), list.get(1));

		if (this.orgId == CpcConstants.Orgs.ADMIN_ID) {
			Set<Integer> orgIds = this.hierarchy.getChildOrgs();

			for (Integer orgId : orgIds) {
				if (orgId != CpcConstants.Orgs.ADMIN_ID) {
					this.findStatsForBaseOrg(orgId, totalStats, session);
				}
			}

			return list;
		} else {
			this.findStatsForBaseOrg(this.orgId, totalStats, session);
			return list;
		}
	}

	private void findStatsForBaseOrg(int orgId, Pair<AggregateHierarchyStats, AggregateHierarchyStats> totalStats,
			CoreSession session) throws CommandException {
		try {
			int orgCount;
			int deviceCount;

			MasterServices masterServices = MasterServices.getInstance();

			if (masterServices.isHostedOrg(orgId)) {
				totalStats.getOne().addOrgs(1);
				totalStats.getTwo().addOrgs(1);
				return;
			}

			{
				orgCount = 1;
				deviceCount = this.getDeviceCountOfOrg(orgId, session);

				Pair<Integer, Integer> allStats = this.recursiveHelperForAllStats(orgId, true, session);
				orgCount += allStats.getOne();
				deviceCount += allStats.getTwo();
				totalStats.getOne().addOrgs(orgCount);
				totalStats.getOne().addDevices(deviceCount);
			}

			{
				orgCount = 0;
				deviceCount = 0;
				BackupOrg org = this.runtime.run(new OrgFindByIdCmd(orgId), session);
				if (!org.getCustomConfig()) {
					orgCount = 1;
					deviceCount = this.getDeviceCountOfOrg(orgId, session);
				}

				Pair<Integer, Integer> stats = this.recursiveHelperForInheritedStats(orgId, true, session);
				orgCount += stats.getOne();
				deviceCount += stats.getTwo();

				totalStats.getTwo().addOrgs(orgCount);
				totalStats.getTwo().addDevices(deviceCount);
			}

			return;
		} catch (HierarchyNotFoundException e) {
			log.error("org not found");
			throw new CommandException("Not Found");
		}
	}

	private Pair<Integer, Integer> recursiveHelperForAllStats(int orgId, boolean first, CoreSession session)
			throws HierarchyNotFoundException, CommandException {
		int orgCount = 0;
		int deviceCount = 0;

		if (first) {
			Set<Integer> childOrgs = this.hierarchy.getChildOrgs(orgId);
			for (Integer childOrgId : childOrgs) {
				Pair<Integer, Integer> childStats = this.recursiveHelperForAllStats(childOrgId, false, session);
				orgCount += childStats.getOne();
				deviceCount += childStats.getTwo();
			}
		} else {
			orgCount++;
			if (!MasterServices.getInstance().isHostedOrg(orgId)) {
				deviceCount = this.getDeviceCountOfOrg(orgId, session);

				Set<Integer> childOrgs = this.hierarchy.getChildOrgs(orgId);
				for (Integer childOrgId : childOrgs) {
					Pair<Integer, Integer> childStats = this.recursiveHelperForAllStats(childOrgId, false, session);
					orgCount += childStats.getOne();
					deviceCount += childStats.getTwo();
				}
			}
		}
		return new Pair<Integer, Integer>(orgCount, deviceCount);
	}

	private Pair<Integer, Integer> recursiveHelperForInheritedStats(int orgId, boolean first, CoreSession session)
			throws HierarchyNotFoundException, CommandException {
		int orgCount = 0;
		int deviceCount = 0;

		if (first) {
			Set<Integer> childOrgs = this.hierarchy.getChildOrgs(orgId);
			for (Integer childOrgId : childOrgs) {
				Pair<Integer, Integer> childStats = this.recursiveHelperForInheritedStats(childOrgId, false, session);
				orgCount += childStats.getOne();
				deviceCount += childStats.getTwo();
			}
		} else {
			BackupOrg org = this.runtime.run(new OrgFindByIdCmd(orgId), session);
			if (!org.getCustomConfig()) {
				orgCount++;
				if (!MasterServices.getInstance().isHostedOrg(org)) {
					deviceCount = this.hierarchy.getGuidsForOrg(orgId).size();

					Set<Integer> childOrgs = this.hierarchy.getChildOrgs(orgId);
					for (Integer childOrgId : childOrgs) {
						Pair<Integer, Integer> childStats = this.recursiveHelperForInheritedStats(childOrgId, false, session);
						orgCount += childStats.getOne();
						deviceCount += childStats.getTwo();
					}
				}
			}
		}

		return new Pair<Integer, Integer>(orgCount, deviceCount);
	}

	private int getDeviceCountOfOrg(int orgId, CoreSession session) throws CommandException, HierarchyNotFoundException {
		int deviceCount = 0;
		Map<Long, ComputerSso> orgComputers = this.runtime.run(new ComputerSsoFindMultipleByGuidCmd(this.hierarchy
				.getGuidsForOrg(orgId)), session);
		for (ComputerSso computer : orgComputers.values()) {
			if (computer.getType() == ComputerType.COMPUTER) {
				deviceCount++;
			}
		}
		return deviceCount;
	}
}
