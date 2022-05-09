package com.code42.computer;

import com.backup42.CpcConstants;
import com.code42.core.db.DBRequestTooLargeException;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.sql.SQLQuery;
import com.code42.sql.SQLUtils;
import com.code42.utils.LangUtils;
import com.code42.utils.SystemProperties;

/**
 * Provides query building functionality for the ComputerDtoFindByCriteria finders (list, count, lastModified). This
 * could be a superclass of the three finders, but we generally prefer composition over inheritance.
 */
public class ComputerDtoViewUtil {

	private static final Logger log = LoggerFactory.getLogger(ComputerDtoViewUtil.class);

	public enum SortKey {
		name,
		orgId,
		userId,
		username,
		selectedBytes,
		archiveBytes,
		billableBytes,
		os,
		percentComplete,
		lastBackup,
		lastCompletedBackup,
		lastConnected
	}

	public enum SortDir {
		ASC, DESC
	}

	final static String COMPUTER_DTO_VIEW_SQL = ""

			// FCU based sorting columns
			+ "--selectSelectedBytes              , fcu_sort.selected_bytes                                                    \n"
			+ "--selectLastActivity               , fcu_sort.last_activity                                                     \n"
			+ "--selectLastCompletedBackup        , fcu_sort.last_completed_backup                                             \n"
			+ "--selectArchiveBytes               , fcu_sort.archive_bytes                                                     \n"
			+ "--selectPercentComplete            , fcu_sort.percent_complete                                                  \n"
			+ "--selectBillableBytes              , fcu_sort.billable_bytes                                                    \n"

			// This is the main view query SQL
			+ "                                   FROM t_computer AS c                                                         \n"
			+ "--joinUser                         INNER JOIN t_user AS u                                                       \n"
			+ "--joinUser                           ON (u.user_id = c.user_id AND u.org_id > 1                                 \n"
			+ "--joinUser  --andUserId                  AND u.user_id = :userId                                                \n"
			+ "--joinUser  --andOrgIds                  AND u.org_id in (:orgIds)                                              \n"
			+ "--joinUser                              )                                                                       \n"
			+ "--joinOrg                          INNER JOIN t_org AS o                                                        \n"
			+ "--joinOrg                            ON u.org_id = o.org_id                                                     \n"

			+ "--joinFcu                          LEFT OUTER JOIN (                                                            \n"
			+ "--joinFcu                            SELECT source_computer_id                                                  \n"
			+ "--selectSelectedBytes                  , MAX(fcu.selected_bytes) AS selected_bytes                              \n"
			+ "--selectLastActivity                   , MAX(fcu.last_activity) AS last_activity                                \n"
			+ "--selectLastCompletedBackup            , MAX(fcu.last_completed_backup) AS last_completed_backup                \n"
			+ "--selectArchiveBytes                   , SUM(fcu.archive_bytes) AS archive_bytes                                \n"
			// This should probably be an OR but it works on all platforms
			+ "--andComputerAlert                     , SUM(fcu.alert_state) AS alert_state                                    \n"
			+ "--selectPercentComplete                , MAX(CASE WHEN (fcu.selected_bytes = 0) THEN 0.0                        \n"
			+ "--selectPercentComplete                   ELSE ((fcu.selected_bytes - fcu.todo_bytes) * 100.0) / fcu.selected_bytes \n"
			+ "--selectPercentComplete                  END) AS percent_complete                                               \n"
			+ "--selectBillableBytes                  , SUM(CASE WHEN fcu.selected_bytes = 0 THEN 0                            \n"
			+ "--selectBillableBytes                      WHEN fcu.archive_bytes > fcu.selected_bytes * ((0.0 + fcu.selected_bytes - fcu.todo_bytes) / fcu.selected_bytes) \n"
			+ "--selectBillableBytes                      THEN fcu.archive_bytes                                               \n"
			+ "--selectBillableBytes                      ELSE fcu.selected_bytes * ((0.0 + fcu.selected_bytes - fcu.todo_bytes) / fcu.selected_bytes) \n"
			+ "--selectBillableBytes                    END) AS billable_bytes                                                 \n"
			+ "--joinFcu                            FROM t_friend_computer_usage as fcu                                        \n"
			+ "--joinFcu                            WHERE is_using = true                                                      \n"
			+ "--joinFcu  --andTargetComputerGuid   AND fcu.target_computer_guid = :targetComputerGuid                         \n"
			+ "--joinFcu                            GROUP BY source_computer_id                                                \n"
			+ "--joinFcu                          ) AS fcu_sort                                                                \n"
			+ "--joinFcu                          ON fcu_sort.source_computer_id = c.computer_id                               \n"

			+ "                                   WHERE c.parent_computer_id is null                                           \n"
			+ "--andNotHosted                     AND (o.master_guid IS NULL OR o.discriminator = 'HostedParentOrg')           \n"
			+ "--andOrgTypes                      AND o.type IN (:orgTypes)                                                    \n"
			+ "--andComputerGuid                  AND c.guid = :computerGuid                                                   \n"
			+ "--andComputerActive                AND c.active = :active                                                       \n"
			+ "--andComputerBlocked               AND c.blocked = :blocked                                                     \n"
			+ "--excludeServers                   AND c.computer_id NOT IN (SELECT computer_id FROM t_server)                  \n"
			+ "--andComputerAlert                 AND (c.alert_state > 0 OR fcu_sort.alert_state > 0)                          \n"
			+ "--andNameLike                      AND (LOWER(c.name) LIKE :nameSearch                                          \n"
			+ "--orGuidLike                            OR CAST(c.guid AS TEXT) LIKE :guidSearch                                \n"
			+ "--andNameLike                          )                                                                        \n"
			+ "--groupBy                          GROUP BY {groupBy}                                                           \n"
			+ "--orderBy                          ORDER BY {orderBy}                                                           \n"
			+ "--orderBy --desc                   DESC NULLS LAST                                                              \n"
			+ "--orderBy --asc                    ASC NULLS FIRST                                                              \n"
			+ "--limit                            LIMIT :limit                                                                 \n"
			+ "--offset                           OFFSET :offset                                                               \n";

	/**
	 * Activates JOIN and WHERE/AND SQL on the query.
	 * 
	 * @param query - a SQLQuery
	 * @param data - a ComputerDtoFindByCriteriaBuilder
	 */
	public static void filterQuery(SQLQuery query, ComputerDtoFindByCriteriaBuilder data)
			throws DBRequestTooLargeException {

		if (data.isOrgJoinRequired() || data.isUserJoinRequired()) {
			query.activate("--joinUser");
		}

		if (data.isOrgJoinRequired()) {
			query.activate("--joinOrg");
		}

		if (data.isFcuJoinRequired()) {
			query.activate("--joinFcu");
		}

		if (data.isFilterHosted()) {
			query.activate("--andNotHosted");
		}

		/*
		 * This looks odd, but we may have a targetComputerGuid without needing to join with the FCU table
		 */
		if (data.isFcuJoinRequired() && data.getTargetComputerGuid() != null
				&& data.getTargetComputerGuid().longValue() != CpcConstants.Computer.ROLLUP_TARGET_GUID) {
			query.activate("--andTargetComputerGuid");
			query.setLong("targetComputerGuid", data.getTargetComputerGuid());
		}

		if (data.getUserId() != null) {
			query.activate("--andUserId");
			query.setInteger("userId", data.getUserId());
		}

		if (data.getOrgIds() != null) {
			if (data.getOrgIds().size() > SystemProperties.getMaxQueryInClauseSize()) {
				log.warn("REQUEST_TOO_LARGE orgIds: {}", data.getOrgIds().size()); // Just making sure this gets logged
				throw new DBRequestTooLargeException("REQUEST_TOO_LARGE, orgIds: {}", data.getOrgIds().size());
			}
			query.activate("--andOrgIds");
			query.setParameterList("orgIds", data.getOrgIds());
		}

		if (data.getOrgTypes() != null) {
			query.activate("--andOrgTypes");
			query.setParameterList("orgTypes", LangUtils.toStringSet(data.getOrgTypes()));
		}

		if (data.getActive() != null) {
			query.activate("--andComputerActive");
			query.setBoolean("active", data.getActive());
		}

		if (data.getBlocked() != null) {
			query.activate("--andComputerBlocked");
			query.setBoolean("blocked", data.getBlocked());
		}

		if (data.getAlerted() != null && data.getAlerted()) {
			query.activate("--andComputerAlert");
		}

		if (data.getComputerGuid() != null) {
			query.activate("--andComputerGuid");
			query.setLong("computerGuid", data.getComputerGuid());
		}

		// Apply a flexible search like the smart search uses
		if (data.getSearch() != null) {
			String searchTerm = SQLUtils.escapeWildcards(data.getSearch().toLowerCase().trim()) + "%";
			query.activate("--andNameLike");
			query.setString("nameSearch", searchTerm);
			Long guidFilter = LangUtils.longValueOf(data.getSearch().trim());
			if (guidFilter != null) {
				// Match on the first digits of the GUID
				query.activate("--orGuidLike");
				query.setString("guidSearch", searchTerm);
			}
		}

		if (!data.isIncludeServers()) {
			query.activate("--excludeServers");
		}
	}

	public static void enableSortColumn(SQLQuery query, SortKey key) {
		switch (key) {
		case percentComplete:
			query.activate("--selectPercentComplete");
			break;
		case billableBytes:
			query.activate("--selectBillableBytes");
			break;
		case selectedBytes:
			query.activate("--selectSelectedBytes");
			break;
		case lastBackup:
			query.activate("--selectLastActivity");
			break;
		case lastCompletedBackup:
			query.activate("--selectLastCompletedBackup");
			break;
		case archiveBytes:
			query.activate("--selectArchiveBytes");
			break;
		}
	}

	/**
	 * Activates ORDER BY and paging (OFFSET, LIMIT) clauses on the query.
	 * 
	 * @param query - a SQLQuery
	 * @param data - a ComputerDtoFindByCriteriaBuilder
	 */
	public static void orderAndPageQuery(SQLQuery query, ComputerDtoFindByCriteriaBuilder data) {
		if (data.getSortKey() != null) {
			query.activate("--orderBy");
			query.orderBy(data.getSortColumn());
			query.activate(data.isDescending() ? "--desc" : "--asc");
		}

		/*
		 * We cannot use query.setMaxResults and query.setFirstResult here because the data paging may be in a subselect.
		 */
		if (data.getLimit() > 0) {
			query.activate("--limit");
			query.setInteger("limit", data.getLimit());
		}

		// Offset can be used in Postgres without limit, but not in H2.
		if (data.getOffset() > 0) {
			query.activate("--offset");
			query.setInteger("offset", data.getOffset());
		}

	}

}