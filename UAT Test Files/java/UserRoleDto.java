package com.code42.user;

import java.util.List;

public class UserRoleDto {

	private UserRole userRole;

	public UserRoleDto(UserRole ur) {
		this.userRole = ur;
	}

	public Boolean isAllowAllOrgs() {
		return this.userRole.isAllowAllOrgs();
	}

	public List<Integer> getOrgIds() {
		return this.userRole.getOrgList();
	}

	public int getUserId() {
		return this.userRole.getUser().getUserId();
	}

	public String getRoleName() {
		return this.userRole.getRole().getRoleName();
	}

	public UserRole getUserRole() {
		return this.userRole;
	}

}
