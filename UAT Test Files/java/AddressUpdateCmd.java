/*
 * <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.user;

import com.code42.address.Address;
import com.code42.core.CommandException;
import com.code42.core.ICoreRuntime;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;

/**
 * Validate and update a few fields on the given computer.
 */
public class AddressUpdateCmd extends DBCmd<Address> {

	private static final Logger log = LoggerFactory.getLogger(AddressUpdateCmd.class.getName());

	private Builder data = null;

	/**
	 * Use the Builder static inner class to construct one of these.
	 */
	private AddressUpdateCmd(Builder b) {
		this.data = b;
	}

	/**
	 * @return null if the computerId does not exist.
	 */
	@Override
	public Address exec(CoreSession session) throws CommandException {

		Address address = null;

		this.db.beginTransaction();

		try {
			// Return null if this computer does not exist.
			address = this.db.find(new AddressFindByIdQuery(this.data.addressId));
			if (address == null) {
				return null; // Address does not exist
			}

			// If permission check fails, this will throw an exception
			// this.runtime.run(new IsAddressManageable(this.data.addressId, C42PermissionApp.Computer.UPDATE), session);

			if (!(this.data.name instanceof None)) {
				address.setName(this.data.name.get());
			}

			if (!(this.data.addressLine1 instanceof None)) {
				address.setAddressLine1(this.data.addressLine1.get());
			}

			if (!(this.data.addressLine2 instanceof None)) {
				address.setAddressLine2(this.data.addressLine2.get());
			}

			if (!(this.data.city instanceof None)) {
				address.setCity(this.data.city.get());
			}

			if (!(this.data.state instanceof None)) {
				address.setState(this.data.state.get());
			}

			if (!(this.data.postalCode instanceof None)) {
				address.setPostalCode(this.data.postalCode.get());
			}

			if (!(this.data.countryCode instanceof None)) {
				address.setCountryCode(this.data.countryCode.get());
			}

			if (!(this.data.phoneNumber instanceof None)) {
				address.setPhoneNumber(this.data.phoneNumber.get());
			}

			address = this.db.update(new AddressUpdateQuery(address));
			this.db.commit();

			log.info(session + " modified address: " + address);
		} catch (CommandException e) {
			this.db.rollback();
			throw e;
		} catch (Throwable e) {
			this.db.rollback();
			log.error("Unexpected: ", e);
		} finally {
			this.db.endTransaction();
		}

		return address;
	}

	/**
	 * Builds the input data and the AddressUpdate command. This takes the place of a big long constructor.
	 */
	public static class Builder {

		/* This value must always be present; it's the only way to get a builder */
		public int addressId = 0;

		public Option<String> name = None.getInstance();
		public Option<String> addressLine1 = None.getInstance();
		public Option<String> addressLine2 = None.getInstance();
		public Option<String> city = None.getInstance();
		public Option<String> state = None.getInstance();
		public Option<String> postalCode = None.getInstance();
		public Option<String> countryCode = None.getInstance();
		public Option<String> phoneNumber = None.getInstance();

		public Builder(int addressId) {
			this.addressId = addressId;
		}

		/**
		 * Only subclasses can use this constructor.
		 */
		protected Builder() {
		}

		public Builder name(String name) {
			this.name = new Some(name);
			return this;
		}

		public Builder addressLine1(String addressLine1) {
			this.addressLine1 = new Some(addressLine1);
			return this;
		}

		public Builder addressLine2(String addressLine2) {
			this.addressLine2 = new Some(addressLine2);
			return this;
		}

		public Builder city(String city) {
			this.city = new Some(city);
			return this;
		}

		public Builder state(String state) {
			this.state = new Some(state);
			return this;
		}

		public Builder postalCode(String postalCode) {
			this.postalCode = new Some(postalCode);
			return this;
		}

		public Builder countryCode(String countryCode) {
			this.countryCode = new Some(countryCode);
			return this;
		}

		public Builder phoneNumber(String phoneNumber) {
			this.phoneNumber = new Some(phoneNumber);
			return this;
		}

		public void validate(ICoreRuntime runtime)
				throws CommandException {
			// Does no checking right now
		}

		public AddressUpdateCmd build(ICoreRuntime runtime)
				throws CommandException {

			this.validate(runtime);
			return new AddressUpdateCmd(this);
		}
	}
}
