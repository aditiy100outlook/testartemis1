package com.code42.user;

import java.util.ArrayList;
import java.util.List;

import com.backup42.common.ComputerType;
import com.backup42.social.SocialComputerNetworkServices;
import com.backup42.social.SocialComputerNetworkServices.SourceUsage;
import com.code42.computer.Computer;
import com.code42.computer.ComputerFindByUserCmd;
import com.code42.computer.ComputerSso;
import com.code42.computer.FriendComputerUsageDeleteCmd;
import com.code42.computer.FriendComputerUsageFindByUserCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.businessobjects.BusinessObjectsException;
import com.code42.core.businessobjects.IBusinessObjectsService;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.destination.DestinationFindAvailableByOrgCmd;
import com.code42.server.destination.Destination;
import com.code42.server.destination.DestinationFindByIdQuery;
import com.code42.social.FriendComputerUsage;
import com.code42.user.destination.UserDestination;
import com.code42.user.destination.UserDestinationFindByUserIdQuery;
import com.code42.user.destination.UserDestinationUpdateCmd;
import com.google.inject.Inject;

/**
 * Move a user from one destination to another. This only applies in the CPC cluster (green/blue) where we only offer
 * one destination. If the consumer product is ever updated to offer multiple cloud destinations, this cmd should not be
 * used.
 * 
 * This command will do the following: <br/>
 * - Remove any FCU's for cloud backups in the current destination for the given user, effectively removing their
 * archive <br/>
 * - Update the t_user_destination row for this user to point to contain the new (target) destination <br/>
 * - Un-offer the current destination through the social service <br/>
 * - Offer the new destination through the social service
 * 
 * @author ahelgeso
 * 
 */
public class UserMoveDestinationCmd extends DBCmd<UserDestination> {

	@Inject
	public IBusinessObjectsService bos;

	private static final Logger log = LoggerFactory.getLogger(UserMoveDestinationCmd.class);

	private User user;
	private String username;
	private final int oldDestinationId;
	private final int newDestinationId;

	private final SocialComputerNetworkServices socialService = SocialComputerNetworkServices.getInstance();

	/**
	 * If a username is provided it will be looked up (during exec) to obtain the user object. If it cannot be found, a
	 * CommandException will be given.
	 * 
	 * @param username
	 * @param oldDestinationId
	 * @param newDestinationId
	 * @throws CommandException
	 */
	public UserMoveDestinationCmd(String username, final int oldDestinationId, final int newDestinationId) {

		this.username = username;
		this.oldDestinationId = oldDestinationId;
		this.newDestinationId = newDestinationId;
	}

	/**
	 * Move user to use destination destinationId
	 * 
	 * @param userId - id of the user to move
	 * @param destinationId - id of the new destination for the user
	 */
	public UserMoveDestinationCmd(final User user, final int oldDestinationId, final int newDestinationId) {
		this.user = user;
		this.newDestinationId = newDestinationId;
		this.oldDestinationId = oldDestinationId;
	}

	@Override
	public UserDestination exec(CoreSession session) throws CommandException {
		this.validateCmd(session);

		// Verify the existing destination is available for this user.
		List<UserDestination> userAvailDests = this.db.find(new UserDestinationFindByUserIdQuery(this.user.getUserId()));
		List<UserDestination> currentDestinationUsages = new ArrayList<UserDestination>();

		for (UserDestination userDest : userAvailDests) {
			if (userDest.getDestinationId() == this.oldDestinationId) {
				currentDestinationUsages.add(userDest);
			}
		}

		if (currentDestinationUsages.size() == 0) {
			throw new CommandException(
					"DEST MOVE:: Current user destination with id {} cannot be found. Skipping UserMoveDestination.",
					this.oldDestinationId);
		}

		// Verify the target destination is available to the org
		User user = this.run(new UserFindByIdCmd(this.user.getUserId()), session);
		if (user == null) {
			throw new CommandException("DEST MOVE:: Failed to find user with id={}. Cancelling user destination move.",
					this.user.getUserId());
		}

		List<Destination> orgDests = this.run(new DestinationFindAvailableByOrgCmd(user.getOrgId()), session);
		Destination newDestination = null;

		for (Destination orgDest : orgDests) {
			if (orgDest.getDestinationId() == this.newDestinationId) {
				newDestination = orgDest;
				break;
			}
		}

		if (newDestination == null) {
			throw new CommandException(
					"DEST MOVE:: Target destination {} for move is not offered by orgId {} for userId {}.",
					this.newDestinationId, user.getOrgId(), this.user.getUserId());
		}

		this.db.beginTransaction();
		try {
			log.info("DEST MOVE:: Moving user {} to destination '{}'", user.getUsername(), newDestination
					.getDestinationName());
			// Find the FCU's for the user and remove any for cloud destinations. Also remove the archive records
			this.removeCloudFCUs(session);

			// Handle the un-offering and offering of destinations
			UserDestination updatedUserDest = this.manageOfferings(session, currentDestinationUsages, newDestination);

			this.db.commit();

			log.info("DEST MOVE:: Completed destination move for user {} to destination {}", user.getUsername(),
					newDestination.getDestinationName());
			return updatedUserDest;
		} catch (Exception e) {
			this.db.rollback();
			log.warn("DEST MOVE:: Error moving user {} to new destination.", this.user.getUsername());
			throw new CommandException(e);
		} finally {
			this.db.endTransaction();
		}
	}

	/**
	 * Remove the FCU entries for this user's cloud backups. This will not touch any local or peer FCU entries.
	 * 
	 * This operation effectively removes the user's archives in their current destination.
	 * 
	 * @param session
	 * @throws CommandException
	 * @throws BusinessObjectsException
	 */
	private void removeCloudFCUs(CoreSession session) throws CommandException, BusinessObjectsException {
		List<FriendComputerUsage> fcus = this.run(new FriendComputerUsageFindByUserCmd(this.user.getUserId()), session);

		Destination oldDestination = this.db.find(new DestinationFindByIdQuery(this.oldDestinationId));
		Long oldDestinationComputerId = oldDestination.getComputer().getComputerId();

		for (FriendComputerUsage fcu : fcus) {
			long targetId = fcu.getTargetComputerId();

			// Only orphan those computers who are not already in the target destination
			if (oldDestinationComputerId.equals(targetId)) {
				Long targetComputerGuid = fcu.getTargetComputerGuid();
				ComputerSso targetComputer = this.bos.getComputerByGuid(targetComputerGuid);

				// Don't delete fcus for non-server backups
				if (targetComputer.getType() != ComputerType.SERVER) {
					continue;
				}

				log.info("DEST MOVE:: Removing FCU for {} with target guid={}", fcu.getSourceComputerGuid(), fcu
						.getTargetComputerGuid());
				// We intentionally elevate to admin for this command to avoid giving invoking user the powerful system setting
				// permission
				this.run(new FriendComputerUsageDeleteCmd(fcu), this.auth.getAdminSession());
			} else {

				log.info("DEST MOVE:: SKipped computer {} with who is already in the target destination", fcu
						.getSourceComputerGuid());
			}
		}
	}

	/**
	 * Manage the process of un-offer the current destination and offering the new destination. In this process, the
	 * user's t_user_destination row will be updated with the new destinationId. After the offering is completed, the
	 * user's computer's will be notified but backup in the new destination will not auto-start.
	 * 
	 * @param session
	 * @param currentDest - The users current destination
	 * @param newDestination - The destination to move them to
	 * @return
	 * @throws CommandException
	 */
	private UserDestination manageOfferings(CoreSession session, List<UserDestination> currentDests,
			Destination newDestination) throws CommandException {
		// Get the actual computer object for the current (soon to be old) destination. The full Computer object is
		// required by the social service.
		UserDestination currentUserDest = currentDests.get(0);
		Destination oldDestination = this.db.find(new DestinationFindByIdQuery(currentUserDest.getDestinationId()));
		Computer oldTargetComputer = oldDestination.getComputer();

		if (oldTargetComputer == null) {
			throw new CommandException(
					"DEST MOVE:: Failed to find current destination (id={}) before move. Cancelling move.", this.oldDestinationId);
		}

		// Un-offer the current friendship
		log.info("DEST MOVE:: Un-offering destination '{}' to all computers for user {}", oldTargetComputer.getName(),
				this.user.getEmail());

		// We only want to modify the usage for actual computers, not local or peer backups
		List<Computer> userComputers = this.run(new ComputerFindByUserCmd(this.user.getUserId()), session);
		List<SourceUsage> cloudUsages = new ArrayList<SourceUsage>();
		for (Computer comp : userComputers) {
			if (comp.getType() == ComputerType.COMPUTER) {
				cloudUsages.add(new SourceUsage(comp.getGuid()));
			}
		}
		SourceUsage[] cloudUsagesArray = cloudUsages.toArray(new SourceUsage[] {});

		this.socialService.manageOfferedComputer(this.user, oldTargetComputer, false /* unoffer */, true, false, false,
				cloudUsagesArray);

		// Update existing user destination rows. The offering below will not work if the user doesn't have this destination
		// available.
		UserDestination updatedUserDest = null;
		for (UserDestination userDest : currentDests) {
			UserDestinationUpdateCmd.Builder updateBuilder = new UserDestinationUpdateCmd.Builder(userDest
					.getUserDestinationId());
			updateBuilder.setDestinationId(newDestination.getDestinationId());
			updatedUserDest = this.run(updateBuilder.build(), session);
		}

		// Offer the new friendship but don't auto-start backup
		Computer newOfferedComputer = newDestination.getComputer();
		log.info("DEST MOVE:: Offering destination '{}' to all computers for user {}", newOfferedComputer.getName(),
				this.user.getEmail());
		this.socialService.manageOfferedComputer(this.user, newOfferedComputer, true /* offer */, false, false, true,
				cloudUsagesArray);

		return updatedUserDest;
	}

	/**
	 * Some validation before we start doing the actual work. <br/>
	 * <u>Required Permissions:</u><br />
	 * C42PermissionApp.Functional.USER_DEST_MOVE<br/>
	 * C42PermissionApp.AllComputer.ALL<br />
	 * 
	 * @param session
	 * @throws CommandException
	 */
	private void validateCmd(CoreSession session) throws CommandException {
		if (!this.env.isCpCentral()) {
			log.error("DEST MOVE:: User destination move is restricted on this server");
			throw new CommandException("User destination moving is restricted on this server");
		}

		// Check the callers authorization
		try {
			this.auth.isAuthorized(session, C42PermissionApp.Functional.USER_DEST_UPDATE);
			this.auth.isAuthorized(session, C42PermissionApp.AllComputer.ALL);
		} catch (UnauthorizedException ue) {
			throw new CommandException("Insufficient permissions to perform UserMoveDestination");
		}

		if (this.oldDestinationId == this.newDestinationId) {
			throw new CommandException("DEST MOVE:: old and new destination ids are the same ({}), move is a no-op",
					this.oldDestinationId);
		}

		// If they provided us a username we have to find the user now
		if (this.user == null) {
			if (this.username == null) {
				throw new CommandException("No username provided for user destination move");
			}
			UserFindByUsernameQuery query = new UserFindByUsernameQuery(this.username);
			List<User> users = this.db.find(query);

			if (users == null || users.size() == 0) {
				throw new CommandException("User {} could not be found. Cannot perform user destination move", this.username);
			}

			// In our cluster there should only be one here anyways
			this.user = users.get(0);
		}
	}
}
