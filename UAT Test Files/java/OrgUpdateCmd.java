/*
 * <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.org;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import com.backup42.CpcConstants;
import com.backup42.common.command.ServiceCommand;
import com.backup42.history.CpcHistoryLogger;
import com.backup42.role.ProOnlineAdminRole;
import com.backup42.server.MasterServices;
import com.code42.backup.SecurityKeyType;
import com.code42.computer.ComputerSendServiceCommandByOrg;
import com.code42.config.OrgComputerConfigUpdateCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.db.NotFoundException;
import com.code42.core.hierarchy.IHierarchyService;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.code42.hibernate.aftertx.AfterTxRunnable;
import com.code42.hibernate.aftertx.IAfterTxRunnable.Priority;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.OrgUpdateCmd.Builder.OrgSettingsBuilder;
import com.code42.org.destination.OrgDestinationUpdateAvailableDestinationsCmd;
import com.code42.org.destination.OrgDestinationUpdateInheritanceCmd;
import com.code42.user.UserRoleDeleteCmd;
import com.code42.utils.SystemProperties;
import com.code42.utils.Weekday;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;
import com.code42.validation.rules.EmailRule;
import com.google.common.base.Splitter;
import com.google.inject.Inject;

/**
 * Update fields on an Org or BackupOrg.<br>
 * <br>
 * As of Bugzilla 1587 this command cannot change the parent org ID for an org. To do so you must use the
 * {@link OrgMoveCmd} command.
 */
public class OrgUpdateCmd extends DBCmd<Org> {

	public enum Error {
		SECURITY_KEY_DOWNGRADE, ORG_DUPLICATE, DUPLICATE_METHODS_OF_AUTHENTICATION
		// they can't ever downgrade their security key type for security reasons
	}

	private interface Property {

		String SAFTEY_CHECK_CONFIG_ONLY = "c42.safetyChecks.configOnly";
	}

	private static final Logger log = LoggerFactory.getLogger(OrgUpdateCmd.class);

	private final Builder data;

	@Inject
	private IHierarchyService hier;

	/**
	 * Use the Builder static inner class to construct one of these.
	 */
	private OrgUpdateCmd(Builder data) {
		this.data = data;
	}

	@Override
	public Org exec(CoreSession session) throws CommandException {
		BackupOrg org = null;
		this.db.beginTransaction();
		try {
			if (!this.data.allowUpdateCpOrg) {
				this.ensureNotCPOrg(this.data.orgId);
			}
			this.ensureNotHostedOrg(this.data.orgId, session);
			this.ensureMaster();

			if (this.data.orgId == CpcConstants.Orgs.ADMIN_ID && !this.data.allowUpdateAdminOrg) {
				this.ensureNotCPCentral();
			}

			// Authorize access to this operation on this org
			org = this.db.find(new OrgFindByIdQuery(this.data.orgId));
			if (org == null) {
				throw new NotFoundException("Unable to update org. Not found. " + this.data.orgId);
			}

			// Auth check after we know the org exists.
			this.runtime.run(new IsOrgManageableCmd(this.data.orgId, C42PermissionApp.Org.UPDATE_BASIC), session);
			if (!MasterServices.getInstance().isMasterOrg(org)) {
				try {
					// If the session is a system session, then override the master org restriction
					this.auth.isSystem(session);
				} catch (final UnauthorizedException ue) {
					// Not a system session, so fail
					throw new CommandException("Permission denied - only allowed to update master orgs");
				}
			}

			boolean canUpdateRestrictedFields = true;
			try {
				this.runtime.run(new IsOrgManageableCmd(this.data.orgId, C42PermissionApp.Org.UPDATE_RESTRICTED), session);
			} catch (UnauthorizedException e) {
				canUpdateRestrictedFields = false;
			}

			if ((!this.env.isBusinessCluster() && !this.env.isConsumerCluster()) && !(this.data.orgName instanceof None)
					&& !org.getOrgName().equals(this.data.orgName.get())
					&& this.runtime.run(new OrgFindAllByNameCmd(this.data.orgName.get()), session).size() > 0) {
				throw new CommandException("Duplicate Organization Name", Error.ORG_DUPLICATE);
			}

			if (org.isBusiness() && !org.getCustomConfig()) {
				// PRO business orgs *always* have a custom config because
				// we do not allow inheritance of default device configs from the PRO/SMB parent org
				this.data.customConfig = new Some<Boolean>(true);
			}

			// Special check. If saftety check is off then you can ONLY adjust config unless overridden
			boolean configOnly = false;
			if (this.env.isProtectedOrg(this.data.orgId) && !SystemProperties.isSafetyChecksEnabled()) {
				// by default only support config changes for protected orgs
				configOnly = SystemProperties.getOptionalBoolean(Property.SAFTEY_CHECK_CONFIG_ONLY, true);
				log.info("OrgUpdateCmd: Protected Org Enabled! configOnly=" + configOnly + ", orgId=" + this.data.orgId);
			}

			// Don't allow them to modify admin or consumer orgs. But they can change the default device config.
			boolean orgChanged = false;
			boolean customConfigChanged = false;
			// Can only update device defaults for org 1
			if (!(this.data.orgName instanceof None)) {
				org.setOrgName(this.data.orgName.get());
				orgChanged = true;
			}

			if (!(this.data.blocked instanceof None)) {
				org.setBlocked(this.data.blocked.get());
				orgChanged = true;
			}

			if (org != null) {
				if (this.data.customConfig instanceof Some<?>) {
					final boolean custom = this.data.customConfig.get();
					if (!custom && (this.data.orgId == CpcConstants.Orgs.ADMIN_ID || org.isConsumer() || org.isBusiness())) {
						throw new CommandException("Unable to turn off custom config for org=" + org);
					}
					if (org.getCustomConfig() != custom) {
						org.setCustomConfig(custom);
						customConfigChanged = true;
						orgChanged = true;
					}
				}
				if (!(this.data.configId instanceof None)) {
					org.setConfigId(this.data.configId.get());
					orgChanged = true;
				}

				boolean hasAllOrgUpdatePerm = this.auth.hasPermission(session, C42PermissionApp.AllOrg.UPDATE_BASIC);

				//
				// Discover the maxBytes and maxSeats that we are allowed to set
				//
				Long maxBytes = null;
				Integer maxSeats = null;
				if (!(this.data.maxSeats instanceof None) || !(this.data.maxBytes instanceof None)) {
					List<Integer> orgIds = this.hier.getAscendingOrgs(org.getOrgId());
					Map<Integer, OrgSso> orgSsoMap = this.run(new OrgSsoFindMultipleByOrgIdCmd(orgIds), this.auth
							.getAdminSession());
					for (Integer orgId : orgIds) { // traverse in order, bottom to top. break when max values are non-null.
						if (orgId == this.data.orgId) {
							continue;
						}
						if (maxBytes == null) {
							maxBytes = orgSsoMap.get(orgId).getMaxBytes();
						}
						if (maxSeats == null) {
							maxSeats = orgSsoMap.get(orgId).getMaxSeats();
						}
						if (maxSeats != null && maxBytes != null) {
							break; // quota is set
						}
					}
				}

				if (!(this.data.maxSeats instanceof None)) {
					// The goal here is to keep basic org admins from raising quotas above that set on their own org.
					// For this to work, a basic org admin is not allowed to change the value for their own org.
					// Admins with higher privileges can set their authorized org's values to any value.
					// It doesn't make sense to allow anybody to change a quota above the parent org's value
					if (hasAllOrgUpdatePerm || canUpdateRestrictedFields || session.getUser().getOrgId() != org.getOrgId()) {
						// Override change if larger than my inherited value
						Integer requestedMaxSeats = this.data.maxSeats.get();
						if (requestedMaxSeats != null && //
								maxSeats != null && //
								maxSeats > 0 && //
								requestedMaxSeats > 0 && //
								requestedMaxSeats > maxSeats) {
							// Override the change with my inherited value
							requestedMaxSeats = maxSeats;
						}
						org.setMaxSeats(requestedMaxSeats);
						orgChanged = true;
					}
				}

				if (!(this.data.maxBytes instanceof None)) {
					// The goal here is to keep basic org admins from raising quotas above that set on their own org.
					// For this to work, a basic org admin is not allowed to change the value for their own org.
					// Admins with higher privileges can set their authorized org's values to any value.
					// It doesn't make sense to allow anybody to change a quota above the parent org's value
					if (hasAllOrgUpdatePerm || canUpdateRestrictedFields || session.getUser().getOrgId() != org.getOrgId()) {
						Long requestedMaxBytes = this.data.maxBytes.get();
						if (requestedMaxBytes != null && //
								maxBytes != null && //
								maxBytes > 0 && //
								requestedMaxBytes > 0 && //
								requestedMaxBytes > maxBytes) {
							// Override the change with my inherited value
							requestedMaxBytes = maxBytes;
						}
						orgChanged = true;
						org.setMaxBytes(requestedMaxBytes);
					}
				}

				if (!(this.data.registrationKey instanceof None)) {
					org.setRegistrationKey(this.data.registrationKey.get());
					orgChanged = true;
				}
			}

			if (orgChanged && !configOnly) {
				// disallow updates to the system org, unless we've explicitly asked for that capability
				boolean canUpdate = true;
				if (SystemProperties.isSafetyChecksEnabled()) {
					// disallow updates to the consumer org
					canUpdate = this.data.orgId != CpcConstants.Orgs.ADMIN_ID || this.data.allowUpdateAdminOrg;
					canUpdate = canUpdate && (!org.isConsumer() || this.data.allowUpdateCpOrg);
				}
				if (canUpdate) {
					// user = this.runtime.run(new OrgValidate(user));
					org = (BackupOrg) this.db.update(new OrgUpdateQuery(org));
				} else {
					// You can change default device config but you can't change the admin org or the consumer org.
					if (this.data.deviceDefaults instanceof None && this.data.settingsBuilder == null) {
						throw new CommandException("Unable to modify system or CrashPlan org=" + org);
					}
				}
			}

			// Update device defaults if specified.
			if (customConfigChanged && !org.getCustomConfig()) {
				// Revert to parent config
				OrgComputerConfigUpdateCmd cmd = new OrgComputerConfigUpdateCmd.Builder(this.data.orgId).build();
				this.runtime.run(cmd, session);
			} else if (!(this.data.deviceDefaults instanceof None) && org.getCustomConfig()) {
				final boolean publish = this.data.publishDeviceDefaults.get();
				final boolean publishAll = this.data.publishToAll.get();
				final String xml = this.data.deviceDefaults.get();

				// WARNING, we are asking for an infinite loop here because
				// OrgComputerConfigUpdateCmd calls us just like we call it
				OrgComputerConfigUpdateCmd cmd = new OrgComputerConfigUpdateCmd.Builder(this.data.orgId, xml).publish(publish)
						.publishAll(publishAll).build();
				this.runtime.run(cmd, session);
			}

			if (!configOnly) {
				// Update destinations
				if (!(this.data.inheritDestinations instanceof None) && this.data.orgId != CpcConstants.Orgs.ADMIN_ID) {
					final Option<Boolean> confirmOption = this.data.inheritDestinationsDeleteConfirmed;
					boolean inheritDestinations = this.data.inheritDestinations.get();
					boolean confirmed = !(confirmOption instanceof None) && confirmOption.get();
					if (!inheritDestinations && org.getInheritDestinations()) {
						this.run(new OrgDestinationUpdateInheritanceCmd(this.data.orgId, false), session);
					} else if (inheritDestinations && confirmed && !org.getInheritDestinations()) {
						this.run(new OrgDestinationUpdateInheritanceCmd(this.data.orgId, true), session);
					}
					org.setInheritDestinations(inheritDestinations); // update settings uses this
				}

				// HACK ALERT: The publish update command has the CBO invalidate call and we want it to run before
				// updateSettings() sends out notifications to clients that they should reconnect. This will be fixed when
				// priority cache invalidation is implemented.
				this.db.afterTransaction(new OrgPublishUpdateCmd(org), session);

				// Settings
				if (this.data.settingsBuilder != null) {
					this.updateSettings(org, this.data.settingsBuilder, session, canUpdateRestrictedFields);
				}
			}

			this.db.commit();

		} catch (CommandException ce) {
			this.db.rollback();
			throw ce;
		} catch (Throwable t) {
			this.db.rollback();
			log.error("Unexpected:", t);
			throw new CommandException("Unexpected Exception: ", t);
		} finally {
			this.db.endTransaction();
		}

		// TODO: history log needs the fields that changed
		CpcHistoryLogger.info(session, "modified org: {}/{}", this.data.orgId, org.getOrgName());

		return org;
	}

	/** Helper for validation - raise a command exception if the option was set to null. */
	private static void raiseErrorIfNull(Option option, String errorMessage) throws CommandException {
		if (!(option instanceof None) && option.get() == null) {
			throw new CommandException(errorMessage);
		}
	}

	/** Helper for validation - raise a command exception if the option was set to less than 0. */
	private static void raiseErrorIfLessThan(Option option, long minVal, String errorMessage) throws CommandException {
		if (!(option instanceof None)) {
			Object opt = option.get();
			if (opt instanceof Long) {
				if ((Long) opt < minVal) {
					throw new CommandException(errorMessage);
				}
			} else if (opt instanceof Integer) {
				if ((Integer) opt < minVal) {
					throw new CommandException(errorMessage);
				}
			}
			// If it's not a long or an int, it's probably null. Ignore.
		}
	}

	/**
	 * Update fields in OrgSettings and OrgNotifySettings.
	 */
	private void updateSettings(BackupOrg org, final OrgSettingsBuilder settingsBuilder, final CoreSession session,
			boolean canUpdateRestrictedFields) throws CommandException {
		OrgSettingsInfoFindByOrgCmd.Builder orgSettingsInfoBuilder = new OrgSettingsInfoFindByOrgCmd.Builder();
		orgSettingsInfoBuilder.orgId(this.data.orgId);
		final OrgSettingsInfo osi = this.run(orgSettingsInfoBuilder.build(), session);
		OrgSettings orgSettings = osi.getOrgSettings();
		OrgNotifySettings orgNotifySettings = this.run(new OrgNotifySettingsFindByOrgCmd.Builder(this.data.orgId).build(),
				session);

		if (!(settingsBuilder.warnInDays instanceof None)) {
			orgNotifySettings.setBackupWarningDays(settingsBuilder.warnInDays.get());
			orgNotifySettings.setUseReportingDefaults(false);
		}

		if (!(settingsBuilder.alertInDays instanceof None)) {
			orgNotifySettings.setBackupCriticalDays(settingsBuilder.alertInDays.get());
			orgNotifySettings.setUseReportingDefaults(false);
		}

		if (!(settingsBuilder.recipients instanceof None)) {
			orgNotifySettings.setAdditionalReportEmailAddresses(settingsBuilder.recipients.get());
			orgNotifySettings.setUseReportingDefaults(false);
		}

		if (!(settingsBuilder.reportSchedule instanceof None)) {
			orgNotifySettings.setReportSchedule(settingsBuilder.reportSchedule.get());
			orgNotifySettings.setUseReportingDefaults(false);
		}

		if (!(settingsBuilder.sendReportsToOrgManagers instanceof None)) {
			orgNotifySettings.setSendReportsToOrgManagers(settingsBuilder.sendReportsToOrgManagers.get());
			orgNotifySettings.setUseReportingDefaults(false);
		}

		if (this.env.isBusinessCluster()) {
			orgNotifySettings.setUseReportingDefaults(false);
		} else {
			// XXX: this setting must come after all the other notify settings which set this value as a side-effect
			if (!(settingsBuilder.useServerDefaultsForNotify instanceof None)) {
				orgNotifySettings.setUseReportingDefaults(settingsBuilder.useServerDefaultsForNotify.get());
			}
		}

		// I can only change archive encryption key policy if parent org isn't locked.
		SecurityKeyType newType = osi.getSecurityKeyType();
		if (!osi.isSecurityKeyLockedByParent()) {
			if (!(settingsBuilder.securityKeyInherit instanceof None) && settingsBuilder.securityKeyInherit.get()) {
				if (!osi.isSecurityKeyInherited()) {
					// Make certain they aren't downgrading their security by inheriting from parent.
					final SecurityKeyType oldType = osi.getSecurityKeyType();
					newType = osi.getSecurityKeyTypeWrapper().getInheritedValue();
					if (newType.ordinal() >= oldType.ordinal()) {
						osi.setSecurityKeyInherited(true);
						log.info("AEK: Now inheriting archive encryption key. Changed orgId=" + this.data.orgId + " from "
								+ oldType + " to " + newType);
					} else {
						// Downgrade, this is not allowed
						throw new CommandException(Error.SECURITY_KEY_DOWNGRADE, "AEK: Unable to downgrade security for orgId="
								+ this.data.orgId + " from " + oldType.name() + " to " + newType.name());
					}
				} // They are already inheriting, do nothing.
			} else {
				// No longer inheriting.
				if (!(settingsBuilder.securityKeyInherit instanceof None) && !settingsBuilder.securityKeyInherit.get()) {
					if (osi.isSecurityKeyInherited()) {
						osi.setSecurityKeyInherited(false);
						log.info("AEK: No longer inheriting archive encryption key settings for orgId=" + this.data.orgId);
					}
				}
				if (!(settingsBuilder.securityKeyType instanceof None)) {
					// Verify they are upgrading, we don't support downgrading.
					final SecurityKeyType oldType = osi.getSecurityKeyType();
					newType = settingsBuilder.securityKeyType.get();
					if (newType.ordinal() > oldType.ordinal()) {
						osi.setSecurityKeyType(newType);
						log.info("AEK: Upgraded archive encryption key for orgId=" + this.data.orgId + " from " + oldType + " to "
								+ newType);
					} else if (newType.equals(oldType)) {
						// do nothing, they aren't upgrading the type.
					} else {
						throw new CommandException(Error.SECURITY_KEY_DOWNGRADE, "AEK: Unable to downgrade security for orgId="
								+ this.data.orgId + " from " + oldType.name() + " to " + newType.name());
					}
				}
				if (!(settingsBuilder.securityKeyLocked instanceof None)) {
					final boolean locked = settingsBuilder.securityKeyLocked.get();
					if (osi.isSecurityKeyLocked() != locked) {
						osi.setSecurityKeyLocked(locked);
						log.info("AEK: Archive encryption key is now " + (locked ? "locked" : "unlocked") + " for orgId="
								+ this.data.orgId);
					}
				}
			}
		}
		boolean keyLocked = osi.isSecurityKeyLocked();

		if (!(settingsBuilder.archiveHoldDays instanceof None)) {
			orgSettings.setArchiveHoldDays(settingsBuilder.archiveHoldDays.get());
		}

		if (!(settingsBuilder.defaultUserMaxBytes instanceof None)) {
			orgSettings.setDefaultUserMaxBytes(settingsBuilder.defaultUserMaxBytes.get());
		}

		if (!(settingsBuilder.defaultUserMobileQuota instanceof None)) {
			orgSettings.setDefaultUserMobileQuota(settingsBuilder.defaultUserMobileQuota.get());
		}

		boolean hasAllOrgUpdatePerm = this.auth.hasPermission(session, C42PermissionApp.AllOrg.UPDATE_BASIC);

		if (!(settingsBuilder.webRestoreUserLimit instanceof None)) {
			// The goal here is to keep basic org admins from raising web restore
			// limits above that set on their own org.
			// For this to work, a basic org admin is not allowed to change the value for their own org.
			// Admins with higher privileges can set their authorized org's values to any value.
			if (hasAllOrgUpdatePerm || canUpdateRestrictedFields) {
				// No value checking here
				orgSettings.setWebRestoreUserLimit(settingsBuilder.webRestoreUserLimit.get());
			} else if (org.getOrgId() != session.getUser().getOrgId()) {
				Integer requestedUserLimit = settingsBuilder.webRestoreUserLimit.get();
				Integer maxUserLimit = osi.getWebRestoreUserLimitWrapper().getInheritedValue();
				if (maxUserLimit != null && //
						requestedUserLimit != null && //
						maxUserLimit >= 0 && //
						requestedUserLimit >= 0 && //
						maxUserLimit < requestedUserLimit) {
					requestedUserLimit = maxUserLimit;
				}
				orgSettings.setWebRestoreUserLimit(requestedUserLimit);
			}
		}

		if (!(settingsBuilder.webRestoreAdminLimit instanceof None)) {
			// The goal here is to keep basic org admins from raising web restore
			// limits above that set on their own org.
			// For this to work, a basic org admin is not allowed to change the value for their own org.
			// Admins with higher privileges can set their authorized org's values to any value.
			if (hasAllOrgUpdatePerm || canUpdateRestrictedFields) {
				// No value checking here
				orgSettings.setWebRestoreAdminLimit(settingsBuilder.webRestoreAdminLimit.get());
			} else if (org.getOrgId() != session.getUser().getOrgId()) {
				Integer requestedAdminLimit = settingsBuilder.webRestoreAdminLimit.get();
				Integer maxAdminLimit = osi.getWebRestoreAdminLimitWrapper().getInheritedValue();
				if (maxAdminLimit != null && //
						requestedAdminLimit != null && //
						maxAdminLimit >= 0 && //
						requestedAdminLimit >= 0 && //
						maxAdminLimit < requestedAdminLimit) {
					requestedAdminLimit = maxAdminLimit;
				}
				orgSettings.setWebRestoreAdminLimit(requestedAdminLimit);
			}
		}

		if (!(settingsBuilder.usernameIsAnEmail instanceof None) && canUpdateRestrictedFields) {
			orgSettings.setUsernameIsAnEmail(settingsBuilder.usernameIsAnEmail.get());
		}

		if (!(settingsBuilder.autoOfferSelf instanceof None) && canUpdateRestrictedFields) {
			orgSettings.setAutoOfferSelf(settingsBuilder.autoOfferSelf.get());
		}

		if (!(settingsBuilder.allowLocalFolders instanceof None) && canUpdateRestrictedFields) {
			orgSettings.setAllowLocalFolders(settingsBuilder.allowLocalFolders.get());
		}

		if (!(settingsBuilder.defaultRoles instanceof None) && canUpdateRestrictedFields) {
			this.run(new OrgSettingsValidateCustomDefaultRolesCmd(this.data.orgId, settingsBuilder.defaultRoles.get()),
					session);
			orgSettings.setDefaultRoles(settingsBuilder.defaultRoles.get());
		}

		if (!(settingsBuilder.ldapServerIds instanceof None) && canUpdateRestrictedFields) {
			this.run(new OrgLdapServerUpdateCmd(this.data.orgId, settingsBuilder.ldapServerIds.get()), session);
		}

		if (!(settingsBuilder.radiusServerIds instanceof None) && canUpdateRestrictedFields) {
			this.run(new OrgRadiusServerUpdateCmd(this.data.orgId, settingsBuilder.radiusServerIds.get()), session);
		}

		if (!(settingsBuilder.ssoAuthIds instanceof None) && canUpdateRestrictedFields) {
			this.run(new OrgSsoAuthUpdateCmd(this.data.orgId, settingsBuilder.ssoAuthIds.get()), session);
		}

		this.db.update(new OrgSettingsUpdateQuery(orgSettings));
		this.db.update(new OrgNotifySettingsUpdateQuery(orgNotifySettings));

		// Remove admins
		if (!(settingsBuilder.removedAdmins instanceof None)) {
			final Integer[] admins = settingsBuilder.removedAdmins.get();
			for (Integer admin : admins) {
				final UserRoleDeleteCmd cmd = new UserRoleDeleteCmd(admin, ProOnlineAdminRole.ROLE_NAME);
				this.run(cmd, session);
			}
		}

		//
		// STOP BELOW THIS LINE FOR PROTECTED ORGS -- Below here, we can adjust destinations and other dangerous elements
		// that you are not allowed to modify for protected orgs
		//
		try {
			this.ensureNotProtectedOrgAllowAdmin(org.getOrgId());
		} catch (Throwable e) {
			log.warn("Aborting command - {} is a protected org", org.getOrgId());
			return;
		}

		// Update destinations
		boolean inherit = org.getInheritDestinations();
		// XXX: the CP console front-end currently /always/ passes along a list of destinations, which means that this code
		// will always run if inherit is false. This is why I've added the isAuthorized check - to prevent it from blowing
		// up when a non-privileged user hits it. Future refactoring should address this by making it discernable on the
		// back-end (starting with the REST layer) whether the list of destinations has changed.
		if (!inherit && this.auth.hasPermission(session, C42PermissionApp.AllOrg.ALL)) {
			if (!(settingsBuilder.destinationIds instanceof None) || !(settingsBuilder.destinationRemovalIds instanceof None)) {
				List<Integer> ensureList = Collections.EMPTY_LIST;
				List<Integer> removalsList = Collections.EMPTY_LIST;
				if (!(settingsBuilder.destinationIds instanceof None)) {
					ensureList = settingsBuilder.destinationIds.get();
				}
				if (!(settingsBuilder.destinationRemovalIds instanceof None)) {
					removalsList = settingsBuilder.destinationRemovalIds.get();
				}
				// The inherit parameter will always be false here
				this.run(new OrgDestinationUpdateAvailableDestinationsCmd(this.data.orgId, ensureList, removalsList, inherit),
						session);
			}
		}

		// Enforce locked key if it wasn't already locked/enforced.
		// Commented this out for now. We will bring it back eventually once we add the option for the user to indicate
		// that child orgs must inherit. We want locking and publish to work consistently. Config doesn't dictate
		// inheritance on lock so why should security key. We will make this an option at some point.
		// if (keyLocked && keyLocked != wasLocked) {
		// // Force my descendants to honor this setting
		// log.info("AEK: Dictate archive encryption key setting to all child orgs for orgId=" + this.data.orgId);
		// SettingsServices.getInstance().dictateSecurityKeyPolicyToDescendants(this.data.orgId);
		// reauthClients = true;
		// }

		// Publish the orgs security key setting to all users and computers within the org and all its child orgs.
		// But only if key is locked and wasn't locked and we are upgrading. You can't publish AccountPassword.
		final SecurityKeyType newTypeFinal = newType;
		final boolean keyLockedFinal = keyLocked;
		final Builder dataFinal = this.data;
		this.db.afterTransaction(new AfterTxRunnable(Priority.NORMAL) {

			public void run() {
				if (!newTypeFinal.equals(SecurityKeyType.AccountPassword)) {
					boolean publishKey = (!(settingsBuilder.securityKeyPublish instanceof None) && settingsBuilder.securityKeyPublish
							.get());
					if (publishKey || keyLockedFinal) {
						try {
							log.info("AEK: Publish archive encryption key setting to all computers of orgId=" + dataFinal.orgId);
							CoreBridge.run(new OrgSecurityKeyPublishCmd(dataFinal.orgId, false));
						} catch (CommandException e) {
							log.warn("Unable to publish archive encryption key setting to all computers of orgId=" + dataFinal.orgId,
									e);
						}
					}
				}

				// Additional check needed to support http://jira/browse/MAINT-12
				// We want to use the system to publish to green clients but we CANNOT issue this reauthorize.
				final boolean crashPlanOrg = OrgUpdateCmd.this.env.isCrashPlanOrg(dataFinal.orgId);
				if (!crashPlanOrg) {
					try {
						CoreBridge.run(new ComputerSendServiceCommandByOrg(dataFinal.orgId, true, ServiceCommand.REAUTHORIZE));
					} catch (CommandException e) {
						log.warn("Unable to send reauthorize to all computers of orgId=" + dataFinal.orgId, e);
					}
				}
			}
		});
	}

	/**
	 * Builds the input data and the OrgUpdate command. This takes the place of a big long constructor.
	 * 
	 * Note that within validate() below we're only doing null checks on builder methods which take a reference type and
	 * not those that take a value type (i.e. a primitive). Even though an integer will get autoboxed into an Integer
	 * within the Option type and that Integer could (in theory) be null there's no way for a null integer primitive to be
	 * passed into the actual method call... and as such there's no way the Integer in the corresponding Option type could
	 * be null in practice. Keep this in mind if you change the method signature on any of the builder methods below.
	 */
	public static class Builder {

		/* This val must always be present; it's the only way to get a builder */
		private int orgId = 0;
		private boolean allowUpdateAdminOrg = false;
		private boolean allowUpdateCpOrg = false;

		private OrgSettingsBuilder settingsBuilder = null; // Lazy

		private Option<String> orgName = None.getInstance();
		private Option<Integer> maxSeats = None.getInstance();
		private Option<Long> maxBytes = None.getInstance();
		private Option<Boolean> customConfig = None.getInstance();
		private Option<Boolean> blocked = None.getInstance();
		private Option<String> deviceDefaults = None.getInstance();
		private Option<Boolean> publishDeviceDefaults = None.getInstance();
		private Option<Boolean> publishToAll = None.getInstance();
		private Option<Boolean> inheritDestinations = None.getInstance();
		private Option<Boolean> inheritDestinationsDeleteConfirmed = None.getInstance();
		private Option<Long> configId = None.getInstance();
		private Option<String> registrationKey = None.getInstance();

		public Builder(int orgId) {
			this.orgId = orgId;
		}

		/**
		 * Mark that this builder can update the admin org (org 1). Danger!
		 * 
		 * TODO: This check needs to go away completely with permissions controlling this!
		 */
		public Builder allowUpdateAdminOrg() {
			this.allowUpdateAdminOrg = true;
			return this;
		}

		/**
		 * Mark that this builder can update the CrashPlan org on Central. Only to be used, for example, when lazily
		 * creating the config_id for the org (which is probably only an issue in dev mode where we don't want to put the
		 * entire default config into the dev SQL)
		 * 
		 * TODO: This check needs to go away completely with permissions controlling this!
		 */
		public Builder allowUpdateCpOrg() {
			this.allowUpdateCpOrg = true;
			return this;
		}

		public Builder orgName(String name) {
			this.orgName = new Some<String>(name);
			return this;
		}

		public Builder maxSeats(Integer maxSeats) {
			this.maxSeats = new Some<Integer>(maxSeats);
			return this;
		}

		public Builder maxBytes(Long maxBytes) {
			this.maxBytes = new Some<Long>(maxBytes);
			return this;
		}

		public Builder disableCustomConfig() {
			this.customConfig = new Some<Boolean>(false);
			return this;
		}

		public Builder allowCustomConfig() {
			this.customConfig = new Some<Boolean>(true);
			return this;
		}

		public Builder inheritDestinations(boolean inheritDestinations, boolean inheritDestinationsDeleteConfirmed) {
			if (!inheritDestinations) {
				this.inheritDestinations = new Some<Boolean>(false);
			} else if (inheritDestinations && inheritDestinationsDeleteConfirmed) {
				this.inheritDestinations = new Some<Boolean>(true);
				this.inheritDestinationsDeleteConfirmed = new Some<Boolean>(true);
			}
			return this;
		}

		public Builder blocked(boolean blocked) {
			this.blocked = new Some<Boolean>(blocked);
			return this;
		}

		public Builder deviceDefaults(String deviceDefaultsXml, boolean publishDeviceDefaults, boolean publishToAll) {
			this.customConfig = new Some<Boolean>(true);
			this.deviceDefaults = new Some<String>(deviceDefaultsXml);
			this.publishDeviceDefaults = new Some<Boolean>(publishDeviceDefaults);
			this.publishToAll = new Some<Boolean>(publishToAll);
			return this;
		}

		public Builder configId(Long configId) {
			this.configId = new Some<Long>(configId);
			return this;
		}

		public Builder registrationKey(String regKey) {
			this.registrationKey = new Some<String>(regKey);
			return this;
		}

		public OrgSettingsBuilder settingsBuilder() {
			if (this.settingsBuilder == null) {
				this.settingsBuilder = new OrgSettingsBuilder();
			}
			return this.settingsBuilder;
		}

		public void validate() throws CommandException {

			// Must be able to update org 1 so we can update the device defaults which is updated via this command.
			if (this.orgId < 1) {
				throw new CommandException("Org ID value must be 1 or greater");
			}

			// we do not allow null values for inheritable fields on the admin org
			if (this.orgId == CpcConstants.Orgs.ADMIN_ID) {
				raiseErrorIfNull(this.maxSeats, "Admin org cannot set null for max_seats");
				raiseErrorIfNull(this.maxBytes, "Admin org cannot set null for max_bytes");
			}

			// validate integers
			raiseErrorIfLessThan(this.maxSeats, CpcConstants.Orgs.UNLIMITED_SEATS, "maxSeats cannot be less than "
					+ CpcConstants.Orgs.UNLIMITED_SEATS);
			raiseErrorIfLessThan(this.maxBytes, CpcConstants.Orgs.UNLIMITED_BYTES, "maxBytes cannot be less than "
					+ CpcConstants.Orgs.UNLIMITED_BYTES);

			if (this.settingsBuilder != null) {
				this.settingsBuilder.validate(this.orgId);
			}

		}

		public OrgUpdateCmd build() throws CommandException {

			this.validate();
			return new OrgUpdateCmd(this);
		}

		public static class OrgSettingsBuilder {

			private Option<Integer> warnInDays = None.getInstance();
			private Option<Integer> alertInDays = None.getInstance();
			private Option<String> recipients = None.getInstance();
			private Option<EnumSet<Weekday>> reportSchedule = None.getInstance();
			private Option<Boolean> sendReportsToOrgManagers = None.getInstance(); // includes parent org managers
			private Option<Boolean> useServerDefaultsForNotify = None.getInstance();
			private Option<Integer[]> removedAdmins = None.getInstance();
			private Option<Integer> archiveHoldDays = None.getInstance();
			private Option<Long> defaultUserMaxBytes = None.getInstance();
			private Option<Integer> defaultUserMobileQuota = None.getInstance();
			private Option<Integer> webRestoreUserLimit = None.getInstance();
			private Option<Integer> webRestoreAdminLimit = None.getInstance();
			private Option<Boolean> usernameIsAnEmail = None.getInstance();
			private Option<List<Integer>> ldapServerIds = None.getInstance();
			private Option<List<Integer>> radiusServerIds = None.getInstance();
			private Option<List<Integer>> ssoAuthIds = None.getInstance();
			private Option<SecurityKeyType> securityKeyType = None.getInstance();
			private Option<Boolean> securityKeyLocked = None.getInstance();
			private Option<Boolean> securityKeyInherit = None.getInstance();
			private Option<Boolean> securityKeyPublish = None.getInstance();
			private Option<List<Integer>> destinationIds = None.getInstance();
			private Option<List<Integer>> destinationRemovalIds = None.getInstance();
			private Option<Boolean> autoOfferSelf = None.getInstance();
			private Option<Boolean> allowLocalFolders = None.getInstance();
			private Option<String> defaultRoles = None.getInstance();

			private OrgSettingsBuilder() {
				super();
			}

			public OrgSettingsBuilder warnInDays(Integer warnInDays) {
				this.warnInDays = new Some<Integer>(warnInDays);
				return this;
			}

			public OrgSettingsBuilder alertInDays(Integer alertInDays) {
				this.alertInDays = new Some<Integer>(alertInDays);
				return this;
			}

			public OrgSettingsBuilder recipients(String recipients) {
				recipients = recipients.replaceAll("\"", ""); // Groovy join adds the quotes; remove them
				this.recipients = new Some<String>(recipients);
				return this;
			}

			public OrgSettingsBuilder reportSchedule(String sReportSchedule) {
				EnumSet<Weekday> reportSchedule = Weekday.fromWeekString(sReportSchedule);
				this.reportSchedule = new Some<EnumSet<Weekday>>(reportSchedule);
				return this;
			}

			public OrgSettingsBuilder sendReportsToOrgManagers(Boolean sendReportsToOrgManagers) {
				this.sendReportsToOrgManagers = new Some<Boolean>(sendReportsToOrgManagers);
				return this;
			}

			public OrgSettingsBuilder useServerDefaultsForNotify(Boolean useServerDefaultsForNotify) {
				this.useServerDefaultsForNotify = new Some<Boolean>(useServerDefaultsForNotify);
				return this;
			}

			public OrgSettingsBuilder securityKeyType(String securityKeyType) {
				this.securityKeyType = new Some<SecurityKeyType>(SecurityKeyType.fromString(securityKeyType));
				return this;
			}

			public OrgSettingsBuilder securityKeyLocked(Boolean securityKeyLocked) {
				this.securityKeyLocked = new Some<Boolean>(securityKeyLocked);
				return this;
			}

			public OrgSettingsBuilder securityKeyPublish(Boolean securityKeyPublish) {
				this.securityKeyPublish = new Some<Boolean>(securityKeyPublish);
				return this;
			}

			public OrgSettingsBuilder securityKeyInherit(Boolean securityKeyInherit) {
				this.securityKeyInherit = new Some<Boolean>(securityKeyInherit);
				return this;
			}

			public OrgSettingsBuilder removedAdmins(Integer[] removedAdmins) {
				this.removedAdmins = new Some<Integer[]>(removedAdmins);
				return this;
			}

			public OrgSettingsBuilder archiveHoldDays(Integer archiveHoldDays) {
				this.archiveHoldDays = new Some<Integer>(archiveHoldDays);
				return this;
			}

			public OrgSettingsBuilder defaultUserMaxBytes(Long defaultUserMaxBytes) {
				this.defaultUserMaxBytes = new Some<Long>(defaultUserMaxBytes);
				return this;
			}

			public OrgSettingsBuilder defaultUserMobileQuota(Integer defaultUserMobileQuota) {
				this.defaultUserMobileQuota = new Some<Integer>(defaultUserMobileQuota);
				return this;
			}

			public OrgSettingsBuilder webRestoreUserLimit(Integer webRestoreUserLimit) {
				this.webRestoreUserLimit = new Some<Integer>(webRestoreUserLimit);
				return this;
			}

			public OrgSettingsBuilder webRestoreAdminLimit(Integer webRestoreAdminLimit) {
				this.webRestoreAdminLimit = new Some<Integer>(webRestoreAdminLimit);
				return this;
			}

			public OrgSettingsBuilder usernameIsAnEmail(Boolean usernameIsAnEmail) {
				this.usernameIsAnEmail = new Some<Boolean>(usernameIsAnEmail);
				return this;
			}

			public OrgSettingsBuilder ldapServerIds(List<Integer> ldapServerIds) {
				this.ldapServerIds = new Some<List<Integer>>(ldapServerIds);
				return this;
			}

			public OrgSettingsBuilder radiusServerIds(List<Integer> radiusServerIds) {
				this.radiusServerIds = new Some<List<Integer>>(radiusServerIds);
				return this;
			}

			public OrgSettingsBuilder ssoAuthIds(List<Integer> ssoAuthIds) {
				this.ssoAuthIds = new Some<List<Integer>>(ssoAuthIds);
				return this;
			}

			public OrgSettingsBuilder destinationIds(List<Integer> ids) {
				this.destinationIds = new Some<List<Integer>>(ids);
				return this;
			}

			public OrgSettingsBuilder destinationRemovalIds(List<Integer> ids) {
				this.destinationRemovalIds = new Some<List<Integer>>(ids);
				return this;
			}

			public OrgSettingsBuilder autoOfferSelf(Boolean autoOfferSelf) {
				this.autoOfferSelf = new Some<Boolean>(autoOfferSelf);
				return this;
			}

			public OrgSettingsBuilder allowLocalFolders(Boolean allowLocalFolders) {
				this.allowLocalFolders = new Some<Boolean>(allowLocalFolders);
				return this;
			}

			public OrgSettingsBuilder defaultRoles(String defaultRoles) {
				this.defaultRoles = new Some<String>(defaultRoles);
				return this;
			}

			private boolean optionListHasValue(Option<List<Integer>> option) {
				// Return true when a value greater than 0 is found.
				if (!(option instanceof None)) {
					List<Integer> ids = option.get();
					if (ids != null) {
						for (Integer id : ids) {
							if (id != null && id > 0) {
								return true;
							}
						}
					}
				}
				return false;
			}

			private void validate(int orgId) throws CommandException {
				// we do not allow null values for inheritable fields on the admin org
				if (orgId == CpcConstants.Orgs.ADMIN_ID) {
					raiseErrorIfNull(this.archiveHoldDays, "Admin org settings cannot set null for archive_hold_days");
					raiseErrorIfNull(this.defaultUserMaxBytes, "Admin org settings cannot set null for default_user_max_bytes");
					raiseErrorIfNull(this.defaultUserMobileQuota,
							"Admin org settings cannot set null for default_user_mobile_quota");
					raiseErrorIfNull(this.webRestoreUserLimit, "Admin org settings cannot set null for web_restore_user_limit");
					raiseErrorIfNull(this.webRestoreAdminLimit, "Admin org settings cannot set null for web_restore_admin_limit");
				}

				// validate email addresses
				if (!(this.recipients instanceof None) && this.recipients.get().trim().length() > 0) {
					for (String email : Splitter.on(',').split(this.recipients.get())) {
						if (EmailRule.isValidEmail(email) == false) {
							throw new CommandException("Invalid recipient email address: " + email);
						}
					}
				}

				boolean hasLdapServers = this.optionListHasValue(this.ldapServerIds);
				boolean hasRadiusServers = this.optionListHasValue(this.radiusServerIds);
				boolean hasSsoServers = this.optionListHasValue(this.ssoAuthIds);

				if ((hasLdapServers || hasRadiusServers) && hasSsoServers) {
					throw new CommandException(Error.DUPLICATE_METHODS_OF_AUTHENTICATION,
							"Cannot use SsoAuth with either LDAP or Radius.");
				}

				raiseErrorIfLessThan(this.warnInDays, 0, "warnInDays cannot be negative");
				raiseErrorIfLessThan(this.alertInDays, 0, "alertInDays cannot be negative");
				raiseErrorIfLessThan(this.archiveHoldDays, 0, "archiveHoldDays cannot be negative");
				raiseErrorIfLessThan(this.defaultUserMaxBytes, CpcConstants.Orgs.UNLIMITED_DEFAULT_USER_MAX_BYTES,
						"defaultUserMaxBytes cannot be less than " + CpcConstants.Orgs.UNLIMITED_DEFAULT_USER_MAX_BYTES);
				raiseErrorIfLessThan(this.defaultUserMobileQuota, CpcConstants.Orgs.UNLIMITED_DEFAULT_USER_MOBILE_QUOTA,
						"defaultUserMobileQuota cannot be less than " + CpcConstants.Orgs.UNLIMITED_DEFAULT_USER_MOBILE_QUOTA);
				raiseErrorIfLessThan(this.webRestoreUserLimit, CpcConstants.Orgs.UNLIMITED_WEB_RESTORE_LIMIT,
						"webRestoreUserLimit cannot be less than " + CpcConstants.Orgs.UNLIMITED_WEB_RESTORE_LIMIT);
				raiseErrorIfLessThan(this.webRestoreAdminLimit, CpcConstants.Orgs.UNLIMITED_WEB_RESTORE_LIMIT,
						"webRestoreAdminLimit cannot be less than " + CpcConstants.Orgs.UNLIMITED_WEB_RESTORE_LIMIT);
			}
		}
	}
}
