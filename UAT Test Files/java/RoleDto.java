package com.code42.auth;

import java.util.Date;
import java.util.Set;

import com.code42.user.Role;

public class RoleDto {

	private Role role;
	private int numberOfUsers;

	public RoleDto(Role role, int numberOfUsers) {
		this.role = role;
		this.numberOfUsers = numberOfUsers;
	}

	public int getRoleId() {
		return this.role.getRoleId();
	}

	public String getRoleName() {
		return this.role.getRoleName();
	}

	public Set<IPermission> getPermissions() {
		return this.role.getPermissions();
	}

	public boolean isLocked() {
		return this.role.isLocked();
	}

	public Date getCreationDate() {
		return this.role.getCreationDate();
	}

	public Date getModificationDate() {
		return this.role.getModificationDate();
	}

	public int getNumberOfUsers() {
		return this.numberOfUsers;
	}
}