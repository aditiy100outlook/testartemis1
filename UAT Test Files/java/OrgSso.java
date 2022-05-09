package com.code42.org;

import java.io.Serializable;

import com.backup42.common.OrgType;
import com.backup42.server.MasterServices;
import com.code42.core.BuilderException;
import com.code42.core.impl.CoreBridge;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;

/**
 * Simple, non-persistent, space storage object representing the basic Org information. Suitable for storing in a cache
 * somewhere.
 */
public class OrgSso implements IOrg, Serializable {

	private static final long serialVersionUID = 6381551374561467692L;

	private final int orgId;
	private final String orgUid;
	private final String orgName;
	private final OrgType orgType;
	private final boolean active;
	private final boolean blocked;
	private final boolean hosted;
	private final boolean master;

	private final Integer parentOrgId;
	private final Long masterGuid;

	private final Integer maxSeats;
	private final Long maxBytes;

	/*
	 * This constructor used to be package protected... but I'm not sure that constraint makes much sense anymore. There's
	 * a clear need to access this functionality from the SSO seed code, and keeping this as package protected forces the
	 * creation of a command in com.code42.org to generate an OrgSso from an Org. But as soon as such a command is created
	 * we now have a general component that can create SSOs... which is exactly the same thing this constructor does. Why
	 * not just skip the middleman and expose the constructor?
	 * 
	 * I believe the original concern was around arbitrary components creating and adding OrgSsos. This concern just
	 * hasn't been a problem in practice... so I'm hard-pressed to argue that we should keep any restriction here.
	 */
	public OrgSso(Org org) {

		this.orgId = org.getOrgId();
		this.orgUid = org.getOrgUid();
		this.orgName = org.getOrgName();
		this.orgType = org.getType();
		this.active = org.isActive();
		this.blocked = org.isBlocked();

		/* ----------- Possibly null values ----------- */
		this.parentOrgId = org.getParentOrgId();
		this.masterGuid = (org instanceof BackupOrg) ? ((BackupOrg) org).getMasterGuid() : null;
		this.maxSeats = (org instanceof BackupOrg) ? ((BackupOrg) org).getMaxSeats() : null;
		this.maxBytes = (org instanceof BackupOrg) ? ((BackupOrg) org).getMaxBytes() : null;

		/* ----------- Computed values ----------- */
		this.hosted = MasterServices.getInstance().isHostedOrg(org);
		this.master = MasterServices.getInstance().isMasterOrg(org);
	}

	public OrgSso(Builder builder) {

		this.orgId = builder.orgId;
		this.orgUid = builder.orgUid;
		this.orgName = builder.orgName;
		this.orgType = builder.orgType;
		this.active = builder.active;
		this.blocked = builder.blocked;

		/* ----------- Possibly null values ----------- */
		this.parentOrgId = (builder.parentOrgId instanceof None) ? null : builder.parentOrgId.get();
		this.masterGuid = (builder.masterGuid instanceof None) ? null : builder.masterGuid.get();
		this.maxSeats = builder.maxSeats;
		this.maxBytes = builder.maxBytes;

		/* ----------- Computed values ----------- */
		this.hosted = MasterServices.getInstance().isHostedOrg(this.masterGuid, builder.discriminator);
		this.master = MasterServices.getInstance().isMasterOrg(this.masterGuid, builder.discriminator);
	}

	public Integer getOrgId() {
		return this.orgId;
	}

	public String getOrgUid() {
		return this.orgUid;
	}

	public String getOrgName() {
		return this.orgName;
	}

	public boolean isActive() {
		return this.active;
	}

	public boolean isBlocked() {
		return this.blocked;
	}

	public OrgType getType() {
		return this.orgType;
	}

	public boolean isHosted() {
		return this.hosted;
	}

	public boolean isMaster() {
		return this.master;
	}

	public Integer getParentOrgId() {
		return this.parentOrgId;
	}

	public Long getMasterGuid() {
		return this.masterGuid;
	}

	public Integer getMaxSeats() {
		return this.maxSeats;
	}

	public Long getMaxBytes() {
		return this.maxBytes;
	}

	public Org toOrg() {
		return CoreBridge.runNoException(new OrgFindByIdCmd(this.orgId));
	}

	@Override
	public String toString() {
		return "OrgSso [orgId=" + this.orgId + ", orgUid=" + this.orgUid + ", orgName=" + this.orgName + ", orgType="
				+ this.orgType + ", active=" + this.active + ", blocked=" + this.blocked + ", hosted=" + this.hosted
				+ ", master=" + this.master + ", parentOrgId=" + this.parentOrgId + ", masterGuid=" + this.masterGuid
				+ ", maxSeats=" + this.maxSeats + ", maxBytes=" + this.maxBytes + "]";
	}

	public static class Builder {

		/*
		 * Not all of these values are required for every org: parent org ID and master GUID are optional (they aren't
		 * present for root orgs and non-hosted orgs respectively). Null isn't enough of an indicator here so for these
		 * values we have to use the option type.
		 * 
		 * Discriminator is necessary for some of the properties computed by the constructors
		 */
		private Integer orgId;
		private String orgUid;
		private String orgName;
		private OrgType orgType;
		private Boolean active;
		private Boolean blocked;
		private String discriminator;

		private Option<Integer> parentOrgId = None.getInstance();
		private Option<Long> masterGuid = None.getInstance();
		private Integer maxSeats;
		private Long maxBytes;

		public Builder orgId(int orgId) {
			this.orgId = orgId;
			return this;
		}

		public Builder orgUid(String orgUid) {
			this.orgUid = orgUid;
			return this;
		}

		public Builder orgName(String orgName) {
			this.orgName = orgName;
			return this;
		}

		public Builder orgType(OrgType orgType) {
			this.orgType = orgType;
			return this;
		}

		public Builder active(boolean active) {
			this.active = active;
			return this;
		}

		public Builder blocked(boolean blocked) {
			this.blocked = blocked;
			return this;
		}

		public Builder discriminator(String discriminator) {
			this.discriminator = discriminator;
			return this;
		}

		public Builder parentOrgId(int parentOrgId) {
			this.parentOrgId = new Some<Integer>(parentOrgId);
			return this;
		}

		public Builder masterGuid(long masterGuid) {
			this.masterGuid = new Some<Long>(masterGuid);
			return this;
		}

		public Builder maxSeats(Integer maxSeats) {
			this.maxSeats = maxSeats;
			return this;
		}

		public Builder maxBytes(Long maxBytes) {
			this.maxBytes = maxBytes;
			return this;
		}

		public void reset() {
			this.orgId = null;
			this.orgUid = null;
			this.orgName = null;
			this.orgType = null;
			this.active = null;
			this.blocked = null;
			this.discriminator = null;

			this.parentOrgId = None.getInstance();
			this.masterGuid = None.getInstance();
			this.maxSeats = null;
			this.maxBytes = null;
		}

		public void validate() throws BuilderException {

			/* Only need to validate some fields; it's okay if parent org ID or master GUID aren't set */
			if (this.orgId == null || this.orgUid == null || this.orgName == null || this.orgType == null) {

				throw new BuilderException("All fields must be set");
			}
			if (this.active == null || this.blocked == null || this.discriminator == null) {

				throw new BuilderException("All fields must be set");
			}
		}

		public OrgSso build() throws BuilderException {
			this.validate();
			return new OrgSso(this);
		}
	}
}
