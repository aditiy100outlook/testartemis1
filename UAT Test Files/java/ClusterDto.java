package com.code42.computer;

import com.code42.core.IEnvironment;

/**
 * A simple class to contain information about this cluster.
 */
public class ClusterDto {

	// Cluster Info
	private int myClusterId;
	private long myClusterComputerId;
	private long myClusterGuid;
	private boolean isPrimary;
	private boolean isMaster;

	// cluster type
	private boolean isCPCentral;
	private boolean isBusinessCluster;
	private boolean isConsumerCluster;

	public ClusterDto(IEnvironment env) {

		this.myClusterId = env.getMyClusterId();
		this.myClusterComputerId = env.getMyClusterComputerId();
		this.myClusterGuid = env.getMyClusterGuid();
		this.isPrimary = env.isPrimary();
		this.isMaster = env.isMaster();
		this.isCPCentral = env.isCpCentral();
		this.isBusinessCluster = env.isBusinessCluster();
		this.isConsumerCluster = env.isConsumerCluster();
	}

	public int getMyClusterServerId() {
		return this.myClusterId;
	}

	public long getMyClusterGuid() {
		return this.myClusterGuid;
	}

	public long getMyClusterComputerId() {
		return this.myClusterComputerId;
	}

	public boolean isPrimary() {
		return this.isPrimary;
	}

	public boolean isMaster() {
		return this.isMaster;
	}

	public boolean isCPCentral() {
		return this.isCPCentral;
	}

	public boolean isBusinessCluster() {
		return this.isBusinessCluster;
	}

	public boolean isConsumerCluster() {
		return this.isConsumerCluster;
	}

	@Override
	public String toString() {

		StringBuffer buffer = new StringBuffer();
		buffer.append("ClusterDto[");
		buffer.append(", myClusterId=").append(this.myClusterId);
		buffer.append(", myClusterComputerId=").append(this.myClusterComputerId);
		buffer.append(", myClusterGuid=").append(this.myClusterGuid);
		buffer.append(", isPrimary=").append(this.isPrimary);
		buffer.append(", isMaster=").append(this.isMaster);
		buffer.append(", isCPCentral=").append(this.isCPCentral);
		buffer.append(", isBusinessCluster=").append(this.isBusinessCluster);
		buffer.append(", isConsumerCluster=").append(this.isConsumerCluster);
		buffer.append("]");

		return buffer.toString();
	}
}
