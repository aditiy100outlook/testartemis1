package com.code42.user;

import java.util.ArrayList;
import java.util.List;

import com.backup42.CpcConstants;
import com.backup42.account.AccountServices;
import com.backup42.common.command.ServiceCommand;
import com.backup42.computer.ConfigServices;
import com.backup42.computer.EncryptionKeyServices;
import com.backup42.history.CpcHistoryLogger;
import com.backup42.server.MasterServices;
import com.backup42.social.SocialComputerNetworkServices;
import com.code42.archiverecord.ArchiveSummary;
import com.code42.archiverecord.ArchiveSummaryRollup;
import com.code42.archiverecord.ArchiveSummaryUpdateQuery;
import com.code42.archivesummary.ArchiveSummaryFindByUserCmd;
import com.code42.backup.SecureDataKey;
import com.code42.backup.SecurityKeyType;
import com.code42.backup.archiverecord.ArchiveSummaryRollupFindByUserQuery;
import com.code42.backup.archiverecord.ArchiveSummaryRollupUpdateQuery;
import com.code42.backup.central.ICentralService;
import com.code42.computer.Computer;
import com.code42.computer.ComputerFindByUserCmd;
import com.code42.computer.ComputerUpdateCmd.Error;
import com.code42.computer.DataEncryptionKey;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.directory.Directory;
import com.code42.core.directory.DirectoryEntry;
import com.code42.core.impl.DBCmd;
import com.code42.directory.DirectoryFindAllByOrgCmd;
import com.code42.directory.DirectoryFindUserAnyCmd;
import com.code42.directory.DirectoryFindUserAnyCmd.Builder;
import com.code42.hibernate.aftertx.AfterTxRunnable;
import com.code42.hibernate.aftertx.IAfterTxRunnable.Priority;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.BackupOrg;
import com.code42.org.OrgDto;
import com.code42.org.OrgDtoFindByCriteriaCmd;
import com.code42.org.OrgFindByNameQuery;
import com.code42.org.OrgSso;
import com.code42.org.OrgSsoFindByOrgIdCmd;
import com.code42.org.destination.OrgDestination;
import com.code42.org.destination.OrgDestinationFindAvailableByOrgCmd;
import com.code42.server.destination.Destination;
import com.code42.server.destination.DestinationFindByIdQuery;
import com.code42.utils.Pair;
import com.google.inject.Inject;

/**
 * Command to move a user to a different org. This command handles all cache invalidation and any attendant real-time
 * stats updates. The user's ArchiveSummary and ArchiveSummaryRollup entries (if any) move with them.
 */
public class UserMoveCmd extends DBCmd<Pair<UserMoveCmd.Result, Integer>> {

	public static enum Result {
		SUCCESS, //
		NONEXISTENT_USER, //
		NONEXISTENT_ORG, //
		MULTIPLE_ORGS, // the target org name matches multiple orgs
		SAME_ORG, //
		INVALID_USER, //
		THROWABLE, //
		NOT_IN_LDAP, //
		INCOMPATIBLE_ORGS,
		DIRECTORY_COMMUNICATION_ERROR,
		HOSTED_ORG
	}

	private static final Logger log = LoggerFactory.getLogger(UserMoveCmd.class);

	private final int userId;
	private final boolean simulate;

	private Integer targetOrgId = null;
	private String targetOrgName;

	private User user;
	private OrgDto targetOrg;

	@Inject
	private ICentralService centralService;

	public UserMoveCmd(int userId, int targetOrgId) {
		this(userId, targetOrgId, false/* false=do it for real */);
	}

	public UserMoveCmd(int userId, String targetOrgName) {
		this(userId, targetOrgName, false/* false=do it for real */);
	}

	/**
	 * Used by the directory synchronizing process.
	 */
	public UserMoveCmd(int userId, int targetOrgId, boolean simulate) {
		this.userId = userId;
		this.targetOrgId = targetOrgId;
		this.simulate = simulate;
	}

	/**
	 * Used by the directory synchronizing process.
	 */
	public UserMoveCmd(int userId, String targetOrgName, boolean simulate) {
		this.userId = userId;
		this.targetOrgName = targetOrgName;
		this.simulate = simulate;
	}

	@Override
	public Pair<Result, Integer> exec(CoreSession session) throws CommandException {

		/*
		 * Check the userId
		 */
		this.user = this.runtime.run(new UserFindByIdCmd(this.userId), session);
		if (this.user == null || this.user.getUserId() == null) {
			return new Pair(Result.NONEXISTENT_USER, this.targetOrgId);
			// throw new CommandException("User does not exist; userId: {}", this.userId);
		}
		if (this.user.getUserId() == CpcConstants.Orgs.ADMIN_ID) {
			// We cannot move this user.
			return new Pair(Result.INVALID_USER, this.targetOrgId);
		}

		/*
		 * Populate the targetOrgId if not already.
		 */
		if (this.targetOrgId == null) {
			OrgSso currentOrgSso = this.runtime.run(new OrgSsoFindByOrgIdCmd(this.user.getOrgId()), this.auth
					.getAdminSession());
			if (currentOrgSso == null) {
				throw new CommandException("Null SSO obtained for org with ID " + this.user.getOrgId());
			}
			String currentOrgName = currentOrgSso.getOrgName();
			if (this.targetOrgName == null || currentOrgName.equals(this.targetOrgName)) {
				return new Pair(Result.SAME_ORG, currentOrgSso.getOrgId());
			}
			// At this point, the org names are different, so see if we can find the new one
			List<BackupOrg> newOrgs = this.db.find(new OrgFindByNameQuery(this.targetOrgName));
			if (newOrgs.size() == 1) {
				this.targetOrgId = newOrgs.get(0).getOrgId();
			} else if (newOrgs.isEmpty()) {
				return new Pair(Result.NONEXISTENT_ORG, 0);
			} else if (newOrgs.size() > 1) {
				return new Pair(Result.MULTIPLE_ORGS, 0);
			}
		}

		assert this.targetOrgId != null : "targetOrgId is null!";

		int oldOrgId = this.user.getOrgId();

		// If the user is already within the target, there's no point in continuing.
		if (oldOrgId == this.targetOrgId) {
			return new Pair(Result.SAME_ORG, this.targetOrgId);
		}

		OrgDtoFindByCriteriaCmd.Builder targetBuilder = new OrgDtoFindByCriteriaCmd.Builder();
		targetBuilder.orgId(this.targetOrgId);
		targetBuilder.includeSettings();
		this.targetOrg = this.runtime.run(targetBuilder.build(), session).getOne().get(0);
		if (this.targetOrg == null) {
			return new Pair(Result.NONEXISTENT_ORG, this.targetOrgId);
			// throw new CommandException("Target org does not exist: {}", this.targetOrgId);
		}

		if (MasterServices.getInstance().isHostedOrg(this.targetOrg.getOrgId())) {
			return new Pair(Result.HOSTED_ORG, this.targetOrgId);
		}

		OrgDtoFindByCriteriaCmd.Builder sourceBuilder = new OrgDtoFindByCriteriaCmd.Builder();
		sourceBuilder.orgId(this.user.getOrgId());
		sourceBuilder.includeSettings();
		OrgDto sourceOrg = this.runtime.run(sourceBuilder.build(), session).getOne().get(0);
		// Check that the orgs are of the same type
		if (this.targetOrg.getType() != sourceOrg.getType()) {
			return new Pair(Result.INCOMPATIBLE_ORGS, this.targetOrgId);
		}

		// Make certain the calling user has authority to change both the user account and the org
		// into which it is being moved.
		this.runtime.run(new IsUserManageableCmd(this.user, C42PermissionApp.User.UPDATE), session);
		this.runtime.run(new IsOrgManageableCmd(this.targetOrg.getOrgId(), C42PermissionApp.Org.UPDATE_BASIC), session);

		// safety check
		if (MasterServices.getInstance().isHostedOrg(this.targetOrg.getOrgId())) {
			throw new CommandException("The user may not be moved to a slave org structure.");
		}

		List<Directory> directories = this.runtime.run(new DirectoryFindAllByOrgCmd(this.targetOrgId), session);
		// Skip this section if there is no external directory for this user
		if (!AccountServices.getInstance().isDirLocal(directories)) {
			DirectoryEntry entry = null;
			Builder builder = new DirectoryFindUserAnyCmd.Builder(this.user.getUsername());
			builder.directories(directories);
			entry = this.run(builder.build(), session);
			if (entry == null) {
				return new Pair(Result.NOT_IN_LDAP, this.targetOrgId);
			}
		}

		if (this.simulate) {
			// We've gone through the main checks, so call it good without doing the work.
			return new Pair(Result.SUCCESS, this.targetOrgId);
		}

		this.db.beginTransaction();
		try {

			OrgDtoFindByCriteriaCmd.Builder builder = new OrgDtoFindByCriteriaCmd.Builder();
			builder.orgId(this.targetOrgId);
			builder.includeSettings();
			OrgDto org = this.runtime.run(builder.build(), session).getOne().get(0);

			// Change the SecurityKeyType for the move
			SecurityKeyType newSecurityKeyType = SecurityKeyType.fromString(org.getSettings().getSecurityKeyType());
			if (org.getSettings().isSecurityKeyLocked()) {
				final DataEncryptionKey key = this.db.find(new DataEncryptionKeyFindByUserQuery(this.userId));
				// Make certain they aren't down-grading security.
				if (newSecurityKeyType.ordinal() > key.getSecurityKeyType().ordinal()) {
					SecureDataKey secureKey = null;
					if (newSecurityKeyType.equals(SecurityKeyType.PrivatePassword)) {
						secureKey = new SecureDataKey(key.getSecureDataKey().getBytes());
					}
					EncryptionKeyServices.getInstance().storeKey(this.userId, null, secureKey);
				} else if (newSecurityKeyType.equals(key.getSecurityKeyType())) {
					// No change, do nothing
				} else {
					// Down-grade, this is not allowed
					throw new CommandException(Error.SECURITY_KEY_DOWNGRADE, "Unable to set security key type for userId="
							+ this.userId + ". Can't downgrade security from " + key.getSecureDataKey() + " to " + newSecurityKeyType);
				}
			}

			// Change the user's data quota
			long userDefaultQuota = org.getSettings().getDefaultUserMaxBytes();
			this.user.setMaxBytes(userDefaultQuota);

			// Change the user's orgId
			this.user.setOrgId(this.targetOrgId);

			this.user = this.runtime.run(new UserValidateCmd(this.user), session);
			this.db.update(new UserUpdateQuery(this.user));

			// Adjust social network based on offered destinations. Must be done before we adjust config.
			this.adjustSocialNetwork(sourceOrg, session);

			// Change the user's config
			ConfigServices.getInstance().publishOrgConfigToUser(this.targetOrgId, this.user);

			// Move their ArchiveSummary rows
			List<ArchiveSummary> summaries = this.run(new ArchiveSummaryFindByUserCmd(this.user.getUserId()), session);
			for (ArchiveSummary summary : summaries) {
				summary.setOrgId(this.targetOrgId);
				this.db.update(new ArchiveSummaryUpdateQuery(summary));
			}

			// Move their ArchiveSummaryRollup row
			ArchiveSummaryRollup rollup = this.db.find(new ArchiveSummaryRollupFindByUserQuery(this.user.getUserId()));
			if (rollup != null) {
				rollup.setOrgId(this.targetOrgId);
				this.db.update(new ArchiveSummaryRollupUpdateQuery(rollup));
			}

			this.db.afterTransaction(new UserPublishMoveCmd(this.user, oldOrgId), session);

			this.db.afterTransaction(new AfterTxRunnable(Priority.NORMAL) {

				public void run() {
					// Let all my computers know that the key has changed.
					UserMoveCmd.this.centralService.getPeer().sendServiceCommandToUser(UserMoveCmd.this.userId,
							ServiceCommand.REAUTHORIZE);
				}
			});

			this.db.commit();
		} catch (CommandException e) {
			this.db.rollback();
			throw e;
		} catch (Throwable e) {
			this.db.rollback();
			log.error("Unexpected: ", e);
			return new Pair(Result.THROWABLE, 0);
		} finally {
			this.db.endTransaction();
		}

		int sourceOrgId = this.user.getOrgId();
		String sourceOrgName = this.runtime.run(new OrgSsoFindByOrgIdCmd(sourceOrgId), session).getOrgName();

		CpcHistoryLogger.info(session, "moved user:{}/{} from org:{}/{} to org:{}/{}", this.userId,
				this.user.getUsername(), sourceOrgId, sourceOrgName, this.targetOrgId, this.targetOrg.getOrgName());

		return new Pair(Result.SUCCESS, this.targetOrgId);
	}

	private void adjustSocialNetwork(OrgDto sourceOrg, CoreSession session) throws CommandException {
		final SocialComputerNetworkServices networkServices = SocialComputerNetworkServices.getInstance();

		final List<Integer> oldDestinationIds = this.getOrgDestinationIds(sourceOrg.getOrgId(), session);
		final List<Integer> newDestinationIds = this.getOrgDestinationIds(this.targetOrgId, session);

		final List<Integer> removeDestinationIds = new ArrayList<Integer>(oldDestinationIds);
		removeDestinationIds.removeAll(newDestinationIds);

		final List<Integer> addDestinationIds = new ArrayList<Integer>(newDestinationIds);
		addDestinationIds.removeAll(oldDestinationIds);

		final boolean adjustUsing = false;
		final boolean useForBackup = true; // not used, default to true for safety
		final boolean notify = false; // making lots of changes, do it once after user move is all done

		// Stop offering
		boolean offered = false;
		for (Integer destinationId : removeDestinationIds) {
			final Destination destination = this.db.find(new DestinationFindByIdQuery(destinationId));
			final Computer destComputer = destination.getComputer();
			networkServices.manageOfferedComputer(this.user, destComputer, offered, adjustUsing, useForBackup, notify);
		}

		// Start offering
		offered = true;
		for (Integer destinationId : addDestinationIds) {
			final Destination destination = this.db.find(new DestinationFindByIdQuery(destinationId));
			final Computer destComputer = destination.getComputer();
			networkServices.manageOfferedComputer(this.user, destComputer, offered, adjustUsing, useForBackup, notify);
		}

		// Adjustments for self offering
		if (sourceOrg.getSettings().getAutoOfferSelf() != this.targetOrg.getSettings().getAutoOfferSelf()) {
			List<Computer> computers = this.runtime.run(new ComputerFindByUserCmd(this.userId), session);
			for (Computer comp : computers) {
				if (!comp.isChild()) {
					networkServices.manageOfferedComputer(this.user, comp, this.targetOrg.getSettings().getAutoOfferSelf(),
							adjustUsing, useForBackup, notify);
				}
			}
		}

		// Adjustments for local folders
		if (!this.targetOrg.getSettings().isAllowLocalFolders()) {
			offered = true;
			List<Computer> computers = this.runtime.run(new ComputerFindByUserCmd(this.userId), session);
			for (Computer comp : computers) {
				if (comp.isChild()) {
					networkServices.deactivateComputer(comp, "Backup to local folders removed.");
					networkServices.manageOfferedComputer(this.user, comp, offered, adjustUsing, useForBackup, notify);
				}
			}
		}
		// Check setting and return if enabled
		// Look up computers being used

	}

	// Check setting and return if enabled
	// Look up computers being used

	private List<Integer> getOrgDestinationIds(int orgId, CoreSession session) throws CommandException {
		final List<Integer> destinationIds = new ArrayList<Integer>();

		final List<OrgDestination> destinations = this.runtime.run(new OrgDestinationFindAvailableByOrgCmd(orgId), session);
		for (OrgDestination dest : destinations) {
			destinationIds.add(dest.getDestinationId());
		}

		return destinationIds;
	}
}
