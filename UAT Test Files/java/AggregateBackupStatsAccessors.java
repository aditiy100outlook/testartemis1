package com.code42.stats;

import java.io.Serializable;

import com.google.common.base.Function;

public class AggregateBackupStatsAccessors {

	public static class GetArchiveBytes implements Function<AggregateBackupStats, Long>, Serializable {

		private static final long serialVersionUID = -3859660310337827790L;

		public Long apply(AggregateBackupStats stats) {
			return stats.getArchiveBytes();
		}
	}

	public static class GetBillableBytes implements Function<AggregateBackupStats, Long>, Serializable {

		private static final long serialVersionUID = -6975279670133084894L;

		public Long apply(AggregateBackupStats stats) {
			return stats.getBillableBytes();
		}
	}

	public static class GetBackupSessionCount implements Function<AggregateBackupStats, Integer>, Serializable {

		private static final long serialVersionUID = -6750457012615978307L;

		public Integer apply(AggregateBackupStats stats) {
			return stats.getBackupSessionCount();
		}
	}

	public static class GetPercentComplete implements Function<AggregateBackupStats, Double>, Serializable {

		private static final long serialVersionUID = 5285541383947812034L;

		public Double apply(AggregateBackupStats stats) {
			return stats.getPercentComplete();
		}
	}

	public static class GetSelectedBytes implements Function<AggregateBackupStats, Long>, Serializable {

		private static final long serialVersionUID = -1220462699528735309L;

		public Long apply(AggregateBackupStats stats) {
			return stats.getSelectedBytes();
		}
	}

}
