package com.code42.computer;

import java.io.Serializable;


/**
 * A specialized FriendComputerUsage where the target is a server. It references the original usage record but provides
 * the real server/computer that can act as authority/restore.
 * 
 * @author <a href="mailto:brada@code42.com">Brad Armstrong</a>
 * 
 */
public class AuthorityTargetUsageDto implements Serializable {

	private static final long serialVersionUID = 7252640967864862415L;

	private Computer authority;

	// the authority in this class replaces the target in the relationship
	// referenced in this usage.
	private Long friendComputerUsageRefId;

	public AuthorityTargetUsageDto(Computer authorityTarget, Long referenceFriendComputerUsageId) {
		this.authority = authorityTarget;
		this.friendComputerUsageRefId = referenceFriendComputerUsageId;
	}

	public Long getAuthorityComputerId() {
		return this.authority.getComputerId();
	}

	public Long getAuthorityGuid() {
		return this.authority.getGuid();
	}

	public String getAuthorityAddress() {
		return this.authority.getAddress();
	}

	public Computer getAuthority() {
		return this.authority;
	}

	public Long getFriendComputerUsageRefId() {
		return this.friendComputerUsageRefId;
	}

}
