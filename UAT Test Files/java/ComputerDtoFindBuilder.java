package com.code42.computer;

import java.util.Collection;
import java.util.HashSet;

import com.code42.core.BuilderException;

public abstract class ComputerDtoFindBuilder<S, T> {

	enum Include {
		ALL, BACKUP_USAGE, AUTHORITY, ACTIVITY, HISTORY, SETTINGS, SERVERS, COUNTS
	}

	private Long computerId;
	private Long computerGuid;
	private Collection<Include> include = new HashSet<Include>();

	public ComputerDtoFindBuilder() {
	}

	public ComputerDtoFindBuilder computerId(Long computerId) {
		this.computerId = computerId;
		return this;
	}

	public ComputerDtoFindBuilder computerGuid(Long computerGuid) {
		this.computerGuid = computerGuid;
		return this;
	}

	/**
	 * Include all the additional pieces that can be added to a computerDto
	 */
	public S includeAll() {
		this.include.add(Include.ALL);
		return (S) this;
	}

	public S includeBackupUsage() {
		this.include.add(Include.BACKUP_USAGE);
		return (S) this;
	}

	public S includeAuthority() {
		this.include.add(Include.BACKUP_USAGE); // Must have destinations if AUTHORITY is chosen
		this.include.add(Include.AUTHORITY);
		return (S) this;
	}

	public S includeActivity() {
		this.include.add(Include.ACTIVITY);
		return (S) this;
	}

	public S includeSettings() {
		this.include.add(Include.SETTINGS);
		return (S) this;
	}

	public S includeHistory() {
		this.include.add(Include.BACKUP_USAGE); // Must have destinations if HISTORY is chosen
		this.include.add(Include.HISTORY);
		return (S) this;
	}

	public S includeServers() {
		this.include.add(Include.SERVERS);
		return (S) this;
	}

	public S includeCounts() {
		this.include.add(Include.COUNTS);
		return (S) this;
	}

	// ======================================================
	// Getters
	// ======================================================

	public Long getComputerId() {
		return this.computerId;
	}

	public Long getComputerGuid() {
		return this.computerGuid;
	}

	public boolean isIncludeAll() {
		return this.include.contains(Include.ALL);
	}

	public boolean isIncludeBackupUsage() {
		return this.isIncludeAll() || this.include.contains(Include.BACKUP_USAGE);
	}

	public boolean isIncludeAuthority() {
		return this.isIncludeAll() || this.include.contains(Include.AUTHORITY);
	}

	public boolean isIncludeActivity() {
		return this.isIncludeAll() || this.include.contains(Include.ACTIVITY);
	}

	public boolean isIncludeHistory() {
		return this.isIncludeAll() || this.include.contains(Include.HISTORY);
	}

	public boolean isIncludeSettings() {
		return this.isIncludeAll() || this.include.contains(Include.SETTINGS);
	}

	public boolean isIncludeServers() {
		return this.isIncludeAll() || this.include.contains(Include.SERVERS);
	}

	public boolean isIncludeCounts() {
		return this.isIncludeAll() || this.include.contains(Include.COUNTS);
	}

	public abstract T build() throws BuilderException;

}
