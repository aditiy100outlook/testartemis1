package com.code42.setting;

import com.google.common.annotations.VisibleForTesting;

/**
 * A packet that defines a setting, but not the mapping ids. These packets are used to satisfy requests wherein the
 * mapping context will be built from the requesting entity.
 * 
 */
public class SettingPacket {

	private String scope;
	private String key;
	private String value;
	private boolean locked;

	public SettingPacket() {
		super();
	}

	@VisibleForTesting
	public SettingPacket(String scope, String key, String value, boolean locked) {
		super();
		this.scope = scope;
		this.key = key;
		this.value = value;
		this.locked = locked;
	}

	public String getScope() {
		return this.scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

	public String getKey() {
		return this.key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getValue() {
		return this.value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public boolean isLocked() {
		return this.locked;
	}

	public void setLocked(boolean locked) {
		this.locked = locked;
	}

	@Override
	public String toString() {
		return "SettingPacket [scope=" + this.scope + ", key=" + this.key + ", value=" + this.value + ", locked="
				+ this.locked + "]";
	}
}
