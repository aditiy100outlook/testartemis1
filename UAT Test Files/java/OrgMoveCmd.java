package com.code42.org;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.hierarchy.IHierarchyService;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.google.inject.Inject;

/**
 * Command to move an org to another location in the org hierarchy.
 * 
 * @author bmcguire
 */
public class OrgMoveCmd extends DBCmd<OrgMoveCmd.Result> {

	/* ================= Dependencies ================= */
	private IHierarchyService hier;

	@Inject
	public void setHierarchy(IHierarchyService hier) {
		this.hier = hier;
	}

	public enum Result {
		SUCCESS, NONEXISTENT_PARENT_ORG, NONEXISTENT_ORG, SAME_PARENT_ORG, THROWABLE, BLOCKED
	}

	private static final Logger log = LoggerFactory.getLogger(OrgMoveCmd.class);

	private int orgId;
	private Integer targetParentOrgId; // this can be null, which implies adding an org to the top level

	public OrgMoveCmd(int orgId, Integer targetParentOrgId) {
		this.orgId = orgId;
		this.targetParentOrgId = targetParentOrgId;
	}

	public int getOrgId() {
		return this.orgId;
	}

	public int getTargetParentOrgId() {
		return this.targetParentOrgId;
	}

	@Override
	public Result exec(CoreSession session) throws CommandException {

		this.ensureNotCPOrg(this.orgId);

		Org org = null;
		Integer oldParentOrgId = null;

		this.db.beginTransaction();
		try {

			/* Some basic up front checking; org IDs must be > 1 */
			/*
			 * TODO: Should these throw exceptions? The method signature allows us to use CommandExceptions here but for most
			 * cases we want to return a specific type in the Result enum to allow the REST/translation layer to return
			 * something intelligent for various types of failures. A better approach might be to add specific members of
			 * Result to handle the following two cases and then throw exceptions only in the case of an authorization failure
			 * or something really unexpected.
			 */
			if (this.orgId < 1) {
				throw new CommandException("Org move failed: Org ID values must be 1 or greater");
			}

			if (this.targetParentOrgId != null && this.targetParentOrgId < 2) {
				throw new CommandException("Org move failed: Parent Org ID value must be 2 or greater");
			}

			// Authorize access to this operation on this org
			this.runtime.run(new IsOrgManageableCmd(this.orgId, C42PermissionApp.Org.UPDATE_BASIC), session);
			org = this.db.find(new OrgFindByIdQuery(this.orgId));
			if (org == null) {
				log.info("Org move failed: Target org does not exist: " + this.orgId);
				return Result.NONEXISTENT_ORG;
			}

			oldParentOrgId = org.getParentOrgId();

			// If the org's parent is already the target parent there is no point in continuing
			if (oldParentOrgId != null && oldParentOrgId.equals(this.targetParentOrgId)) {
				return Result.SAME_PARENT_ORG;
			}

			if (this.targetParentOrgId == null) {
				// They're making a new root org; check authorization to do so
				this.auth.isAuthorized(session, C42PermissionApp.AllOrg.UPDATE_BASIC);

			} else {

				OrgSso sso = this.runtime.run(new OrgSsoFindByOrgIdCmd(this.targetParentOrgId), session);

				// Make sure the parentOrg exists and can be given a new sub-org by this user.
				this.runtime.run(new IsOrgManageableCmd(this.targetParentOrgId, C42PermissionApp.Org.UPDATE_BASIC), session);
				if (sso == null) {
					log.info("Org move failed: Parent org ID not found: " + this.targetParentOrgId);
					return Result.NONEXISTENT_PARENT_ORG;
				}

				// Test for a cycle
				if (this.targetParentOrgId != null && this.hier.getAscendingOrgs(this.targetParentOrgId).contains(this.orgId)) {
					throw new CommandException(
							"Org move failed: Target parent org is a descendant of the org being moved. Moving would create a cycle. targetParentOrgId="
									+ this.targetParentOrgId + ",  orgId=" + this.orgId);
				}

				// Check for target org status
				BackupOrg parentOrg = this.db.find(new OrgFindByIdQuery(this.targetParentOrgId));
				if (parentOrg.isSlave() || parentOrg.isHostedParent()) {
					log.info("Org move failed: Invalid Parent state; org cannot be moved to parent: {}", parentOrg);
					return Result.BLOCKED;
				} else if (parentOrg.isBlocked()) {
					log.info("Org move failed: Invalid Parent state; org cannot be moved to parent: {}", parentOrg);
					return Result.BLOCKED;
				}
			}

			org.setParentOrgId(this.targetParentOrgId);

			org = this.db.update(new OrgUpdateQuery(org));

			this.db.afterTransaction(new OrgPublishMoveCmd((BackupOrg) org, oldParentOrgId), session);

			this.db.commit();
		} catch (CommandException e) {
			throw e;
		} catch (Throwable e) {
			log.error("Unexpected: ", e);
			return Result.THROWABLE;
		} finally {
			this.db.endTransaction();
		}

		log.info(session + " moved org: " + this.orgId + " to be child of parent org "
				+ (this.targetParentOrgId == null ? "top" : this.targetParentOrgId));

		return Result.SUCCESS;
	}
}
