package com.code42.custom;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.hibernate.Session;

import com.backup42.CpcConstants;
import com.code42.core.CommandException;
import com.code42.core.IEnvironment;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.businessobjects.BusinessObjectsException;
import com.code42.core.businessobjects.IBusinessObjectsService;
import com.code42.core.businessobjects.IBusinessObjectsVisitor;
import com.code42.core.businessobjects.impl.BaseBusinessObjectsVisitor;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;
import com.code42.custom.CustomUsage1ReportCmd.CustomUsage1;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.OrgSso;
import com.code42.sql.SQLQuery;
import com.code42.sql.SQLUtils;
import com.code42.utils.option.Option;
import com.google.inject.Inject;
import com.google.inject.internal.Lists;
import com.google.inject.internal.Maps;

/**
 * Custom org usage report written for a big communications provider.
 */
public class CustomUsage1ReportCmd extends DBCmd<List<CustomUsage1>> {

	private static final Logger log = LoggerFactory.getLogger(CustomUsage1ReportCmd.class);

	@Inject
	private IBusinessObjectsService busObjs;

	@Inject
	private IEnvironment env;

	// private Integer topOrgId;
	//
	// public CustomUsage1ReportCmd(Integer topOrgId) {
	// this.topOrgId = topOrgId;
	// }

	@Override
	public List<CustomUsage1> exec(CoreSession session) throws CommandException {
		if (this.env.isCpCentral()) {
			// This probably should not be run on the CrashPlan Central database
			// Although it should run just fine
			return null;
		}

		Map<Integer, Date> orgCreation = this.db.find(new OrgCreationDateQuery());
		Map<Integer, Date> orgLastActivity = this.db.find(new OrgLastActivityQuery());
		Map<Integer, Long> orgArchiveBytes = this.db.find(new OrgArchiveBytesRollupQuery());

		long defaultMaxBytes = -1;
		try {
			OrgSso adminOrg = this.busObjs.getOrg(CpcConstants.Orgs.ADMIN_ID);
			defaultMaxBytes = adminOrg.getMaxBytes();
		} catch (BusinessObjectsException e) {
			log.error("Unable to get admin org from IBusinessObjectService", e);
		}

		List<CustomUsage1> usages = Lists.newArrayList();
		IBusinessObjectsVisitor visitor = new CustomUsage1ReportVisitor(usages, orgCreation, orgArchiveBytes,
				orgLastActivity, defaultMaxBytes);

		// Populates the usages list
		this.busObjs.visitOrgs(visitor);

		log.debug("Returning {} custom usage report objects.", usages.size());
		return usages;
	}

	public static class OrgCreationDateQuery extends FindQuery<Map<Integer, Date>> {

		private static final String SQL = "-- Find the creation date for all orgs \n"
				+ "SELECT o.org_id, o.creation_date \n" //
				+ "FROM t_org AS o                  \n";

		@Override
		public Map<Integer, Date> query(Session session) throws DBServiceException {

			SQLQuery query = new SQLQuery(session, SQL);
			List<Object[]> list = query.list();

			Map<Integer, Date> orgCreated = Maps.newHashMap();
			for (Object[] row : list) {
				Integer orgId = SQLUtils.getInteger(row[0]);
				Date created = SQLUtils.getDate(row[1]);
				orgCreated.put(orgId, created);
			}

			return orgCreated;
		}

	}

	public static class OrgLastActivityQuery extends FindQuery<Map<Integer, Date>> {

		// This SQL will include cold storage archives
		private static final String SQL = "-- Find the last activity for a bunch of orgs \n"
				+ "SELECT u.org_id, MAX(fcu.last_activity) \n" //
				+ "FROM t_friend_computer_usage AS fcu     \n" //
				+ "INNER JOIN t_computer AS c ON (c.computer_id = fcu.source_computer_id) \n" //
				+ "INNER JOIN t_user AS u ON (u.user_id = c.user_id) \n" //
				+ "WHERE u.org_id > 1  \n" //
				+ "GROUP BY u.org_id   \n";

		@Override
		public Map<Integer, Date> query(Session session) throws DBServiceException {

			SQLQuery query = new SQLQuery(session, SQL);
			List<Object[]> list = query.list();

			Map<Integer, Date> orgLastActivity = Maps.newHashMap();
			for (Object[] row : list) {
				Integer orgId = SQLUtils.getInteger(row[0]);
				Date lastActivity = SQLUtils.getDate(row[1]);
				orgLastActivity.put(orgId, lastActivity);
			}

			return orgLastActivity;
		}

	}

	public static class OrgArchiveBytesRollupQuery extends FindQuery<Map<Integer, Long>> {

		private static String SQL = "" //
				+ "SELECT asr.org_id, asr.archive_bytes \n" //
				+ "FROM t_archive_summary_rollup AS asr \n" //
				+ "WHERE asr.org_id IS NOT NULL         \n" //
				+ "AND asr.user_id IS NULL              \n";

		public OrgArchiveBytesRollupQuery() {
			super();
		}

		@Override
		public Map<Integer, Long> query(Session session) throws DBServiceException {

			SQLQuery query = new SQLQuery(session, SQL);
			List<Object[]> list = query.list();

			Map<Integer, Long> orgArchiveBytes = Maps.newHashMap();
			for (Object[] row : list) {
				Integer orgId = SQLUtils.getInteger(row[0]);
				Long archiveBytes = SQLUtils.getLong(row[1]);
				orgArchiveBytes.put(orgId, archiveBytes);
			}

			return orgArchiveBytes;
		}
	}

	public static class CustomUsage1ReportVisitor extends BaseBusinessObjectsVisitor {

		private static final Logger log = LoggerFactory.getLogger(CustomUsage1ReportCmd.CustomUsage1ReportVisitor.class);

		private Map<Integer, Date> orgCreation;
		private Map<Integer, Date> orgLastActivity;
		private Map<Integer, Long> orgBytes;
		private long defaultMaxBytes = -1;

		private List<CustomUsage1> usages;
		private Map<Integer, CustomUsage1> usageById = Maps.newHashMap();

		public CustomUsage1ReportVisitor(List<CustomUsage1> usages, Map<Integer, Date> orgCreation,
				Map<Integer, Long> orgBytes, Map<Integer, Date> orgLastActivity, long defaultMaxBytes) {
			super();
			this.usages = usages;
			this.orgCreation = orgCreation;
			this.orgBytes = orgBytes;
			this.orgLastActivity = orgLastActivity;
			this.defaultMaxBytes = defaultMaxBytes;
		}

		/**
		 * Visits each org in the tree
		 */
		@Override
		public void visitOrg(int orgId, Option<OrgSso> orgOpt) {
			if (orgOpt == null) {
				log.warn("Ignoring null Option<OrgSso> for orgId: {}", orgId);
				return;
			}

			OrgSso org = orgOpt.get();
			if (org == null) {
				log.warn("Ignoring null OrgSso for orgId: {}", orgId);
				return;
			}

			if (!org.isActive()) {
				log.trace("Ignoring deactivated org: {}", org);
				return;
			}

			CustomUsage1 parentUsage = null;
			int level = 0;
			if (org.getParentOrgId() == null) {
			} else {
				parentUsage = this.usageById.get(org.getParentOrgId());
				if (parentUsage == null) {
					log.warn("No parent found for parentOrgId: {}", org.getParentOrgId());
				} else {
					level = parentUsage.getLevel() + 1;
				}
			}

			CustomUsage1 usage = new CustomUsage1(org, parentUsage, level, this.defaultMaxBytes);
			this.usageById.put(orgId, usage);

			// Set the creation date
			Date creationDate = this.orgCreation.get(orgId);
			if (creationDate == null) {
				log.warn("No creation date found for orgId: {}");
			} else {
				usage.setCreationDate(creationDate.getTime());
			}

			// Set archive bytes
			Long bytes = this.orgBytes.get(orgId);
			if (bytes == null) {
				log.warn("No archive bytes found for orgId: {}");
			} else {
				usage.setArchiveBytes(bytes);
			}

			// Set last activity date
			Date lastActivityDate = this.orgLastActivity.get(orgId);
			if (lastActivityDate == null) {
				log.warn("No last activity found for orgId: {}");
			} else {
				usage.setLastActivity(lastActivityDate.getTime());
			}

			if (usage.getOrgId() != CpcConstants.Orgs.ADMIN_ID) {
				this.usages.add(usage);
			}
		}
	}

	public static class CustomUsage1 {

		OrgSso org;
		CustomUsage1 parent;
		List<CustomUsage1> children = Lists.newArrayList();
		int level = 0;
		long archiveBytes = 0L;
		long lastActivity = -1L;
		long creationDate = -1L;
		long defaultMaxBytes = -1L;

		public CustomUsage1(OrgSso org, CustomUsage1 parent, int level, long defaultMaxBytes) {
			this.org = org;
			this.parent = parent;
			this.level = level;
			this.defaultMaxBytes = defaultMaxBytes;
			if (parent != null) {
				parent.addChild(this);
			}
		}

		/**
		 * Checks children to see if they have a newer lastActivity date than I have
		 */
		public long getLastActivity() {
			for (CustomUsage1 child : this.children) {
				if (child.getLastActivity() > this.lastActivity) {
					this.lastActivity = child.getLastActivity();
				}
			}
			return this.lastActivity;
		}

		public int getOrgId() {
			return this.org.getOrgId();
		}

		public String getOrgName() {
			return this.org.getOrgName();
		}

		public CustomUsage1 getParent() {
			return this.parent;
		}

		public List<CustomUsage1> getChildren() {
			return this.children;
		}

		public int getLevel() {
			return this.level;
		}

		public long getArchiveBytes() {
			return this.archiveBytes;
		}

		public long getAvailableBytes() {
			long maxBytes = this.getMaxBytes();
			if (maxBytes < 0) {
				return -1;
			}
			if (maxBytes < this.getArchiveBytes()) {
				log.error("Error, archiveBytes ({}) is greater than maxBytes ({})", this.getArchiveBytes(), maxBytes);
			}
			return maxBytes - this.getArchiveBytes();
		}

		public long getMaxBytes() {
			Long maxBytes = this.org.getMaxBytes();
			if (maxBytes == null) {
				if (this.parent == null) {
					return this.defaultMaxBytes;
				}
				maxBytes = this.parent.getMaxBytes();
			}
			assert maxBytes != null;
			return maxBytes;
		}

		public void setArchiveBytes(long archiveBytes) {
			this.archiveBytes = archiveBytes;
		}

		public void setLastActivity(long lastActivity) {
			this.lastActivity = lastActivity;
		}

		public void setCreationDate(long ts) {
			this.creationDate = ts;
		}

		public long getCreationDate() {
			return this.creationDate;
		}

		public void addChild(CustomUsage1 usage) {
			assert !this.children.contains(usage) : "Error, parent {} already has child: {}";
			this.children.add(usage);
		}

		@Override
		public String toString() {
			return "CustomUsage[" //
					+ "orgId:" + this.org.getOrgId() //
					+ ", orgName:" + this.getOrgName() //
					+ ", level:" + this.level //
					+ ", bytes:" + this.archiveBytes //
					+ ", maxBytes:" + this.getMaxBytes() //
					+ ", lastBackup:" + new Date(this.getLastActivity()) //
					+ "]";
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof CustomUsage1)) {
				return false;
			}
			CustomUsage1 other = (CustomUsage1) o;
			return other.getOrgId() == this.getOrgId();
		}

		@Override
		public int hashCode() {
			return this.getOrgId();
		}
	}

}
