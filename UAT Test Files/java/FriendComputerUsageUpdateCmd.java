/*
 * <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.computer;

import java.util.Date;

import com.backup42.history.CpcHistoryLogger;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.social.FriendComputerUsage;

/**
 * Validate and update a few fields on the given fcu.<br>
 */
public class FriendComputerUsageUpdateCmd extends DBCmd<FriendComputerUsage> {

	private static final Logger log = LoggerFactory.getLogger(FriendComputerUsageUpdateCmd.class);

	private FriendComputerUsage fcu;
	private long sourceComputerId;
	private long targetComputerId;
	private final Date archiveHoldExpireDate;

	public FriendComputerUsageUpdateCmd(FriendComputerUsage fcu, Date archiveHoldExpireDate) {
		this(fcu.getSourceComputerId(), fcu.getTargetComputerId(), archiveHoldExpireDate);
		this.fcu = fcu;
	}

	public FriendComputerUsageUpdateCmd(long sourceComputerId, long targetComputerId, Date archiveHoldExpireDate) {
		super();
		this.sourceComputerId = sourceComputerId;
		this.targetComputerId = targetComputerId;
		this.archiveHoldExpireDate = archiveHoldExpireDate;
	}

	/**
	 * @return null if the computerId does not exist.
	 */
	@Override
	public FriendComputerUsage exec(CoreSession session) throws CommandException {

		this.runtime.run(new IsComputerManageableCmd(this.sourceComputerId, C42PermissionApp.Computer.UPDATE), session);

		this.db.beginTransaction();
		try {
			if (this.fcu == null) { // do we already have it?
				// Return null if this computer does not exist.
				this.fcu = this.db.find(new FriendComputerUsageFindBySourceIdAndTargetIdQuery(this.sourceComputerId,
						this.targetComputerId));
			}
			if (this.fcu == null) {
				return null; // FCU does not exist
			}

			if (this.archiveHoldExpireDate != null) {
				this.fcu.setArchiveHoldExpireDate(this.archiveHoldExpireDate);
			}

			this.fcu = this.db.update(new FriendComputerUsageUpdateQuery(this.fcu));

			this.db.commit();
			CpcHistoryLogger.info(session, "modified computer: {}", this.fcu);

		} catch (CommandException e) {
			this.db.rollback();
			throw e;
		} catch (Throwable e) {
			this.db.rollback();
			log.error("Unexpected: ", e);
		} finally {
			this.db.endTransaction();
		}

		return this.fcu;
	}
}