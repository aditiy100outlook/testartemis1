package com.code42.computer;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.backup42.common.OrgType;
import com.code42.computer.ComputerDtoViewUtil.SortDir;
import com.code42.computer.ComputerDtoViewUtil.SortKey;
import com.code42.core.BuilderException;
import com.code42.utils.SystemProperties;

/**
 * Builds the data needed by both the command and the query. S=Subclass, T=return type for build method
 */
public abstract class ComputerDtoFindByCriteriaBuilder<S, T> extends ComputerDtoFindBuilder<S, T> {

	final static String ASC = "ASC";
	final static String DESC = "DESC";
	final static Map<SortKey, String> SORT_KEYS = new HashMap<SortKey, String>();
	final static Set<SortKey> FCU_SORT_KEYS = new HashSet<SortKey>();
	final static Set<SortKey> USER_SORT_KEYS = new HashSet<SortKey>();

	static {
		SORT_KEYS.put(SortKey.name, "c.name");
		SORT_KEYS.put(SortKey.userId, "c.user_id");
		SORT_KEYS.put(SortKey.os, "c.os_name");
		SORT_KEYS.put(SortKey.orgId, "u.org_id");
		SORT_KEYS.put(SortKey.username, "u.username");
		SORT_KEYS.put(SortKey.selectedBytes, "selected_bytes");
		SORT_KEYS.put(SortKey.percentComplete, "percent_complete");
		SORT_KEYS.put(SortKey.archiveBytes, "archive_bytes");
		SORT_KEYS.put(SortKey.billableBytes, "billable_bytes");
		SORT_KEYS.put(SortKey.lastBackup, "last_activity");
		SORT_KEYS.put(SortKey.lastCompletedBackup, "last_completed_backup");
		SORT_KEYS.put(SortKey.lastConnected, "c.last_connected");

		// Keys that require the FriendComputerUsage table
		FCU_SORT_KEYS.add(SortKey.selectedBytes);
		FCU_SORT_KEYS.add(SortKey.archiveBytes);
		FCU_SORT_KEYS.add(SortKey.billableBytes);
		FCU_SORT_KEYS.add(SortKey.percentComplete);
		FCU_SORT_KEYS.add(SortKey.lastBackup);
		FCU_SORT_KEYS.add(SortKey.lastCompletedBackup);

		// Keys that require the User table
		USER_SORT_KEYS.add(SortKey.username);
		USER_SORT_KEYS.add(SortKey.orgId);
	}

	Integer userId = null;
	Collection<Integer> orgIds = null;
	Set<OrgType> orgTypes = null;
	Long targetComputerGuid = null;
	String search = null;
	Boolean active = null;
	Boolean blocked = null;
	boolean filterHosted = false;
	Boolean alerted = null;
	SortKey sortKey = SortKey.userId;
	SortDir sortDir = null;
	int offset = 0;
	int limit = 100; // maximum number of rows to return; default to 100
	boolean obeyQueryLimit = false;
	Boolean exportAll = Boolean.FALSE;

	protected boolean fcuJoinRequired = false;
	private boolean userJoinRequired = false;
	private boolean orgJoinRequired = true;

	public ComputerDtoFindByCriteriaBuilder() {
	}

	public S userId(Integer userId) {
		this.userId = userId;
		this.userJoinRequired = true;
		return (S) this;
	}

	public S orgId(int orgId) {
		if (this.orgIds == null) {
			this.orgIds = new HashSet<Integer>();
		}
		this.orgIds.add(orgId);
		this.userJoinRequired = true;
		return (S) this;
	}

	S orgTypes(Set<OrgType> orgTypes) {
		this.orgTypes = orgTypes;
		this.userJoinRequired = true;
		this.orgJoinRequired = true;
		return (S) this;
	}

	public S orgIds(Collection<Integer> orgIds) {
		if (orgIds != null) {
			for (Integer orgId : orgIds) {
				if (orgId != null) {
					this.orgId(orgId);
				}
			}
		}
		return (S) this;
	}

	/** Flexibly search on computer name or start of GUID */
	public S search(String q) {
		this.search = q;
		return (S) this;
	}

	public S targetComputerGuid(Long guid) {
		this.targetComputerGuid = guid;
		return (S) this;
	}

	public S alerted(Boolean flag) {
		this.alerted = flag;
		this.fcuJoinRequired = true;
		return (S) this;
	}

	public S active(Boolean active) {
		this.active = active;
		return (S) this;
	}

	public S filterHosted() {
		this.filterHosted = true;
		this.orgJoinRequired = true;
		return (S) this;
	}

	public boolean isFilterHosted() {
		return this.filterHosted;
	}

	public S blocked(Boolean blocked) {
		this.blocked = blocked;
		return (S) this;
	}

	public S sortKey(String sortKey) {
		if (sortKey != null) {
			this.sortKey = SortKey.valueOf(sortKey);
			if (FCU_SORT_KEYS.contains(this.sortKey)) {
				this.fcuJoinRequired = true;
			}
			if (USER_SORT_KEYS.contains(this.sortKey)) {
				this.userJoinRequired = true;
			}
		}
		return (S) this;
	}

	/**
	 * Valid values are "asc" or "desc" (case insensitive)
	 */
	public S sortDir(String sortDir) {
		if (sortDir != null) {
			this.sortDir = SortDir.valueOf(sortDir.toUpperCase());
		}
		return (S) this;
	}

	public S offset(Integer offset) {
		this.offset = (offset == null ? 0 : offset);
		return (S) this;
	}

	public S limit(Integer limit) {
		this.limit = (limit == null ? 0 : limit);
		return (S) this;
	}

	public S obeyQueryLimit() {
		this.obeyQueryLimit(true);
		return (S) this;
	}

	public S obeyQueryLimit(boolean flag) {
		this.obeyQueryLimit = flag;
		return (S) this;
	}

	public S exportAll() {
		return this.exportAll(Boolean.TRUE);
	}

	public S exportAll(boolean flag) {
		this.exportAll = flag;
		return (S) this;
	}

	// ======================================================
	// Getters
	// ======================================================

	public boolean isAscending() {
		// ascending is the default
		return this.sortDir == null || this.sortDir == SortDir.ASC;
	}

	public boolean isDescending() {
		return this.sortDir != null && this.sortDir == SortDir.DESC;
	}

	public String getSortColumn() {
		return SORT_KEYS.get(this.sortKey);
	}

	public boolean isFcuJoinRequired() {
		return this.fcuJoinRequired;
	}

	public boolean isUserJoinRequired() {
		return this.userJoinRequired;
	}

	public boolean isOrgJoinRequired() {
		return this.orgJoinRequired;
	}

	public Integer getUserId() {
		return this.userId;
	}

	public Collection<Integer> getOrgIds() {
		return this.orgIds;
	}

	Set<OrgType> getOrgTypes() {
		return this.orgTypes;
	}

	public Long getTargetComputerGuid() {
		return this.targetComputerGuid;
	}

	public String getSearch() {
		return this.search;
	}

	public Boolean getActive() {
		return this.active;
	}

	public Boolean getBlocked() {
		return this.blocked;
	}

	public Boolean getAlerted() {
		return this.alerted;
	}

	public SortKey getSortKey() {
		return this.sortKey;
	}

	public SortDir getSortDir() {
		return this.sortDir;
	}

	public int getOffset() {
		return this.offset;
	}

	public int getLimit() {
		return this.limit;
	}

	public boolean isObeyQueryLimit() {
		return this.obeyQueryLimit;
	}

	public boolean isExportAll() {
		return this.exportAll;
	}

	public void validate() throws BuilderException {
		if (this.sortKey != null) {
			if (!SORT_KEYS.containsKey(this.sortKey)) {
				throw new BuilderException("Unrecognized sortKey: " + this.sortKey);
			}
		}
		if (this.offset < 0) {
			throw new BuilderException("Invalid offset: " + this.offset);
		}
		if (this.limit < 0) {
			throw new BuilderException("Invalid limit: " + this.limit);
		}

		if (this.getOrgIds() != null) {
			int size = this.getOrgIds().size();
			if (size > SystemProperties.getMaxQueryInClauseSize()) {
				throw new BuilderException("REQUEST_TOO_LARGE, ComputerDtoFindByCriteriaCmd is filtering on too many orgs: {}",
						size);
			}
		}
	}
}