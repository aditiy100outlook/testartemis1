package com.code42.org;

import java.util.List;

import com.backup42.history.CpcHistoryLogger;
import com.backup42.social.SocialComputerNetworkServices;
import com.code42.computer.Computer;
import com.code42.computer.ComputerFindByOrgCmd;
import com.code42.core.CommandException;
import com.code42.core.IEnvironment;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.user.UserUnblockCmd;
import com.code42.utils.LangUtils;
import com.google.inject.Inject;

/**
 * Unblocks an org and all its children.
 * 
 * A blocked org will not allow any of its users or computers to log in, new registrations will be rejected and all
 * currently logged in clients will be logged out. Backups will continue, however.
 */
public class OrgUnblockCmd extends DBCmd<OrgUnblockCmd.Result> {

	@Inject
	private IEnvironment environment;

	public enum Result {
		NOT_FOUND, NOT_BLOCKED, SUCCESS
	}

	public enum Error {
		PARENT_ORG_IS_BLOCKED
	}

	private static final Logger log = LoggerFactory.getLogger(UserUnblockCmd.class);

	private int orgId;
	private Org org;

	public OrgUnblockCmd(int userId) {
		this.orgId = userId;
	}

	public OrgUnblockCmd(Org org) {
		if (org == null) {
			throw new IllegalArgumentException("Invalid argument; org is null");
		}

		this.org = org;
		this.orgId = org.getOrgId();
	}

	@Override
	public Result exec(CoreSession session) throws CommandException {

		this.runtime.run(new IsOrgManageableCmd(this.orgId, C42PermissionApp.Org.UPDATE_BASIC), session);

		// Unable to unblock your own org.
		if (this.orgId == session.getUser().getOrgId()) {
			throw new UnauthorizedException("Unable to unblock org, can't unblock your own org.");
		}

		if (this.org == null) {
			this.org = this.db.find(new OrgFindByIdQuery(this.orgId));
		}

		// Validate
		{
			this.org = this.db.find(new OrgFindByIdQuery(this.orgId));
			if (this.org == null || this.org.getOrgId() == null) {
				return Result.NOT_FOUND;
			}

			if (!this.org.isBlocked()) {
				return Result.NOT_BLOCKED;
			}

			if (this.org.getParentOrgId() != null) {
				// Make sure the parent org is unblocked before unblocking the user.
				// CP-5804: Go to the db directly because the parent SSO is not yet updated when unblocking a tree of orgs.
				BackupOrg parentOrg = this.db.find(new OrgFindByIdQuery(this.org.getParentOrgId()));
				if (parentOrg.isBlocked()) {
					log.info("Attempted to unblock an org with a blocked parent org: {}", this.org);
					// The org needs to be unblocked first
					throw new CommandException(Error.PARENT_ORG_IS_BLOCKED,
							"Attempted to unblock an org with a blocked parent org");
				}
			}

			if (this.environment.isProtectedOrg(this.org.getOrgId())) {
				throw new UnsupportedOperationException("CANNOT UNBLOCK PROTECTED org in PRD! " + this.org);
			}
		}

		this.db.beginTransaction();
		try {

			this.unblockOrg(session);

			Result result = this.unblockChildren(session);
			if (result != Result.SUCCESS) {
				this.db.rollback();
				return result;
			}

			// This work occurs AFTER the transaction is completed
			this.updateOrgComputers(session);

			this.db.afterTransaction(new OrgPublishUpdateCmd((BackupOrg) this.org), session);

			this.db.commit();

			CpcHistoryLogger.info(session, "unblocked org: {}/{}", this.orgId, this.org.getOrgName());

		} catch (CommandException ce) {
			this.db.rollback();
			throw ce;
		} catch (Throwable t) {
			this.db.rollback();
			throw new CommandException("Unexpected exception while unblocking user", t);
		} finally {
			this.db.endTransaction();
		}

		return Result.SUCCESS;
	}

	private void unblockOrg(CoreSession session) throws CommandException {
		log.info("unblocking org: " + this.org);
		OrgUpdateCmd.Builder builder = new OrgUpdateCmd.Builder(this.orgId);
		builder.blocked(false);
		this.run(builder.build(), session);
		log.info("Org has been unblocked: " + this.org);
	}

	private Result unblockChildren(CoreSession session) throws CommandException {
		List<BackupOrg> children = this.org.getChildOrgs();
		if (LangUtils.hasElements(children)) {
			for (Org child : children) {
				Result result = this.run(new OrgUnblockCmd(child), session);
				if (result != Result.SUCCESS) {
					log.warn("Unexpected Error unblocking org's children; org={}, child={}", this.org, child);
					return result;
				}
			}
		}
		return Result.SUCCESS;
	}

	/**
	 * Reauthorize all the computers in the org to recognize the change
	 * 
	 * @param session
	 * @throws CommandException
	 */
	private void updateOrgComputers(CoreSession session) throws CommandException {
		List<Computer> computers = this.run(new ComputerFindByOrgCmd(this.orgId, true, false), session);
		for (final Computer computer : computers) {
			SocialComputerNetworkServices.getInstance().notifyComputerOfChange(computer.getGuid());
		}
	}
}
