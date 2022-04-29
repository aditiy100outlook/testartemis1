package com.code42.archive.maintenance;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import com.code42.backup.manifest.BackupArchiveProperties.ReduceState;
import com.code42.backup.manifest.CompactStats;
import com.code42.backup.manifest.maintenance.ArchiveMaintenanceStats;
import com.code42.backup.manifest.maintenance.ArchiveMaintenanceStats.StepStats;
import com.code42.backup.manifest.maintenance.MaintQueueJob;

/**
 * A bean containing information about archive management jobs.
 * 
 * @see ArchiveMaintenanceStats
 * @see MaintQueueJob
 * @see cppMaintenanceMgmt.vm
 * 
 * @author mharper
 * 
 */
public class ArchiveMaintenanceJobDto {

	/**
	 * A state suitable for UI presentation. The existing back-end state is fragmented across several fields.
	 */
	private enum MaintenanceState {
		UNDEFINED, PENDING, VERIFYING_BLOCKS, STEP_1, STEP_2, STEP_3, STEP_4, STEP_5, COMPLETED, QUEUED, VERIFYING_FILES
	}

	// keys
	// XXX: cannot store guids as longs - they lose precision in javascript
	private final String sourceGuid;
	private final String targetGuid;
	// source computer info
	private final long sourceComputerId;
	// job info
	private final boolean isUserJob;
	private final long totalFiles;
	private final long completedFiles;
	private final MaintenanceState maintenanceState;
	private final boolean isDeepMaintenance;
	private final long durationMs;
	private final Long beforeSizeBytes;
	private final Long removedBytes;
	private final String lastCompactedOn;

	public ArchiveMaintenanceJobDto(long sourceGuid, long targetGuid, long sourceComputerId, boolean isUserJob,
			MaintQueueJob job) {
		// metadata about the archives being maintained
		this.sourceGuid = "" + sourceGuid;
		this.targetGuid = "" + targetGuid;
		this.sourceComputerId = sourceComputerId;
		// metadata about the maintenance job
		this.isUserJob = isUserJob;

		// Protect against NPEs. Stats fields are mutable, and begin their lives as nulls.
		ArchiveMaintenanceStats stats = job.getStats();
		if (stats == null) {
			this.totalFiles = 0;
			this.completedFiles = 0;
			this.maintenanceState = MaintenanceState.PENDING;
			this.isDeepMaintenance = false;
			this.durationMs = 0;
			this.beforeSizeBytes = null;
			this.removedBytes = null;
			this.lastCompactedOn = null;
		} else {
			StepStats currentStepStats = stats.getCurrentStepStats();
			if (currentStepStats == null) {
				this.totalFiles = 0;
				this.completedFiles = 0;
			} else {
				this.totalFiles = currentStepStats.getTotal();
				this.completedFiles = currentStepStats.getTotalNumCompleted();
			}

			// flatten back-end state into a single field
			if (stats.isVerifyingBlockManifest()) {
				this.maintenanceState = MaintenanceState.VERIFYING_BLOCKS;
			} else if (stats.getReduceState() == ReduceState.STEP_1_PRUNE_FILES) {
				this.maintenanceState = MaintenanceState.STEP_1;
			} else if (stats.getReduceState() == ReduceState.STEP_2_PRUNE_BLOCKS) {
				this.maintenanceState = MaintenanceState.STEP_2;
			} else if (stats.getReduceState() == ReduceState.STEP_3_COMPACT_FILES) {
				this.maintenanceState = MaintenanceState.STEP_3;
			} else if (stats.getReduceState() == ReduceState.STEP_4_COMPACT_BLOCKS) {
				this.maintenanceState = MaintenanceState.STEP_4;
			} else if (stats.getReduceState() == ReduceState.STEP_5_VERIFY_FILES) {
				this.maintenanceState = MaintenanceState.STEP_5;
			} else if (stats.getReduceState() == ReduceState.OFF) {
				this.maintenanceState = MaintenanceState.COMPLETED;
			} else if (stats.getReduceState() == ReduceState.QUEUED) {
				this.maintenanceState = MaintenanceState.QUEUED;
			} else if (stats.isRunning()) {
				this.maintenanceState = MaintenanceState.VERIFYING_FILES;
			} else {
				this.maintenanceState = MaintenanceState.UNDEFINED;
			}

			this.isDeepMaintenance = stats.isDeepMaintenance();
			this.durationMs = stats.getDuration();

			// stats about data compaction
			CompactStats compactStats = stats.getCompactStats();
			this.beforeSizeBytes = (compactStats != null) ? compactStats.getTotalBytes() : null;
			this.removedBytes = (compactStats != null) ? compactStats.getBytesRemoved() : null;
			this.lastCompactedOn = (compactStats != null) ? ISODateTimeFormat.dateTime().print(
					new DateTime(compactStats.getLastCompactTimestamp())) : null;
		}

	}

	public String getSourceGuid() {
		return this.sourceGuid;
	}

	public String getTargetGuid() {
		return this.targetGuid;
	}

	public long getSourceComputerId() {
		return this.sourceComputerId;
	}

	/** true if this is a user-initiated job. Otherwise it's a system-initiated (automatic) job. */
	public boolean isUserJob() {
		return this.isUserJob;
	}

	/** total number of files to process */
	public long getTotalFiles() {
		return this.totalFiles;
	}

	/** number of files we've processed */
	public long getCompletedFiles() {
		return this.completedFiles;
	}

	public MaintenanceState getMaintenanceState() {
		return this.maintenanceState;
	}

	public boolean isDeepMaintenance() {
		return this.isDeepMaintenance;
	}

	/** How long the maintenance took. Only useful if we're done. */
	public long getDurationMs() {
		return this.durationMs;
	}

	/** Pre-compaction size. May be null if job is in progress. */
	public Long getBeforeSizeBytes() {
		return this.beforeSizeBytes;
	}

	/** Post-compaction size. May be null if job is in progress. */
	public Long getRemovedBytes() {
		return this.removedBytes;
	}

	/** When last compacted. May be null if job is in progress. */
	public String getLastCompactedOn() {
		return this.lastCompactedOn;
	}

}
