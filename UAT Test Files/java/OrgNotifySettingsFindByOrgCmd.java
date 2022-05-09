package com.code42.org;

import org.hibernate.Query;
import org.hibernate.Session;

import com.backup42.CpcConstants;
import com.code42.core.CommandException;
import com.code42.core.ICmd;
import com.code42.core.annotation.CoreNamedQuery;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.IAuthorizationService;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.hierarchy.HierarchyNotFoundException;
import com.code42.core.hierarchy.IHierarchyService;
import com.code42.core.impl.AbstractCmd;
import com.code42.core.impl.DBCmd;
import com.code42.exception.DebugRuntimeException;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.tree.AscendingTreeVisitor;
import com.google.common.base.Function;
import com.google.inject.Inject;

public interface OrgNotifySettingsFindByOrgCmd extends ICmd<OrgNotifySettings> {

	public static class Builder {

		private final int orgId;
		private boolean findInheritedSettings;

		public Builder(final int orgId) {
			this.orgId = orgId;
		}

		/**
		 * If this method is called on the builder, the built command will find the inherited OrgNotifySettings for the
		 * given orgId. By default -- if this method is not invoked prior to build() -- the command will return the
		 * OrgNotifySettings for the organization, regardless of inheritance.
		 */
		public Builder findInheritedSettings() {
			this.findInheritedSettings = true;
			return this;
		}

		public OrgNotifySettingsFindByOrgCmd build() {
			if (this.findInheritedSettings) {
				return new InheritedOrgNotifySettingsFindForOrgCmd(this.orgId);
			} else {
				return new ReferencedOrgNotifySettingsFindByOrgCmd(this.orgId);
			}
		}
	}

	/**
	 * From the organization identified by the given orgId, walk up the organization hierarchy until reaching an
	 * organization that does NOT inherit settings from its parent and return settings for that organization. If all
	 * organizations in the tree inherit, then return the default settings.
	 */
	static class InheritedOrgNotifySettingsFindForOrgCmd extends AbstractCmd<OrgNotifySettings> implements
			OrgNotifySettingsFindByOrgCmd {

		private static Logger log = LoggerFactory.getLogger(InheritedOrgNotifySettingsFindForOrgCmd.class.getName());

		@Inject
		private IHierarchyService hier;

		@Inject
		private IAuthorizationService auth;

		private final int orgId;

		private InheritedOrgNotifySettingsFindForOrgCmd(int orgId) {
			this.orgId = orgId;
		}

		@Override
		public OrgNotifySettings exec(final CoreSession session) throws CommandException {
			// A function that can be used in the hierarchy visitor to simplify settings retrieval for each org
			final Function<Integer, OrgNotifySettings> getSettingsFunction = new Function<Integer, OrgNotifySettings>() {

				public OrgNotifySettings apply(final Integer orgId) {
					try {
						final OrgNotifySettingsFindByOrgCmd cmd = new OrgNotifySettingsFindByOrgCmd.Builder(orgId).build();
						final CoreSession adminSession = InheritedOrgNotifySettingsFindForOrgCmd.this.auth.getAdminSession();

						return InheritedOrgNotifySettingsFindForOrgCmd.this.runtime.run(cmd, adminSession);
					} catch (final CommandException ce) {
						throw new DebugRuntimeException("Unable to retrieve OrgNotifySettings", ce, orgId);
					}
				}
			};

			final OrgNotifySettings defaultSettings = getSettingsFunction.apply(CpcConstants.Orgs.ADMIN_ID);

			final SettingsTreeVisitor visitor = new SettingsTreeVisitor(defaultSettings, getSettingsFunction);

			try {
				this.hier.visitAscending(this.orgId, visitor);
				return visitor.getSettings();
			} catch (final DebugRuntimeException dre) {
				log.error("Unable to retrieve OrgNotifySettings for orgId={}", dre.getObjects(), dre);
				return null;
			} catch (final HierarchyNotFoundException hnfe) {
				log.error("Unable to find a hierarchy for orgId={}", this.orgId);

				return null;
			}
		}

		private static final class SettingsTreeVisitor implements AscendingTreeVisitor<Object> {

			private boolean stillVisiting;
			private OrgNotifySettings settings;
			private final Function<Integer, OrgNotifySettings> getSettingsFunction;

			public SettingsTreeVisitor(final OrgNotifySettings defaultSettings,
					final Function<Integer, OrgNotifySettings> getSettingsFunction) {
				this.stillVisiting = true;
				this.settings = defaultSettings;
				this.getSettingsFunction = getSettingsFunction;
			}

			public void visit(int id, Object data) {
				if (this.stillVisiting) {
					final OrgNotifySettings currentSettings = this.getSettingsFunction.apply(id);
					if (!currentSettings.isUseReportingDefaults()) {
						this.stillVisiting = false;
						this.settings = currentSettings;
					}
				}
			}

			public OrgNotifySettings getSettings() {
				return this.settings;
			}

		}
	}

	/**
	 * Creates a new OrgNotifySettings if one doesn't exist yet.
	 */
	static class ReferencedOrgNotifySettingsFindByOrgCmd extends DBCmd<OrgNotifySettings> implements
			OrgNotifySettingsFindByOrgCmd {

		private final int orgId;

		private ReferencedOrgNotifySettingsFindByOrgCmd(int orgId) {
			this.orgId = orgId;
		}

		@Override
		public OrgNotifySettings exec(CoreSession session) throws CommandException {

			this.runtime.run(new IsOrgManageableCmd(this.orgId, C42PermissionApp.Org.READ), session);

			OrgNotifySettings ons = this.db.find(new OrgNotifySettingsFindByOrgIdQuery(this.orgId));
			if (ons == null) {
				// This is here only for legacy purposes; these SHOULD be created with the org, but
				// this was not always the case. If we ever run a "conversion" job to create all
				// these rows, this logic could be replaced by an exception for "not found".
				ons = this.runtime.run(new OrgNotifySettingsCreateCmd(this.orgId), session);
			}

			return ons;
		}

		@CoreNamedQuery(name = "findNotifySettingsByOrgId", query = "select n from OrgNotifySettings as n where n.orgId = :orgId")
		private static class OrgNotifySettingsFindByOrgIdQuery extends FindQuery<OrgNotifySettings> {

			private final Integer orgId;

			public OrgNotifySettingsFindByOrgIdQuery(Integer orgId) {
				this.orgId = orgId;
			}

			@Override
			public OrgNotifySettings query(Session session) throws DBServiceException {
				Query query = this.getNamedQuery(session);
				query.setInteger("orgId", this.orgId);

				return (OrgNotifySettings) query.uniqueResult();
			}
		}
	}

}
