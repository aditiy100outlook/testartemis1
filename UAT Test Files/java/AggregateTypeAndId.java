package com.code42.stats;

import java.util.Map;

import com.google.common.base.Preconditions;

/**
 * A convenience class intended to wrap an id and an id type together, for REST resources that expect exactly one of
 * several types as a parameter.
 * 
 * @author mharper
 */
public class AggregateTypeAndId {

	// required
	private final AggregateType type;

	// exactly one of these will be non-null
	private final Integer userId;
	private final Integer orgId;
	private final Long computerId;
	private final Integer mountPointId;
	private final Integer serverId;
	private final Integer destinationId;

	// see static builders
	private AggregateTypeAndId(AggregateType type, Integer userId, Integer orgId, Long computerId, Integer mountPointId,
			Integer serverId, Integer destinationId) {
		this.type = type;
		this.userId = userId;
		this.orgId = orgId;
		this.computerId = computerId;
		this.mountPointId = mountPointId;
		this.serverId = serverId;
		this.destinationId = destinationId;
	}

	/**
	 * Translates API request params into an AggregateTypeAndId object, for use in rest resources.
	 */
	public static AggregateTypeAndId fromRequestParams(Map<String, Object> params) {
		// required
		AggregateType type = null;
		// optional
		Integer userId = null;
		Integer orgId = null;
		Long computerId = null;
		Integer mountPointId = null;
		Integer serverId = null;
		Integer destinationId = null;

		int idCount = 0;
		if (params.containsKey("orgId")) {
			type = AggregateType.ORG;
			orgId = Integer.parseInt((String) params.get("orgId"));
			idCount++;
		}
		if (params.containsKey("userId")) {
			type = AggregateType.USER;
			userId = Integer.parseInt((String) params.get("userId"));
			idCount++;
		}
		if (params.containsKey("computerId")) {
			type = AggregateType.COMPUTER;
			computerId = Long.parseLong((String) params.get("computerId"));
			idCount++;
		}
		if (params.containsKey("serverId")) {
			type = AggregateType.SERVER;
			serverId = Integer.parseInt((String) params.get("serverId"));
			idCount++;
		}
		if (params.containsKey("mountPointId")) {
			type = AggregateType.MOUNT_POINT;
			mountPointId = Integer.parseInt((String) params.get("mountPointId"));
			idCount++;
		}
		if (params.containsKey("destinationId")) {
			type = AggregateType.DESTINATION;
			destinationId = Integer.parseInt((String) params.get("destinationId"));
			idCount++;
		}

		Preconditions.checkState(idCount == 1, "Must provide exactly one id parameter, but there are " + idCount);
		if (type == null) {
			throw new IllegalStateException("Type was not initialized");
		}
		return new AggregateTypeAndId(type, userId, orgId, computerId, mountPointId, serverId, destinationId);
	}

	public static AggregateTypeAndId fromUserId(int userId) {
		return new AggregateTypeAndId(AggregateType.USER, userId, null, null, null, null, null);
	}

	public static AggregateTypeAndId fromOrgId(int orgId) {
		return new AggregateTypeAndId(AggregateType.ORG, null, orgId, null, null, null, null);
	}

	public static AggregateTypeAndId fromComputerId(long computerId) {
		return new AggregateTypeAndId(AggregateType.COMPUTER, null, null, computerId, null, null, null);
	}

	public static AggregateTypeAndId fromMountPointId(int mountPointId) {
		return new AggregateTypeAndId(AggregateType.MOUNT_POINT, null, null, null, mountPointId, null, null);
	}

	public static AggregateTypeAndId fromServerId(int serverId) {
		return new AggregateTypeAndId(AggregateType.SERVER, null, null, null, null, serverId, null);
	}

	public static AggregateTypeAndId fromDestinationId(int destinationId) {
		return new AggregateTypeAndId(AggregateType.DESTINATION, null, null, null, null, null, destinationId);
	}

	public AggregateType getType() {
		return this.type;
	}

	public Integer getUserId() {
		return this.userId;
	}

	public Integer getOrgId() {
		return this.orgId;
	}

	public Long getComputerId() {
		return this.computerId;
	}

	public Integer getMountPointId() {
		return this.mountPointId;
	}

	public Integer getServerId() {
		return this.serverId;
	}

	public Integer getDestinationId() {
		return this.destinationId;
	}
}
