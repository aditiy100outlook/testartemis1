package com.code42.computer;

import com.code42.core.CommandException;
import com.code42.core.ICoreRuntime;
import com.code42.core.auth.IAuthorizationService;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.hierarchy.HierarchyNotFoundException;
import com.code42.core.hierarchy.IHierarchyService;
import com.code42.core.hierarchy.IHierarchyServiceNotify;
import com.code42.core.impl.DBCmd;
import com.code42.core.relation.IRelationServiceNotify;
import com.code42.hibernate.aftertx.IAfterTxRunnable;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.server.license.IUserLicenseService;
import com.code42.social.FriendComputerUsage;
import com.code42.utils.Triple;
import com.google.inject.Inject;

public class FriendComputerUsageChangePublishCmd extends DBCmd<Void> {

	@Inject
	IAuthorizationService auth;
	@Inject
	ICoreRuntime runtime;
	@Inject
	IUserLicenseService userLicenseService;
	@Inject
	IHierarchyService hierarchy;
	@Inject
	IHierarchyServiceNotify hierarchyServiceNotify;
	@Inject
	IRelationServiceNotify relationServiceNotify;

	private static final Logger log = LoggerFactory.getLogger(FriendComputerUsageChangePublishCmd.class);

	private final FriendComputerUsage fcu;
	private final Boolean create;

	public FriendComputerUsageChangePublishCmd(FriendComputerUsage fcu, Boolean create) {
		this.fcu = fcu;
		this.create = create;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		Integer userId = null;
		try {
			Triple<Integer, Integer, Long> userHierarchy = this.hierarchy
					.getHierarchyByGUID(this.fcu.getSourceComputerGuid());
			userId = userHierarchy.getTwo();
		} catch (HierarchyNotFoundException hnfe) {
			log.debug("Failed to find userId for source computer guid {}", this.fcu.getSourceComputerGuid());
		}

		Long fcuId = this.fcu.getFriendComputerUsageId();
		InvalidateGuidCacheCmd invalidateCmd = new InvalidateGuidCacheCmd(this.userLicenseService,
				this.hierarchyServiceNotify, this.relationServiceNotify, this.auth, this.runtime, fcuId, userId, this.create);

		this.db.afterTransaction(invalidateCmd);

		return null;
	}

	/**
	 * Invalidate or update any relevant caches concerned with computer guids.
	 * 
	 */
	private static class InvalidateGuidCacheCmd implements IAfterTxRunnable {

		private static final Logger log = LoggerFactory.getLogger(InvalidateGuidCacheCmd.class);

		private final ICoreRuntime runtime;
		private final IAuthorizationService auth;
		private final IUserLicenseService userLicenseService;
		private final IHierarchyServiceNotify hierarchyServiceNotify;
		private final IRelationServiceNotify relationServiceNotify;

		private final Long fcuId;
		private final Integer userId;
		private final boolean create;

		private InvalidateGuidCacheCmd(IUserLicenseService license, IHierarchyServiceNotify hierarchyNotify,
				IRelationServiceNotify relationNotify, IAuthorizationService auth, ICoreRuntime runtime, long fcuId,
				Integer userId, boolean create) {
			this.runtime = runtime;
			this.auth = auth;
			this.userLicenseService = license;
			this.hierarchyServiceNotify = hierarchyNotify;
			this.relationServiceNotify = relationNotify;

			this.fcuId = fcuId;
			this.userId = userId;
			this.create = create;
		}

		public Priority getPriority() {
			return Priority.HIGH;
		}

		public void run() {
			this.userLicenseService.invalidate();

			/*
			 * The FCU was not passed in from above because of database caching issues. If we saved the original FCU, when
			 * this command runs it would see the old cached fcu in the session with a null mount point id.
			 */
			FriendComputerUsage fcu = null;
			try {
				fcu = this.runtime.run(new FriendComputerUsageFindByIdCmd(this.fcuId), this.auth.getAdminSession());
			} catch (CommandException e) {
				log.debug("Failed to find new FCU, skipping update of CRS");
			}

			// The hierarchy service needs to be updated so the license service will see current counts
			// when it runs as part of the space stats aggregation
			if (fcu != null && fcu.getMountPointId() != null) {
				if (this.create) {
					this.relationServiceNotify.handleArchiveCreate(fcu.getSourceComputerGuid(), fcu.getMountPointId());
				} else {
					this.relationServiceNotify.handleArchiveDelete(fcu.getSourceComputerGuid(), fcu.getMountPointId());
				}
			}

			if (this.userId != null) {
				this.hierarchyServiceNotify.handleUserLicenseUpdate(this.userId);
			}
		}
	}
}
