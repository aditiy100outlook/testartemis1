package com.code42.archive.maintenance;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import com.backup42.app.cpc.backup.CPCArchiveMaintenanceManager;
import com.backup42.common.perm.C42PermissionPro;
import com.code42.backup.manifest.maintenance.IArchiveMaintenanceQueue;
import com.code42.backup.manifest.maintenance.MaintQueueJob;
import com.code42.computer.ComputerSso;
import com.code42.computer.ComputerSsoFindByGuidCmd;
import com.code42.core.CommandException;
import com.code42.core.archive.IArchiveService;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

/**
 * Find the in-memory archive maintenance state on the current server, filtering by job state.
 * 
 * @author mharper
 */
public class ArchiveMaintenanceFindCmd extends DBCmd<List<ArchiveMaintenanceJobDto>> {

	// private final static Logger log = LoggerFactory.getLogger(ArchiveMaintenanceFindCmd.class);

	public enum JobState {
		COMPLETED, PENDING, CURRENT;
	}

	private final int limit;
	private final int offset;
	private final EnumSet<JobState> stateFilters;

	public ArchiveMaintenanceFindCmd(EnumSet<JobState> stateFilters, int offset, int limit) {
		super();
		this.offset = offset;
		this.limit = limit;
		this.stateFilters = stateFilters;
	}

	/**
	 * Get an archive maintenance manager.
	 */
	static CPCArchiveMaintenanceManager getManager() throws CommandException {
		CPCArchiveMaintenanceManager manager = null;
		try {
			IArchiveService backup = CoreBridge.getArchiveService();
			if (backup != null) {
				manager = (CPCArchiveMaintenanceManager) backup.getMaintenanceManager();
			} else {
				throw new CommandException("CPCentralServices has a null CPCBackupController");
			}
			if (manager == null) {
				throw new CommandException("CPCentralServices has a null CPCArchiveMaintenanceManager");
			}
		} catch (CommandException e) {
			throw e;
		} catch (Exception e) {
			throw new CommandException("Could not get the CPCArchiveMaintenanceManager", e);
		}
		return manager;
	}

	/**
	 * Build DTO from a job in memory.
	 * 
	 * This is really slow. However, we need more information than is provided by SSOs, and we need to display a mount
	 * point name. There is an opportunity for optimization here, but this command may not be run enough to warrant it.
	 */
	private ArchiveMaintenanceJobDto buildDto(CoreSession session, MaintQueueJob job, AnnotatedMaintQueueJob.Type type)
			throws CommandException {
		long sourceGuid = job.getSourceGuid();
		long targetGuid = job.getTargetGuid();
		boolean isUserJob = (type == AnnotatedMaintQueueJob.Type.USER);

		ComputerSso sourceComputer = this.run(new ComputerSsoFindByGuidCmd(sourceGuid), session);
		if (sourceComputer == null) {
			throw new CommandException("Invalid Source Computer GUID: {}", sourceGuid);
		}
		return new ArchiveMaintenanceJobDto(sourceGuid, targetGuid, sourceComputer.getComputerId(), isUserJob, job);
	}

	@Override
	public List<ArchiveMaintenanceJobDto> exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.ARCHIVE_MAINTENANCE);

		// validate paging and offset parameters
		if (this.offset < 0 || this.limit < 0 || this.limit > 1000) {
			throw new CommandException("Invalid parameters for cmd: offset=" + this.offset + ", limit=" + this.limit);
		}

		// get an archive maintenance manager
		CPCArchiveMaintenanceManager manager = getManager();

		// build a sorted list of jobs
		List<AnnotatedMaintQueueJob> allJobs = Lists.newArrayList();
		if (this.stateFilters.contains(JobState.COMPLETED)) {
			allJobs.addAll(AnnotatedMaintQueueJob.buildAnnotatedJobs(manager, JobState.COMPLETED));
		}
		if (this.stateFilters.contains(JobState.CURRENT)) {
			allJobs.addAll(AnnotatedMaintQueueJob.buildAnnotatedJobs(manager, JobState.CURRENT));
		}
		if (this.stateFilters.contains(JobState.PENDING)) {
			allJobs.addAll(AnnotatedMaintQueueJob.buildAnnotatedJobs(manager, JobState.PENDING));
		}

		// sort first by job type, then by completed date, then by original position in queue
		List<AnnotatedMaintQueueJob> sortedJobs = AnnotatedMaintQueueJob.byJobState //
				.compound(AnnotatedMaintQueueJob.byLastCompactedDate.reverse()) //
				.compound(AnnotatedMaintQueueJob.byPositionInQueue) //
				.sortedCopy(allJobs);

		// extract a page
		int min = this.offset, max = this.offset + this.limit;
		if (max > sortedJobs.size()) {
			max = sortedJobs.size();
		}
		if (min > max) {
			min = max;
		}
		List<AnnotatedMaintQueueJob> sortedPageOfJobs = sortedJobs.subList(min, max);

		// build DTOs
		List<ArchiveMaintenanceJobDto> jobDtos = Lists.newArrayList();
		for (AnnotatedMaintQueueJob job : sortedPageOfJobs) {
			jobDtos.add(this.buildDto(session, job.getJob(), job.getType()));
		}
		return jobDtos;
	}

	/**
	 * Bundle up a job and a type, so that we can combine them in one list.
	 */
	private static class AnnotatedMaintQueueJob {

		/** Differentiate between a system-initiated job and a user-initiated job */
		private enum Type {
			USER, SYSTEM
		}

		private final MaintQueueJob job;
		private final Type type;
		private final JobState state;
		private final int positionInQueue;

		AnnotatedMaintQueueJob(MaintQueueJob job, Type type, JobState state, int positionInQueue) {
			this.job = job;
			this.type = type;
			this.state = state;
			this.positionInQueue = positionInQueue;
		}

		public MaintQueueJob getJob() {
			return this.job;
		}

		public Type getType() {
			return this.type;
		}

		public JobState getState() {
			return this.state;
		}

		public int getPositionInQueue() {
			return this.positionInQueue;
		}

		/**
		 * Sort by the last compacted date. Only useful for completed jobs.
		 */
		private static Ordering<AnnotatedMaintQueueJob> byLastCompactedDate = new Ordering<AnnotatedMaintQueueJob>() {

			@Override
			public int compare(AnnotatedMaintQueueJob j1, AnnotatedMaintQueueJob j2) {
				// protect against null pointers for jobs that are pending or running
				long j1Ts = Long.MAX_VALUE, j2Ts = Long.MAX_VALUE;
				if (j1.getJob().getStats() != null && j1.getJob().getStats().getCompactStats() != null) {
					j1Ts = j1.getJob().getStats().getCompactStats().getLastCompactTimestamp();
				}
				if (j2.getJob().getStats() != null && j2.getJob().getStats().getCompactStats() != null) {
					j2Ts = j2.getJob().getStats().getCompactStats().getLastCompactTimestamp();
				}
				return Longs.compare(j1Ts, j2Ts);
			}
		};

		/**
		 * Sort by original ordering in the queue.
		 */
		private static Ordering<AnnotatedMaintQueueJob> byPositionInQueue = new Ordering<AnnotatedMaintQueueJob>() {

			@Override
			public int compare(AnnotatedMaintQueueJob j1, AnnotatedMaintQueueJob j2) {
				return Ints.compare(j1.getPositionInQueue(), j2.getPositionInQueue());
			}
		};

		/**
		 * Sort by job state: completed, then running, then queued.
		 */
		private static Ordering<AnnotatedMaintQueueJob> byJobState = new Ordering<AnnotatedMaintQueueJob>() {

			private int score(JobState state) {
				if (state == JobState.COMPLETED) {
					return 0;
				} else if (state == JobState.CURRENT) {
					return 1;
				} else {
					return 2;
				}
			}

			@Override
			public int compare(AnnotatedMaintQueueJob j1, AnnotatedMaintQueueJob j2) {
				return Ints.compare(this.score(j1.getState()), this.score(j2.getState()));
			}
		};

		/** Minor helper method. */
		private static Collection<AnnotatedMaintQueueJob> buildAnnotatedJobs(CPCArchiveMaintenanceManager manager,
				JobState state) throws CommandException {
			Collection<AnnotatedMaintQueueJob> annotatedJobs = Lists.newArrayList();
			IArchiveMaintenanceQueue systemQueue = manager.getSystemQueue();
			IArchiveMaintenanceQueue userQueue = manager.getUserQueue();

			List<MaintQueueJob> completedSystemJobs = null;
			List<MaintQueueJob> completedUserJobs = null;
			if (state == JobState.COMPLETED) {
				completedSystemJobs = systemQueue.getCompletedJobs();
				completedUserJobs = userQueue.getCompletedJobs();
			} else if (state == JobState.CURRENT) {
				completedSystemJobs = systemQueue.getCurrentJobs();
				completedUserJobs = userQueue.getCurrentJobs();
			} else if (state == JobState.PENDING) {
				completedSystemJobs = systemQueue.getPendingJobs();
				completedUserJobs = userQueue.getPendingJobs();
			} else {
				throw new CommandException("JobState parameter must not be null");
			}

			int i = 0;
			for (MaintQueueJob job : completedSystemJobs) {
				annotatedJobs.add(new AnnotatedMaintQueueJob(job, AnnotatedMaintQueueJob.Type.SYSTEM, state, i));
				i += 1;
			}
			i = 0;
			for (MaintQueueJob job : completedUserJobs) {
				annotatedJobs.add(new AnnotatedMaintQueueJob(job, AnnotatedMaintQueueJob.Type.USER, state, i));
				i += 1;
			}
			return annotatedJobs;
		}

	}
}