package com.code42.computer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.hibernate.Session;

import com.backup42.common.command.ServiceCommand;
import com.backup42.social.SocialComputerNetworkServices;
import com.backup42.social.SocialComputerNetworkServices.SourceUsage;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.UpdateQuery;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.server.destination.Destination;
import com.code42.server.destination.DestinationFindAvailableByUserCmd;
import com.code42.social.FriendComputerUsage;
import com.code42.user.User;
import com.code42.user.UserFindByIdQuery;

/**
 * Swap one destination for another.
 * 
 * @author <a href="mailto:brian@code42.com">Brian Bispala </a>
 */
public class FriendComputerUsageSwapCmd extends DBCmd<Void> {

	private static final Logger log = Logger.getLogger(FriendComputerUsageSwapCmd.class.getName());

	private final long sourceGuid;
	private final long previousTargetGuid;
	private final long newTargetGuid;
	private final Integer newMountPointId;

	public FriendComputerUsageSwapCmd(long sourceGuid, long previousTargetGuid, long newTargetGuid) {
		this(sourceGuid, previousTargetGuid, newTargetGuid, null);
	}

	public FriendComputerUsageSwapCmd(long sourceGuid, long previousTargetGuid, long newTargetGuid,
			Integer newMountPointId) {
		super(false);// NOTE: Do NOT used long-lived sessions for this command!!
		this.sourceGuid = sourceGuid;
		this.previousTargetGuid = previousTargetGuid;
		this.newTargetGuid = newTargetGuid;
		this.newMountPointId = newMountPointId;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		log.info(this.msg("START"));

		this.auth.isSysadmin(session);

		final SocialComputerNetworkServices scns = SocialComputerNetworkServices.getInstance();

		this.db.beginTransaction();
		try {
			final Computer sourceComputer = this.db.find(new ComputerFindByGuidQuery(this.sourceGuid));
			final User sourceUser = this.db.find(new UserFindByIdQuery(sourceComputer.getUserId()));
			final List<Destination> availableTargetDestinations = CoreBridge.run(new DestinationFindAvailableByUserCmd(
					sourceUser, sourceComputer));

			// confirm that the requested destination is offered
			boolean offered = false;
			for (Destination dest : availableTargetDestinations) {
				if (dest.getDestinationGuid() == this.newTargetGuid) {
					offered = true; // found it
					break;
				}
			}
			if (!offered) {
				throw new CommandException(this.msg("Requested target guid is NOT OFFERED!"));
			}

			// ensure that the new destination is offered
			scns.ensureSocialNetwork(sourceUser);

			final FriendComputerUsage prevFCU = this.db.find(new FriendComputerUsageFindBySourceGuidAndTargetGuidQuery(
					this.sourceGuid, this.previousTargetGuid));
			final Computer newTargetComputer = this.db.find(new ComputerFindByGuidQuery(this.newTargetGuid));

			boolean prevIsUsing = prevFCU.isUsing();
			{ // start using the new destination
				// NOTE: this will adjust the config
				final SourceUsage sourceUsage = new SourceUsage(sourceComputer.getGuid(), null, this.newMountPointId, prevFCU
						.getMountVersion());
				scns.manageOfferedComputer(sourceUser, newTargetComputer, true, true, true, false, false, sourceUsage);
			}

			{// update the NEW FCU
				final FriendComputerUsage newFCU = this.db.find(new FriendComputerUsageFindBySourceGuidAndTargetGuidQuery(
						this.sourceGuid, this.newTargetGuid));

				// record the previous target guid
				newFCU.setPreviousTargetComputerGuid(this.previousTargetGuid);
				newFCU.setUsing(prevIsUsing);

				// copy old stats
				populateFCU(newFCU, prevFCU);

				this.db.update(new FriendComputerUsageUpdateQuery(newFCU));
			}

			{ // stop using the old
				final Computer prevTargetComputer = this.db.find(new ComputerFindByGuidQuery(this.previousTargetGuid));
				// NOTE: this will adjust the config
				final SourceUsage sourceUsage = new SourceUsage(sourceComputer.getGuid());
				scns.manageOfferedComputer(sourceUser, prevTargetComputer, true, true, false/* stop */, false, false,
						sourceUsage);
			}

			{// migrate the history data
				this.db.update(new FriendComputerUsageSwapQuery(sourceComputer.getComputerId(), prevFCU.getTargetComputerId(),
						newTargetComputer.getComputerId()));
			}

			log.info(this.msg("Deleting previous FCU. " + prevFCU));
			this.run(new FriendComputerUsageDeleteCmd(prevFCU), session);

			// reconnect and notify
			CoreBridge.getCentralService().runCommand(ServiceCommand.RECONNECT_AUTHORITY, this.sourceGuid, "DBSource");
			SocialComputerNetworkServices.getInstance().notifySocialNetworkOfChange(this.sourceGuid);

			this.db.commit();

		} catch (CommandException e) {
			this.db.rollback();
			throw e;
		} catch (Throwable e) {
			this.db.rollback();
			log.error(this.msg("Unexpected: " + e), e);
		} finally {
			this.db.endTransaction();
		}

		log.info(this.msg("DONE"));
		return null;
	}

	private static final void populateFCU(FriendComputerUsage newFCU, FriendComputerUsage prevFCU) {
		newFCU.setArchiveHoldExpireDate(prevFCU.getArchiveHoldExpireDate());
		newFCU.setSelectedFiles(prevFCU.getSelectedFiles());
		newFCU.setSelectedBytes(prevFCU.getSelectedBytes());
		newFCU.setTodoFiles(prevFCU.getTodoFiles());
		newFCU.setTodoBytes(prevFCU.getTodoBytes());
		newFCU.setArchiveBytes(prevFCU.getArchiveBytes());
		newFCU.setSendRateAverage(prevFCU.getSendRateAverage());
		newFCU.setCompletionRateAverage(prevFCU.getCompletionRateAverage());
		newFCU.setLastActivity(prevFCU.getLastActivity());
		newFCU.setLastCompletedBackup(prevFCU.getLastCompletedBackup());
		newFCU.setLastConnected(prevFCU.getLastConnected());
		newFCU.setLastMaintenanceDate(prevFCU.getLastMaintenanceDate());
		newFCU.setUserMaintenanceDate(prevFCU.getUserMaintenanceDate());
		newFCU.setMaintenanceDuration(prevFCU.getMaintenanceDuration());
		newFCU.setLastCompactDate(prevFCU.getLastCompactDate());
		newFCU.setNumBlocks(prevFCU.getNumBlocks());
		newFCU.setNumBlocksToCompact(prevFCU.getNumBlocksToCompact());
		newFCU.setNumBlocksCompacted(prevFCU.getNumBlocksCompacted());
		newFCU.setNumBlocksFailedChecksum(prevFCU.getNumBlocksFailedChecksum());
		newFCU.setCompactBytesRemoved(prevFCU.getCompactBytesRemoved());
		newFCU.setCompactTotalBytes(prevFCU.getCompactTotalBytes());
		newFCU.setAlertState(prevFCU.getAlertState());
		newFCU.setBackupSetVersion(prevFCU.getBackupSetVersion());
		newFCU.setBackupSetCount(prevFCU.getBackupSetCount());
		newFCU.setBackupSetData(prevFCU.getBackupSetData());
	}

	private class FriendComputerUsageSwapQuery extends UpdateQuery<Void> {

		private final long sourceComputerId;
		private final long previousTargetComputerId;
		private final long newTargetComputerId;

		public FriendComputerUsageSwapQuery(long sourceComputerId, long previousTargetComputerId, long newTargetComputerId) {
			super();
			this.sourceComputerId = sourceComputerId;
			this.previousTargetComputerId = previousTargetComputerId;
			this.newTargetComputerId = newTargetComputerId;
		}

		@Override
		public Void query(Session session) throws DBServiceException {
			final Connection c = session.connection();
			try {
				this.tArchiveRecord(c);
				return null;
			} catch (SQLException e) {
				throw new DBServiceException(FriendComputerUsageSwapCmd.this.msg("Exception swapping FCU target! " + e), e);
			}
		}

		private void tArchiveRecord(final Connection c) throws SQLException {
			PreparedStatement ps = c
					.prepareStatement("update t_archive_record set target_computer_id = ? where source_computer_id = ? and target_computer_id = ?");
			ps.setLong(1, this.newTargetComputerId);
			ps.setLong(2, this.sourceComputerId);
			ps.setLong(3, this.previousTargetComputerId);
			int num = ps.executeUpdate();
			log.info(FriendComputerUsageSwapCmd.this.msg("archive record for org. num=" + num));
			ps.close();
		}

	}

	private String msg(String s) {
		return "FCUSWAP:: " + s + "; sourceGuid=" + this.sourceGuid + ", previousTargetGuid=" + this.previousTargetGuid
				+ ", newTargetGuid=" + this.newTargetGuid + ", newMountPointId=" + this.newMountPointId;
	}
}
