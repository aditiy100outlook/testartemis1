/*
 * Created on Feb 15, 2011 by Tony Lindquist <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.server.mount;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

public class MountPointFindCountSummaryCmd extends DBCmd<MountPointCountSummary> {

	// Injected Services
	// private IPlatformService platformService;

	// @Inject
	// public void setPlatformService(IPlatformService platformService) {
	// this.platformService = platformService;
	// }

	@Override
	public MountPointCountSummary exec(CoreSession session) throws CommandException {
		// Map<Integer, MountPointCounts> serverCounts = new HashMap<Integer, MountPointCounts>();
		// Map<Integer, MountPointCounts> mountPointCounts = new HashMap<Integer, MountPointCounts>();
		//
		// // Get the counts
		// BackupUserDataProvider udp = BackupUser.getDataProvider(false);
		// Map<Integer, Integer> sessionCounts = CPCentralServices.getInstance().getBackup()
		// .getNumActiveBackupSessionsByMountPointId();
		// Map<Integer, Integer> userCounts = udp.findCountsByMountPointId(true);
		// Map<Integer, Integer> userAssignedCounts = udp.findCountsByMountPointId(false);
		// Map<Integer, Integer> computerCounts = ComputerDataProvider.findCountsByMountPointId(true);
		//
		// // Put them all into one map keyed by mountPointId
		// List<MountPoint> mountPoints = CoreBridge.find(new MountPointFindByClusterQuery(CoreBridge.getEnvironment()
		// .getMyClusterServerIdTemp()));
		//
		// // DCL #12227 - We care about MountPoint.sizeAvailable now and that's not stored in the DB; it has to be
		// // retrieved by the ServerManager.
		// this.platformService.loadDeviceData(mountPoints);
		//
		// for (MountPoint mp : mountPoints) {
		// int serverId = mp.getServerId();
		// MountPointCounts sCounts = serverCounts.get(serverId);
		// if (sCounts == null) {
		// sCounts = new MountPointCounts(serverId);
		// serverCounts.put(serverId, sCounts);
		// }
		// MountPointCounts counts = new MountPointCounts(serverId, mp.getMountPointId());
		// counts.sessionCount = LangUtils.coalesceIntegers(sessionCounts.get(mp.getMountPointId()), 0);
		// sCounts.sessionCount += counts.sessionCount;
		// counts.userCount = LangUtils.coalesceIntegers(userCounts.get(mp.getMountPointId()), 0);
		// sCounts.userCount += counts.userCount;
		// counts.userAssignedCount = LangUtils.coalesceIntegers(userAssignedCounts.get(mp.getMountPointId()), 0);
		// sCounts.userAssignedCount += counts.userAssignedCount;
		// counts.computerCount = LangUtils.coalesceIntegers(computerCounts.get(mp.getMountPointId()), 0);
		// sCounts.computerCount += counts.computerCount;
		//
		// // try to pull size available from node stats
		// Long sizeAvailableFromStats = null;
		// final NodeStatsService nodeStatsService = CPCentralServices.getInstance().getPeer().getNodeStatsService();
		// if (nodeStatsService != null) {
		// final Map<Integer, MountStatsMessagePacket> clusterMounts = nodeStatsService.getMountStatsForCluster();
		// final MountStatsMessagePacket mountStats = clusterMounts.get(mp.getMountPointId());
		// if (mountStats != null) {
		// sizeAvailableFromStats = mountStats.getAvailableBytes();
		// }
		// }
		//
		// counts.sizeAvailable = LangUtils.coalesceLongs(sizeAvailableFromStats, mp.getSizeAvailable(), 0L);
		// sCounts.sizeAvailable += counts.sizeAvailable;
		//
		// counts.acceptingNewUsers = mp.getBalanceNewUsers();
		// sCounts.acceptingNewUsers = mp.getBalanceNewUsers();
		//
		// mountPointCounts.put(mp.getMountPointId(), counts);
		// }
		//
		// return new MountPointCountSummary(serverCounts, mountPointCounts);
		return null;
	}

}
