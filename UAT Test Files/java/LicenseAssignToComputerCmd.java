package com.code42.license;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.backup42.common.ComputerType;
import com.backup42.computer.ComputerServices;
import com.backup42.computer.LicenseServices;
import com.backup42.social.SocialComputerNetworkServices;
import com.code42.computer.Computer;
import com.code42.computer.ComputerFindByIdCmd;
import com.code42.computer.FriendComputerUsageFindByUserAndTargetQuery;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.code42.exception.DebugRuntimeException;
import com.code42.hibernate.Persistence;
import com.code42.server.destination.Destination;
import com.code42.server.destination.DestinationFindAllByClusterCmd;
import com.code42.social.FriendComputerUsage;
import com.code42.user.User;
import com.code42.user.UserFindByIdCmd;
import com.code42.user.UserFindByIdQuery;
import com.code42.utils.LangUtils;
import com.google.inject.Inject;

/**
 * Assign the license to the target computer if the operation is allowed.
 * 
 * This method handles UserLicenses, ComputerLicenses and those items may be tied to subscriptions. All of that must be
 * taken into account during processing.
 */
public class LicenseAssignToComputerCmd extends DBCmd<Void> {

	private final String key;
	private final Computer computer;
	private final boolean transferConfirmed;

	private Set<LicenseCallback> licenseCallbacks;

	@Inject
	public void setLicenseCallbacks(Set<LicenseCallback> callbacks) {
		this.licenseCallbacks = callbacks;
	}

	/**
	 * Assign the specified license key to the computer.
	 * 
	 * A transfer that is not confirmed will fail and be sent back to the user if there is a conflict on this license (aka
	 * it's already in use). Once confirmed, the assignment will go through.
	 * 
	 * @param key license key
	 * @param computer target computer
	 * @param transferConfirmed
	 */
	public LicenseAssignToComputerCmd(String key, Computer computer, boolean transferConfirmed) {
		super();
		this.key = key;
		this.computer = computer;
		this.transferConfirmed = transferConfirmed;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		// verify that a license exists and that it is active
		License license = this.db.find(new LicenseFindByKeyQuery(this.key));

		if (license == null) {
			throw new LicenseDoesNotExistException("License key does not exist", this.key);
		} else if (!license.isActive()) {
			// must be active
			throw new IneligibleLicenseException("License is not active", license);
		} else if (!(license instanceof BaseUserLicense)) {
			// must be user-based
			throw new IneligibleLicenseException("License is not of type BaseUserLicense");
		} else if (!LangUtils.in(this.computer.getType(), ComputerType.ELIGIBLE_FOR_GREEN_LICENSES)) {
			throw new IneligibleLicenseException("Computer is not eligible to be assigned this license type", license);
		}

		/**
		 * On to doing the assignment.
		 */

		final User targetUser = this.db.find(new UserFindByIdQuery(this.computer.getUserId()));

		// are we approved? if not we need to (optionally) abort with some nice 'are you sure' context for the user
		if (!this.transferConfirmed) {
			this.validateTransfer(license, targetUser, session);
		}

		try {
			Persistence.beginTransaction();

			// gather the original source user if we can
			User sourceUser = null;
			if (license instanceof BaseUserLicense) {
				BaseUserLicense ul = (BaseUserLicense) license;
				sourceUser = ul.getUserId() != null ? CoreBridge.run(new UserFindByIdCmd(ul.getUserId())) : null;
			}

			// always unassign any licenses assigned to the target computer. the provided license is what should be displayed.
			LicenseServices.getInstance().unassignLicensesForComputer(this.computer.getComputerId());

			// attempt to transfer the license
			for (LicenseCallback licenseCallback : this.licenseCallbacks) {
				licenseCallback.transferLicenseToUser(license, targetUser, session);
			}

			// what we're ultimately here to do is assign the license
			this.assignLicenseToComputer(license, this.computer);

			// notify all users involved of a license change; sourceUser may be null, but the handle method can take it
			if (sourceUser != null) {
				LicenseServices.getInstance().handleLicenseChangeForUser(sourceUser);
				boolean same = sourceUser != null && targetUser != null
						&& sourceUser.getUserId().equals(targetUser.getUserId());
				if (!same) {
					LicenseServices.getInstance().handleLicenseChangeForUser(targetUser);
				}
			}

			Persistence.commit();

		} catch (Exception e) {
			throw new DebugRuntimeException("Failed to assignLicense key=" + this.key + " to computerId="
					+ this.computer.getComputerId(), e, new Object[] { this.key, this.computer });
		} finally {
			Persistence.endTransaction();
		}

		return null;
	}

	/**
	 * Validate that this transfer should occur. If you're moving a license between users this method WILL throw a
	 * LicenseUnavailableException with some context for user-approval on the client side.
	 */
	private void validateTransfer(License license, User targetUser, CoreSession session)
			throws LicenseUnavailableException, CommandException {

		// anonymous licenses require no approval to transfer
		if (license.isAnonymous()) {
			return;
		}

		// if no computers are dependent upon the license where it is, then we can move it without warning the user
		boolean hasDependent = false;
		for (LicenseCallback callback : this.licenseCallbacks) {
			hasDependent = hasDependent || callback.hasDependentComputers(license, session);
		}
		if (!hasDependent) {
			return;
		}

		// the license is in use!
		if (license instanceof BaseUserLicense) {
			final BaseUserLicense bul = (BaseUserLicense) license;

			// we have some decisions to make; now that we know we have a user id we're null safe
			final User srcUser = CoreBridge.runNoException(new UserFindByIdCmd(bul.getUserId()));
			final boolean sameUser = srcUser.getUserId().intValue() == targetUser.getUserId().intValue();
			final boolean isUserLicense = license instanceof UserLicense;
			final boolean isComputerLicense = license instanceof ComputerLicense;

			// USER LICENSE
			if (isUserLicense) {

				// user licenses that aren't moving between users require no approval step
				if (sameUser) {
					return;
				}

				// its cool if they have no computers, likely the user was created during purchase
				final List<Computer> computers = ComputerServices.getInstance().findComputersForUser(srcUser.getUserId());
				if (computers.isEmpty()) {
					return;
				}

				long archiveSize = 0;
				List<Long> targetGuids = new ArrayList<Long>();
				List<Destination> destinations = this.run(new DestinationFindAllByClusterCmd(), session);
				for (Destination d : destinations) {
					targetGuids.add(d.getDestinationGuid());
				}
				final List<FriendComputerUsage> usages = this.db.find(new FriendComputerUsageFindByUserAndTargetQuery(srcUser
						.getUserId(), targetGuids));
				for (FriendComputerUsage usage : usages) {
					archiveSize += usage.getArchiveBytes();
				}

				// get name - the display name could be empty, i.e. user created during purchase
				String name = srcUser.getDisplayName();
				if (!LangUtils.hasValue(name)) {
					name = srcUser.getUsername();
				}

				throw new LicenseUnavailableException("The license is in use", true, name, archiveSize, usages.size());
			}

			// COMPUTER LICENSE
			if (isComputerLicense) {

				// is it assigned to another computer?
				Long computerId = ((ComputerLicense) license).getComputerId();
				if (computerId != null) { // its assigned
					final Computer srcComputer = CoreBridge.runNoException(new ComputerFindByIdCmd(computerId));

					// Find out how big the archive size is.
					final FriendComputerUsage fcu = SocialComputerNetworkServices.getInstance().getCPCDestinationUsage(
							srcComputer);
					final long archiveSize = (fcu != null) ? fcu.getArchiveBytes() : 0;

					if (!sameUser) {
						// Not our computer
						// get name - the display name could be empty, i.e. user created during purchase
						String name = srcUser.getDisplayName();
						if (!LangUtils.hasValue(name)) {
							name = srcUser.getUsername();
						}
						throw new LicenseUnavailableException("The license is in use", true, name, archiveSize, 1);
					} else {
						// our own computer name
						throw new LicenseUnavailableException("The license is in use", false, srcComputer.getName(), archiveSize, 1);

					}
				}
			}
		}
	}

	// /**
	// * Transfer this license to the target user if it's moving at all.
	// */
	// private void transferLicenseToUser(License license, User targetUser, CoreSession session) throws CommandException {
	//
	// if (license instanceof BaseUserLicense) {
	//
	// // the license may be anonymouse and have a null userId
	// final boolean sameUser = LangUtils.equals(((BaseUserLicense) license).getUserId(), targetUser.getUserId());
	//
	// // the actual transfer is quite easy once we're allowed to go forward; we only do something if we're switching
	// // ownership to a new user
	// if (!sameUser) {
	// if (license.getSubscriptionId() != null) {
	// // let subscription services handle it
	// this.run(new SubscriptionTransferCmd(license.getSubscriptionId(), targetUser), session);
	// } else {
	// // move the license directly
	// ((BaseUserLicense) license).setUserId(targetUser.getUserId());
	// LicenseDataProvider.save(license);
	// }
	// }
	// }
	// }

	/**
	 * Tie the license to the computer.
	 * 
	 * This only takes effect if the license is a ComputerLicense. No error will be returned if it is not.
	 * 
	 * @param computer
	 * @param license
	 */
	private void assignLicenseToComputer(final License license, final Computer computer) {

		if (license instanceof ComputerLicense) {
			ComputerLicense computerLicense = (ComputerLicense) license;
			computerLicense.setComputerId(computer.getComputerId());
			CoreBridge.update(new LicenseUpdateQuery(license));
		} else {
			// do nothing
		}
	}
}
