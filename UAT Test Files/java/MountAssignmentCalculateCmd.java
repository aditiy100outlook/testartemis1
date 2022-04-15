/**
 * <a href="http://www.code42.com">(c)Code 42 Software, Inc.</a>
 */
package com.code42.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.code42.computer.Computer;
import com.code42.core.CommandException;
import com.code42.core.auth.AuthenticationException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.exception.DebugRuntimeException;
import com.code42.logging.Logger;
import com.code42.server.destination.Destination;
import com.code42.server.destination.DestinationFindByGuidQuery;
import com.code42.server.destination.ProviderDestination;
import com.code42.server.mount.MountPoint;
import com.code42.server.mount.MountPointFindByDestinationGuidQuery;
import com.code42.server.mount.MountPointFindByDestinationQuery;
import com.code42.server.mount.MountPointFindByIdQuery;
import com.code42.utils.LangUtils;
import com.code42.utils.SystemProperties;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

/**
 * Get a mount for your computer. This method takes all of the settings into account and returns an authoritative
 * answer.
 */
public class MountAssignmentCalculateCmd extends DBCmd<MountPoint> {

	private static final Logger log = Logger.getLogger(MountAssignmentCalculateCmd.class);

	private interface Property {

		String destMPAssignment = "destination.mountPointAssignment";
	}

	private final Computer sourceComputer;
	private final Computer targetComputer;

	public MountAssignmentCalculateCmd(Computer sourceComputer, Computer targetComputer) {
		super();
		this.sourceComputer = sourceComputer;
		this.targetComputer = targetComputer;
	}

	@Override
	public MountPoint exec(CoreSession session) throws CommandException {

		// no auth needed; this is a read-only op called by system commands

		/*
		 * Only a Master node is allowed to choose mount assignments. We might request help from other nodes, but this API
		 * itself may only be run on a Master.
		 */
		if (!this.env.isMaster()) {
			throw new AuthenticationException("Mount point assignment on master ONLY! sourceComputer=" + this.sourceComputer
					+ ", targetComputer=" + this.targetComputer);
		}

		// we can only choose mounts for destinations, validate that we were given a proper id
		final long destinationGuid = this.targetComputer.getGuid();
		final boolean isDestination = this.serverService.getCache().isDestination(destinationGuid);
		if (!isDestination) {
			throw new CommandException("TargetComputer is not a Destination; unable to choose a mount", this.targetComputer);
		}

		/*
		 * DEV/TEST OVERRIDE
		 * 
		 * There is a !prd override system prop that allows testers to specify the default mount for a given destination. If
		 * !prd and that override is present, then we'll respect it ahead of all other .logic
		 */
		if (!SystemProperties.isPrdEnv()) {
			Integer mountId = this.getDevMountPoint(destinationGuid);
			if (mountId != null) {
				MountPoint mount = this.db.find(new MountPointFindByIdQuery(mountId));
				if (mount != null) {
					log.info("MP ASSIGN: Using dev mount point override", this.sourceComputer, this.targetComputer, mount);
					return mount;

				} else {
					log.info("DEV override mount point NOT FOUND! destinationGuid={}, mpId={}", destinationGuid, mountId);
				}
			}
		}

		/*
		 * PROVIDER DESTINATION OVERRIDE
		 * 
		 * Provider Destinations only have one Mount, by definition. If that's what we're dealing with, then just do the
		 * assignment.
		 */
		final Destination destination = this.db.find(new DestinationFindByGuidQuery(destinationGuid));
		if (destination instanceof ProviderDestination) {
			final List<MountPoint> mounts = this.db.find(new MountPointFindByDestinationQuery(destination.getDestinationId()));
			if (mounts.isEmpty()) {
				throw new CommandException("ProviderDestination has no mounts. pd={}, sourceComputer=", destination,
						this.sourceComputer);
			}

			final MountPoint mount = LangUtils.selectFromList(mounts);
			log.info("MP ASSIGN: Assigning ProviderDestination override", this.sourceComputer, this.targetComputer, mount);

			return mount;
		}

		/*
		 * REQUEST ASSIGNMENT CALCULATION
		 * 
		 * Calculate an appropriate mount assignment by relying on the assignment calculator. This may fail.
		 */
		calculate: try {

			final Integer mountId = this.run(new MountAssignmentAlgorithmCmd(destinationGuid), session);
			if (mountId == null) {
				log.warn("Calculator provided a null mountId", this.sourceComputer, this.targetComputer);
				// break instead of throwing an exception. we want to continue with the algorithm and choose a mount randomly
				// that will satisfy this request
				break calculate;
			}

			final MountPoint mount = this.db.find(new MountPointFindByIdQuery(mountId));
			if (mount == null) {
				log.warn("Calculator specified an invalid mountId={}", mountId, this.sourceComputer, this.targetComputer);
				break calculate;
			}

			log.info("MP ASSIGN: Calculated assignment", this.sourceComputer, this.targetComputer, mount);
			return mount;

		} catch (Exception e) {
			log.info("Failed to receive valid mount assignment from Calculator. Algorithm will continue", e,
					this.sourceComputer, this.targetComputer);
		}

		/*
		 * ALGORITHM FAILURE: RANDOM SELECTION
		 * 
		 * Asking a member of the destination has failed. Choose randomly from a set of all Mounts belonging to the
		 * Destination. Note that we want to look up the Mounts from the db; we might be here due to an error in the cache.
		 */
		final List<MountPoint> mounts = this.db.find(new MountPointFindByDestinationGuidQuery(destinationGuid));
		final MountPoint mount = this.selectMountAtRandom(mounts);
		if (mount != null) {
			log.info("MP ASSIGN: Calculated random mount assignment", this.sourceComputer, this.targetComputer, mount);
			return mount;
		}

		/*
		 * Failure to find a mount is unacceptable. Something has gone very wrong.
		 */
		throw new CommandException("MP ASSIGN: Failed to find a mountPoint for targetComputer={}", this.targetComputer);
	}

	/**
	 * Retrieve the property-override for this destination, if such a thing exists. The property takes the form:
	 * 
	 * destination.mountPointAssignment destinationGuid:mountId[,...]
	 * 
	 * @param requestedDestGuid
	 * @return
	 * @throws CommandException
	 */
	private Integer getDevMountPoint(long requestedDestGuid) {

		if (SystemProperties.isPrdEnv()) {
			throw new DebugRuntimeException("Dev Mount config not valid in production", requestedDestGuid);
		}

		try {
			String mountPointMapCsv = SystemProperties.getOptional(Property.destMPAssignment);
			if (LangUtils.hasValue(mountPointMapCsv)) {
				String[] mappings = mountPointMapCsv.split(",");
				for (String mapping : mappings) {
					mapping = mapping.trim();
					String[] items = mapping.split(":");
					long destGuid = Long.parseLong(items[0]);
					int mpId = Integer.parseInt(items[1]);
					if (destGuid == requestedDestGuid) {
						return mpId;
					}
				}
			}
		} catch (Exception e) {
			throw new DebugRuntimeException("Exception parsing destination mount point mapping! requestedDestGuid={}",
					requestedDestGuid, e);
		}

		// nothing was found
		return null;
	}

	/**
	 * Select a mount at random from this list. We will prefer to choose one that is accepting new users, but if there are
	 * none of those we'll choose from the entire list.
	 * 
	 * @param mounts
	 * @return
	 */
	private MountPoint selectMountAtRandom(final List<MountPoint> mounts) {

		// get a list of mounts that are accepting new users
		final Collection<MountPoint> newUserMounts = Collections2.filter(mounts, new Predicate<MountPoint>() {

			public boolean apply(MountPoint mount) {
				return mount.getBalanceNewUsers();
			}
		});

		// we'd prefer to choose from the list of mounts that are accepting new users. if there are none of those, we'll
		// just select one from the entire list
		final MountPoint mount;
		if (LangUtils.hasElements(newUserMounts)) {
			mount = LangUtils.selectFromList(new ArrayList<MountPoint>(newUserMounts));

		} else {
			// otherwise, randomly choose from the whole list
			mount = LangUtils.selectFromList(mounts);
		}

		return mount;
	}
}
