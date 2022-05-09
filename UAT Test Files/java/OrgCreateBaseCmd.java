package com.code42.org;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import com.backup42.common.OrgType;
import com.code42.address.Address;
import com.code42.config.OrgComputerConfigCreateCmd;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.DuplicateExistsException;
import com.code42.core.db.impl.CreateQuery;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.OrgCreateCmd.Error;
import com.code42.user.AddressCreateQuery;
import com.code42.utils.LangUtils;
import com.code42.utils.UniqueId;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;

public abstract class OrgCreateBaseCmd extends DBCmd<Org> {

	private static Logger log = LoggerFactory.getLogger(OrgCreateBaseCmd.class);

	protected Builder data;
	protected OrgSso parentOrg = null;

	public OrgCreateBaseCmd(Builder data) {
		this.data = data;
	}

	public <T extends Org> T createOrg(CoreSession session, BackupOrg org) throws CommandException {
		return this.createOrg(session, org, true);
	}

	public <T extends Org> T createOrg(CoreSession session, BackupOrg org, boolean save) throws CommandException {

		this.populateData(org);

		BackupOrg createdOrg;

		try {
			this.db.beginTransaction();

			this.populateParentOrg(org);

			org.setType(this.getOrgType());

			this.populateOrgUid(org);

			if (!this.env.getClusterOrgTypes().contains(org.getType())) {
				long clusterGuid = this.env.getMyClusterGuid();
				log.error("Invalid Data: cannot create this org on this cluster. clusterGuid=" + clusterGuid + ", org=" + org);
				throw new CommandException("Invalid Data: cannot create this org on this cluster GUID: " + clusterGuid,
						"clusterGuid=" + clusterGuid, org);
			}

			this.populateAddress(org);

			// Check to make sure we are not adding an already existing external id.
			Org duplicateOrg = this.runtime.run(new OrgFindDuplicateCmd(org), session);
			if (duplicateOrg != null) {
				throw new DuplicateExistsException(OrgCreateCmd.Error.ORG_DUPLICATE,
						"Unable to create org, already exists (uid=" + org.getOrgUid() + ").");
			}

			if (!save) {
				return (T) org;
			}

			OrgCreateQuery query = new OrgCreateQuery(org);
			createdOrg = this.db.create(query);
			String regKey = this.run(new OrgEnsureHasRegKeyCmd(createdOrg), session);

			if (createdOrg.getRegistrationKey() == null || !createdOrg.getRegistrationKey().equals(regKey)) {
				throw new CommandException("Failed to create a reg key for org=" + createdOrg);
			}

			/*
			 * Create the notify settings and config for the org
			 * 
			 * We are using an adminSession because because to get to this point you would already have been authenticated for
			 * the create command you are running. In the situation of an OrgAdmin the newly created org is not yet in the
			 * OrgAdmin's authorized orgs and the next two commands will fail.
			 */
			CoreSession systemSession = this.auth.getSystemSession();
			this.runtime.run(new OrgComputerConfigCreateCmd(createdOrg.getOrgId()), systemSession);
			this.runtime.run(new OrgNotifySettingsCreateCmd(createdOrg.getOrgId()), systemSession);

			this.db.afterTransaction(new OrgPublishCreateCmd(createdOrg), session);

			this.db.commit();
		} catch (CommandException ce) {
			this.db.rollback();
			throw ce;
		} catch (Throwable t) {
			this.db.rollback();
			throw new CommandException("Error Creating Org; org=" + this.data.orgName, t);
		} finally {
			this.db.endTransaction();
		}

		return (T) createdOrg;
	}

	private BackupOrg populateData(BackupOrg org) {

		org.setOrgName(this.data.orgName);

		if (LangUtils.hasValue(this.data.orgUid)) {
			org.setOrgUid(this.data.orgUid.get());
		}
		if (LangUtils.hasValue(this.data.customConfig)) {
			org.setCustomConfig(this.data.customConfig.get());
		}
		if (LangUtils.hasValue(this.data.inheritDestinations)) {
			org.setInheritDestinations(this.data.inheritDestinations.get());
		}
		if (LangUtils.hasValue(this.data.masterGuid)) {
			org.setMasterGuid(this.data.masterGuid.get());
		}
		if (LangUtils.hasValue(this.data.maxBytes)) {
			org.setMaxBytes(this.data.maxBytes.get());
		}
		if (LangUtils.hasValue(this.data.maxSeats)) {
			org.setMaxSeats(this.data.maxSeats.get());
		}
		if (LangUtils.hasValue(this.data.registrationKey)) {
			org.setRegistrationKey(this.data.registrationKey.get());
		}

		return org;
	}

	/**
	 * Builds the input data and the command. This takes the place of a big long constructor.
	 */
	abstract static class Builder {

		protected final String orgName;
		protected Option<String> orgUid = None.getInstance();
		protected Option<Integer> parentOrgId = None.getInstance();
		protected Option<String> parentOrgUid = None.getInstance();
		protected Option<Boolean> customConfig = None.getInstance();
		protected Option<Boolean> inheritDestinations = None.getInstance();
		protected Option<Long> masterGuid = None.getInstance();
		protected Option<Long> maxBytes = None.getInstance();
		protected Option<Integer> maxSeats = None.getInstance();
		protected Option<String> registrationKey = None.getInstance();
		protected Option<Integer> addressId = None.getInstance();
		protected Option<String> contactName = None.getInstance();
		protected Option<String> contactEmail = None.getInstance();
		protected Option<String> contactPhoneNumber = None.getInstance();
		protected Option<String> contactStreet1 = None.getInstance();
		protected Option<String> contactStreet2 = None.getInstance();
		protected Option<String> contactCity = None.getInstance();
		protected Option<String> contactState = None.getInstance();
		protected Option<String> contactPostalCode = None.getInstance();
		protected Option<Boolean> hosted = new Some<Boolean>(false);

		public Builder(String orgName) throws BuilderException {
			if (LangUtils.hasValue(orgName)) {
				this.orgName = orgName.trim();
			} else {
				throw new BuilderException(Error.ORG_NAME_MISSING, "Organization name is required.");
			}
		}

		public Builder orgUid(String orgUid) {
			this.orgUid = new Some<String>(orgUid);
			return this;
		}

		public Builder parentOrgId(Integer parentOrgId) {
			this.parentOrgId = new Some<Integer>(parentOrgId);
			return this;
		}

		public Builder parentOrgUid(String parentOrgUid) {
			this.parentOrgUid = new Some<String>(parentOrgUid);
			return this;
		}

		public Builder customConfig(Boolean customConfig) {
			this.customConfig = new Some<Boolean>(customConfig);
			return this;
		}

		public Builder inheritDestinations(Boolean inheritDestinations) {
			this.inheritDestinations = new Some<Boolean>(inheritDestinations);
			return this;
		}

		public Builder masterGuid(Long masterGuid) {
			this.masterGuid = new Some<Long>(masterGuid);
			return this;
		}

		public Builder maxBytes(Long maxBytes) {
			this.maxBytes = new Some<Long>(maxBytes);
			return this;
		}

		public Builder maxSeats(Integer maxSeats) {
			this.maxSeats = new Some<Integer>(maxSeats);
			return this;
		}

		public Builder registrationKey(String registrationKey) {
			this.registrationKey = new Some<String>(registrationKey);
			return this;
		}

		public Builder addressId(Integer addressId) {
			this.addressId = new Some<Integer>(addressId);
			return this;
		}

		public Builder contactName(String contactName) {
			this.contactName = new Some<String>(contactName);
			return this;
		}

		public Builder contactEmail(String contactEmail) {
			this.contactEmail = new Some<String>(contactEmail);
			return this;
		}

		public Builder contactPhoneNumber(String contactPhoneNumber) {
			this.contactPhoneNumber = new Some<String>(contactPhoneNumber);
			return this;
		}

		public Builder contactStreet1(String contactStreet1) {
			this.contactStreet1 = new Some<String>(contactStreet1);
			return this;
		}

		public Builder contactStreet2(String contactStreet2) {
			this.contactStreet2 = new Some<String>(contactStreet2);
			return this;
		}

		public Builder contactCity(String contactCity) {
			this.contactCity = new Some<String>(contactCity);
			return this;
		}

		public Builder contactState(String contactState) {
			this.contactState = new Some<String>(contactState);
			return this;
		}

		public Builder contactZip(String contactZip) {
			this.contactPostalCode = new Some<String>(contactZip);
			return this;
		}

		public Builder hosted(boolean hosted) {
			this.hosted = new Some<Boolean>(hosted);
			return this;
		}

		protected abstract void validate() throws BuilderException;

		public abstract OrgCreateBaseCmd build() throws BuilderException;
	}

	// /////////////////////
	// HELPER METHODS
	// /////////////////////

	protected void populateParentOrg(Org org) throws CommandException {
		if (this.parentOrg != null) {
			// Validate that the parent is not blocked
			if (this.parentOrg.isBlocked()) {
				throw new CommandException(Error.PARENT_ORG_BLOCKED, "Parent org is blocked: " + this.parentOrg);
			}
			// Validate that the parent is not deactivated
			if (!this.parentOrg.isActive()) {
				throw new CommandException(Error.PARENT_ORG_NOT_ACTIVE, "Parent org is deactivated: " + this.parentOrg);
			}

			if (this.parentOrg != null) {
				org.setParentOrgId(this.parentOrg.getOrgId());
			}
		}
	}

	/*
	 * Org type is entirely determined by the command that is being executed. Current policy is that OrgCreateCmd will
	 * generate BUSINESS orgs while ProOrgCreateCmd will generate ENTERPRISE orgs. That's it; note that nobody is able to
	 * create an org of type CONSUMER. This policy is designed to enforce the constraint that at all times there is one
	 * and only one org of type CONSUMER in the system... and it's org 42.
	 */
	abstract OrgType getOrgType();

	/**
	 * 
	 * @param org - Cannot be null and must have a populated orgName
	 * @throws CommandException if org is null or it's orgName property is null.
	 */
	protected void populateOrgUid(Org org) throws CommandException {

		if (org.getOrgUid() == null) { // doesn't have externalId yet
			String uid = String.valueOf(UniqueId.generateId());
			// String externalId = org.getOrgName();
			// externalId = externalId.toLowerCase();
			// externalId = externalId.replaceAll(" ", "");
			// BackupOrg bOrg = (BackupOrg) org;
			// if (bOrg.isHostedParent()) {
			// externalId = externalId + "_" + bOrg.getMasterGuid();
			// }
			// // Find a unique external id
			// Org dupe = this.db.find(new OrgFindByExternalIdQuery(externalId));
			// int i = 1;
			// String temp = externalId;
			// while (dupe != null) {
			// temp = externalId;
			// temp += "_" + i++;
			// dupe = this.db.find(new OrgFindByExternalIdQuery(temp));
			// }
			// externalId = temp;
			org.setOrgUid(uid);
		}
	}

	protected void populateAddress(Org org) throws CommandException {
		// Check if they are using an address created elsewhere
		if (!(this.data.addressId instanceof None)) {
			org.setAddressId(this.data.addressId.get());
			return;
		}

		boolean valueSet = false;
		Address a = new Address();

		if (!(this.data.contactName instanceof None)) {
			a.setName(this.data.contactName.get());
			valueSet = true;
		}

		if (!(this.data.contactEmail instanceof None)) {
			a.setEmail(this.data.contactEmail.get());
			valueSet = true;
		}

		if (!(this.data.contactPhoneNumber instanceof None)) {
			a.setPhoneNumber(this.data.contactPhoneNumber.get());
			valueSet = true;
		}

		if (!(this.data.contactStreet1 instanceof None)) {
			a.setAddressLine1(this.data.contactStreet1.get());
			valueSet = true;
		}

		if (!(this.data.contactStreet2 instanceof None)) {
			a.setAddressLine2(this.data.contactStreet2.get());
			valueSet = true;
		}

		if (!(this.data.contactCity instanceof None)) {
			a.setCity(this.data.contactCity.get());
			valueSet = true;
		}

		if (!(this.data.contactState instanceof None)) {
			a.setState(this.data.contactState.get());
			valueSet = true;
		}

		if (!(this.data.contactPostalCode instanceof None)) {
			a.setPostalCode(this.data.contactPostalCode.get());
			valueSet = true;
		}

		if (valueSet) {
			a = this.db.create(new AddressCreateQuery(a));
			org.setAddressId(a.getAddressId());
		}

	}

	/**
	 * Find the parent org, if any; can return null.
	 */
	protected OrgSso getParentOrg() throws CommandException {
		OrgSso pOrg = null;
		// Get parent org
		if (!(this.data.parentOrgId instanceof None)) {
			Integer parentOrgId = this.data.parentOrgId.get();
			final Org po = this.db.find(new OrgFindByIdQuery(parentOrgId));
			if (po != null) {
				pOrg = new OrgSso(po);
			} else {
				throw new CommandException("Unable to create org, parent org not found. orgId=" + parentOrgId);
			}
		} else if (!(this.data.parentOrgUid instanceof None)) {
			String parentOrgUid = this.data.parentOrgUid.get();
			Long masterGuid = (!(this.data.masterGuid instanceof None)) ? this.data.masterGuid.get() : null;
			final Org po = this.db.find(new OrgFindByUidQuery(parentOrgUid, masterGuid));
			if (po != null) {
				pOrg = new OrgSso(po);
			} else {
				throw new CommandException("Unable to create org, parent org not found. orgUid=" + parentOrgUid);
			}
		} // else creating a top-level org
		return pOrg;
	}

	// /////////////////////
	// PRIVATE QUERIES
	// /////////////////////

	/**
	 * Provides the "query" interface for accessing the database.
	 */
	private static class OrgCreateQuery extends CreateQuery<BackupOrg> {

		private BackupOrg org;

		OrgCreateQuery(BackupOrg org) {
			this.org = org;
		}

		@Override
		public BackupOrg query(Session session) throws DBServiceException {
			try {
				session.save(this.org);
				return this.org;
			} catch (HibernateException e) {
				throw new DBServiceException("Unable to create org=" + this.org, e);
			}
		}

	}
}
