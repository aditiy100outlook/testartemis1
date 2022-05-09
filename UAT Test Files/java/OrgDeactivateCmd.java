package com.code42.org;

import java.util.List;
import java.util.Set;

import com.backup42.CpcConstants;
import com.backup42.app.Backup42Formatter;
import com.backup42.history.CpcHistoryLogger;
import com.backup42.social.SocialComputerNetworkServices;
import com.code42.core.CommandException;
import com.code42.core.IEnvironment;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.hierarchy.HierarchyNotFoundException;
import com.code42.core.hierarchy.IHierarchyService;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.code42.exception.DebugRuntimeException;
import com.code42.hibernate.aftertx.AfterTxRunnable;
import com.code42.hibernate.aftertx.IAfterTxRunnable.Priority;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.server.sync.provider.ProviderSyncJobWorkerCmd;
import com.code42.user.User;
import com.code42.user.UserFindByOrgCmd;
import com.code42.utils.LangUtils;
import com.code42.utils.Stopwatch;
import com.code42.utils.Time;
import com.google.inject.Inject;

public class OrgDeactivateCmd extends DBCmd<OrgDeactivateCmd.Result> {

	private static final Logger log = LoggerFactory.getLogger(OrgDeactivateCmd.class.getName());

	@Inject
	private IEnvironment env;

	@Inject
	private IHierarchyService hierarchyService;

	public enum Result {
		NOT_FOUND, NOT_ACTIVE, SUCCESS
	}

	private int orgId;

	private Set<OrgEventCallback> orgEventCallbacks;

	@Inject
	public void setOrgEventCallbacks(Set<OrgEventCallback> orgEventCallbacks) {
		this.orgEventCallbacks = orgEventCallbacks;
	}

	public OrgDeactivateCmd(int orgId) {
		this.orgId = orgId;
	}

	public int getOrgId() {
		return this.orgId;
	}

	@Override
	public Result exec(CoreSession session) throws CommandException {

		this.runtime.run(new IsOrgManageableCmd(this.orgId, C42PermissionApp.Org.UPDATE_BASIC), session);

		// Can't deactivate your own org.
		if (this.orgId == session.getUser().getOrgId()) {
			throw new UnauthorizedException("Unable to deactivate org, can't deactivate your own org.");
		}

		Org org = null;
		this.db.beginTransaction();
		try {

			if (this.orgId < 2) {
				throw new CommandException("Org ID value must be 2 or greater");
			}

			if (this.orgId == CpcConstants.Orgs.ADMIN_ID) {
				throw new CommandException("No one is allowed to deactivate the admin org.");
			}

			org = this.db.find(new OrgFindByIdQuery(this.orgId));
			if (org == null || org.getOrgId() == null) {
				return Result.NOT_FOUND;
			}

			if (!org.isActive()) {
				return Result.NOT_ACTIVE;
			}

			/*
			 * deactivateOrg() method takes care of all save operations... eventually we'll need to migrate this stuff to the
			 * new architecture.
			 */
			this.deactivateOrg(org);

			for (OrgEventCallback callback : this.orgEventCallbacks) {
				callback.orgDeactivate(org, session);
			}

			this.db.commit();

			CpcHistoryLogger.info(session, "deactivated org: {}/{}", this.orgId, org.getOrgName());

			this.db.afterTransaction(new OrgPublishUpdateCmd((BackupOrg) org), session);

			/*
			 * After a successfully deactivating the orgs (i.e. if we're still here and haven't thrown an exception yet) we're
			 * able to invalidate the relevant cache entries. Since we also deactivate the descendant orgs, we need to
			 * invalidate their space storage objects as well.
			 */
			Set<Integer> suborgs = this.hierarchyService.getAllOrgs(org.getOrgId());
			for (Integer orgId : suborgs) {
				BackupOrg ancestor = this.db.find(new OrgFindByIdQuery(orgId));
				this.db.afterTransaction(new OrgPublishUpdateCmd(ancestor), session);
			}

			if (org instanceof HostedParentOrg) {
				// If this fails, the SyncJob will try again later.
				final HostedParentOrg hpo = (HostedParentOrg) org;
				this.db.afterTransaction(new AfterTxRunnable(Priority.NORMAL) {

					public void run() {
						try {
							OrgDeactivateCmd.this.runtime.run(new ProviderSyncJobWorkerCmd(hpo), OrgDeactivateCmd.this.auth
									.getSystemSession());
						} catch (Exception e) {
							log.warn("Initial sync attempt failed. The scheduled sync job will retry as needed", e);
						}
					}
				});
			}

		} catch (CommandException ce) {
			this.db.rollback();
			throw ce;
		} catch (Throwable t) {
			this.db.rollback();
			throw new CommandException("Unexpected exception while deactivating org", t);
		} finally {
			this.db.endTransaction();
		}

		return Result.SUCCESS;
	}

	/**
	 * Deactivates the given Org and all its descendants.
	 * 
	 * @param org
	 */
	private Org deactivateOrg(Org org) throws HierarchyNotFoundException, CommandException {
		if (!org.isActive()) {
			return org;
		}
		Set<Integer> orgIds = this.hierarchyService.getAllOrgs(org.getOrgId());
		this.deactivateOrgs(orgIds);
		return this.runtime.run(new OrgFindByIdCmd(org.getOrgId()), this.auth.getSystemSession());
	}

	/**
	 * Inactivates the given orgs as well as all their users and computers and friend relationships. The option for
	 * multiple orgs is used when you want to inactivate an org and all it's child orgs.
	 * 
	 * @param orgIds
	 */
	private void deactivateOrgs(Set<Integer> orgIds) {
		log.info("Deactivating orgs " + LangUtils.toString(orgIds));
		Stopwatch sw = new Stopwatch();
		for (Integer orgId : orgIds) {
			try {
				// Org org = CoreBridge.run(new OrgFindByIdCmd(orgId));
				Org org = this.runtime.run(new OrgFindByIdCmd(orgId), this.auth.getSystemSession());
				if (org == null) {
					continue;
				}

				// if protected org, then reject, this is an invalid operation
				// if (CoreBridge.getEnvironment().isProtectedOrg(org.getOrgId())) {
				if (this.env.isProtectedOrg(org.getOrgId())) {
					throw new UnsupportedOperationException("CANNOT deactivate PROTECTED org in PRD! " + org);
				}

				log.info("Deactivating org: " + org);

				List<User> users = CoreBridge.runNoException(new UserFindByOrgCmd(orgId, true));

				for (User u : users) {
					SocialComputerNetworkServices.getInstance().deactivateUser(u);
				}
				org.setActive(false);
				if (!org.getOrgName().contains("deactivated")) {
					String newOrgName = org.getOrgName() + " deactivated "
							+ Backup42Formatter.getDateString(Time.getNow(), Time.FORMAT_ISO_8601_DATETIME);
					org.setOrgName(newOrgName);
				}
				this.db.update(new OrgUpdateQuery(org));
				log.info("Org and any active users and their computers have been deactivated: " + org);
			} catch (Exception e) {
				throw new DebugRuntimeException("An error occurred deactivating this orgId: " + orgId
						+ ". If the user tries again, the process will pick up where it left off.", e);
			}
		}
		log.info("Deactivating orgs " + LangUtils.toString(orgIds) + " took " + sw.toString());
	}
}
