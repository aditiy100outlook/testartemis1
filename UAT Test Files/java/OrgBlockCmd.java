package com.code42.org;

import java.util.List;

import com.backup42.history.CpcHistoryLogger;
import com.backup42.social.SocialComputerNetworkServices;
import com.code42.computer.Computer;
import com.code42.computer.ComputerFindByOrgCmd;
import com.code42.core.CommandException;
import com.code42.core.IEnvironment;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.utils.LangUtils;
import com.google.inject.Inject;

/**
 * Block an org and all its children.
 * 
 * A blocked org will not allow any of its users or computers to log in, new registrations will be rejected and all
 * currently logged in clients will be logged out. Backups will continue, however.
 */
public class OrgBlockCmd extends DBCmd<OrgBlockCmd.Result> {

	@Inject
	private IEnvironment environment;

	public enum Result {
		NOT_FOUND, ALREADY_BLOCKED, SUCCESS, OWN_NOT_ALLOWED
	}

	private static final Logger log = LoggerFactory.getLogger(OrgBlockCmd.class);

	private int orgId;
	private Org org;

	public OrgBlockCmd(int orgId) {
		this.orgId = orgId;
	}

	public OrgBlockCmd(Org org) {
		if (org == null) {
			throw new IllegalArgumentException("Invalid argument; org is null");
		}

		this.org = org;
		this.orgId = org.getOrgId();
	}

	@Override
	public Result exec(CoreSession session) throws CommandException {

		this.runtime.run(new IsOrgManageableCmd(this.orgId, C42PermissionApp.Org.UPDATE_BASIC), session);

		// Unable to block your own org.
		if (this.orgId == session.getUser().getOrgId()) {
			throw new CommandException("Unable to block org, can't block your own org.");
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

			if (this.org.isBlocked()) {
				return Result.ALREADY_BLOCKED;
			}

			if (this.environment.isProtectedOrg(this.org.getOrgId())) {
				throw new CommandException("CANNOT BLOCK PROTECTED org in PRD! " + this.org);
			}
		}

		this.db.beginTransaction();
		try {

			this.blockOrg(session);

			Result result = this.blockChildren(this.auth.getSystemSession());
			if (result != Result.SUCCESS) {
				return result;
			}

			this.updateOrgComputers(session);

			this.db.afterTransaction(new OrgPublishUpdateCmd((BackupOrg) this.org), session);

			this.db.commit();

			CpcHistoryLogger.info(session, "blocked org: {}/{}", this.orgId, this.org.getOrgName());

		} catch (CommandException ce) {
			this.db.rollback();
			throw ce;
		} catch (Throwable t) {
			this.db.rollback();
			throw new CommandException("Unexpected exception while blocking org", t);
		} finally {
			this.db.endTransaction();
		}

		return Result.SUCCESS;
	}

	private void blockOrg(CoreSession session) throws CommandException {
		log.info("blocking org: " + this.org);
		OrgUpdateCmd.Builder builder = new OrgUpdateCmd.Builder(this.orgId);
		builder.blocked(true);
		this.run(builder.build(), session);
		log.info("Org has been blocked: " + this.org);
	}

	private Result blockChildren(CoreSession session) throws CommandException {
		List<BackupOrg> children = this.org.getChildOrgs();
		if (LangUtils.hasElements(children)) {
			for (Org child : children) {
				Result result = this.run(new OrgBlockCmd(child), session);
				if (result != Result.SUCCESS) {
					log.warn("Unexpected Error blocking org's children; org={}, child={}", this.org, child);
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
