package com.code42.org;

import com.backup42.history.CpcHistoryLogger;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;

public class OrgActivateCmd extends DBCmd<OrgActivateCmd.Result> {

	public enum Result {
		NOT_FOUND, NOT_DEACTIVATED, PARENT_IS_DEACTIVATED, SUCCESS
	}

	private static final Logger log = LoggerFactory.getLogger(OrgActivateCmd.class);

	private int orgId;

	public OrgActivateCmd(int orgId) {
		this.orgId = orgId;
	}

	public int getOrgId() {
		return this.orgId;
	}

	@Override
	public Result exec(CoreSession session) throws CommandException {

		this.runtime.run(new IsOrgManageableCmd(this.orgId, C42PermissionApp.Org.UPDATE_BASIC), session);

		// Can't activate your own org.
		if (this.orgId == session.getUser().getOrgId()) {
			throw new UnauthorizedException("Unable to activate org, can't activate your own org.");
		}

		Org org = null;
		this.db.beginTransaction();
		try {

			if (this.orgId < 2) {
				throw new CommandException("Org ID value must be 2 or greater");
			}

			org = this.db.find(new OrgFindByIdQuery(this.orgId));
			if (org == null || org.getOrgId() == null) {
				return Result.NOT_FOUND;
			}

			if (org.isActive()) {
				// There is nothing to do
				return Result.NOT_DEACTIVATED;
			}

			// Make sure the parent is active before activating this one.
			if (org.getParentOrgId() != null) {
				OrgSso parent = this.run(new OrgSsoFindByOrgIdCmd(org.getParentOrgId()), session);
				if (!parent.isActive()) {
					log.info("Attempted to activate an org with a deactivated parent org.  Org attempted: " + org);
					// User needs to activate the parent first.
					return Result.PARENT_IS_DEACTIVATED;
				}
			}

			org.setActive(true);
			org = this.db.update(new OrgUpdateQuery(org));

			this.db.afterTransaction(new OrgPublishUpdateCmd((BackupOrg) org), session);

			this.db.commit();

			CpcHistoryLogger.info(session, "activated org: {}/{}", this.orgId, org.getOrgName());
		} catch (CommandException ce) {
			this.db.rollback();
			throw ce;
		} catch (Throwable t) {
			this.db.rollback();
			throw new CommandException("Unexpected exception while activating user", t);
		} finally {
			this.db.endTransaction();
		}

		return Result.SUCCESS;
	}
}
