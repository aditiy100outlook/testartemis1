package com.code42.computer;

import com.backup42.history.CpcHistoryLogger;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.social.FriendComputerUsage;

/**
 * Swap one destination for another.
 * 
 * @author <a href="mailto:brian@code42.com">Brian Bispala </a>
 */
public class FriendComputerUsageSwapDoneCmd extends DBCmd<Void> {

	private static final Logger log = Logger.getLogger(FriendComputerUsageSwapDoneCmd.class.getName());

	private final long sourceGuid;
	private final long targetGuid;
	private final long previousTargetGuid;

	public FriendComputerUsageSwapDoneCmd(long sourceGuid, long targetGuid, long previousTargetGuid) {
		super();
		this.sourceGuid = sourceGuid;
		this.targetGuid = targetGuid;
		this.previousTargetGuid = previousTargetGuid;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		final ComputerSso sso = this.run(new ComputerSsoFindByGuidCmd(this.sourceGuid), session);
		if (sso == null) {
			return null;
		}
		this.run(new IsComputerManageableCmd(sso.getComputerId(), C42PermissionApp.Computer.UPDATE), session);

		this.db.beginTransaction();
		try {
			// Return null if this computer does not exist.
			FriendComputerUsage fcu = this.db.find(new FriendComputerUsageFindBySourceGuidAndTargetGuidQuery(this.sourceGuid,
					this.targetGuid));
			if (fcu == null) {
				return null; // FCU does not exist
			}

			if (fcu.getPreviousTargetComputerGuid() == null) {
				return null; // nothing to do
			} else if (fcu.getPreviousTargetComputerGuid().longValue() != this.previousTargetGuid) {
				throw new CommandException("Invalid FCU! previousTargetGuid={}, fcu={}", this.previousTargetGuid, fcu);
			}

			// clear the previous
			fcu.setPreviousTargetComputerGuid(null);

			this.db.update(new FriendComputerUsageUpdateQuery(fcu));

			this.db.commit();

			CpcHistoryLogger.info(session, "Destination swap DONE: sourceGuid={}, targetGuid={}, previousTargetGuid={}",
					this.sourceGuid, this.targetGuid, this.previousTargetGuid);

		} catch (CommandException e) {
			this.db.rollback();
			throw e;
		} catch (Throwable e) {
			this.db.rollback();
			log.error("Unexpected: ", e);
		} finally {
			this.db.endTransaction();
		}
		return null;
	}

}
