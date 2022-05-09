package com.code42.user;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.backup42.CpcConstants;
import com.backup42.common.CPErrors;
import com.code42.auth.RequestedDataFilterCmd;
import com.code42.auth.RequestedDataFilterCmd.Result;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.RequestTooLargeException;
import com.code42.core.auth.impl.AuthorizedOrgs;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.OrgSso;
import com.code42.org.OrgSsoFindMultipleByOrgIdCmd;
import com.code42.stats.AggregateBackupStatsAccessors;
import com.code42.user.UserDtoFindByCriteriaBuilder.SortDir;
import com.code42.util.SublistIterator;
import com.code42.utils.LangUtils;
import com.code42.utils.Pair;
import com.code42.utils.Stopwatch;
import com.code42.utils.SystemProperties;
import com.code42.utils.SystemProperty;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Finds UserDto instances using any one of multiple criteria
 * 
 * @see UserIdFilterAndSortQuery
 * @see UserDtoFindByUserQuery
 */
public class UserDtoFindByCriteriaCmd extends DBCmd<Pair<List<UserDto>, Integer>> {

	private static final Logger log = LoggerFactory.getLogger(UserDtoFindByCriteriaCmd.class);

	protected final UserDtoFindByCriteriaBuilder<?, ?> data;

	public UserDtoFindByCriteriaCmd(UserDtoFindByCriteriaBuilder data) {
		this.data = data;
	}

	/**
	 * @return requested page of UserDtos and the total count of UserDtos that match the criteria
	 */
	@Override
	public Pair<List<UserDto>, Integer> exec(CoreSession session) throws CommandException {
		Stopwatch sw = new Stopwatch();

		/*
		 * Filter the requested user and orgIds based on the subjects allowable orgs
		 */
		Result result = this.run(new RequestedDataFilterCmd(this.data.userId, this.data.orgIds), session);
		this.data.userId = result.getUserId();
		this.data.orgIds = result.getOrgIds();

		/*
		 * This is a very rough guess on whether or not the user is attempting to sort too much data. If so, throw
		 * RequestTooLargeException.
		 */
		if (this.data.userId == null && this.data.search == null) {
			AuthorizedOrgs authOrgs = session.getAuthorizedOrgs();
			boolean checkQueryLimit = this.data.isObeyQueryLimit()
					&& SystemProperties.getOptionalBoolean(SystemProperty.QUERY_LIMIT, false);
			if (checkQueryLimit && this.data.orgIds == null && authOrgs.isAll()) {
				throw new RequestTooLargeException();
			}
			if (checkQueryLimit && this.data.orgIds != null && this.env.isConsumerCluster()
					&& this.data.orgIds.contains(CpcConstants.Orgs.CP_ID)) {
				throw new RequestTooLargeException();
			}
		}

		/*
		 * data.orgIds is null if we're looking at all orgs. userId is null if we just show all users in the specified orgs.
		 * If data.userId is populated, there will be no sorting necessary
		 */
		List<Integer> sortedUserIds;
		if (this.data.userId != null) {
			sortedUserIds = Lists.newArrayList(this.data.userId);
		} else {

			/*
			 * Remove users of orgs that are not in this cluster
			 */
			this.data.orgTypes(this.env.getClusterOrgTypes());

			/*
			 * Do database filtering and sorting.
			 */
			List<Integer> filteredUserIds = this.db.find(new UserIdFilterAndSortQuery(this.data));

			if (this.data.isBackupSortKey()) {
				/*
				 * Do space sorting using the filtered list we just collected above
				 */
				Set<Integer> filteredUserIdSet = Sets.newHashSet(filteredUserIds);

				int maxResults = this.data.offset + this.data.limit;
				SortDir sortDir = this.data.sortDir;

				switch (this.data.getSortKey()) {
				case archiveBytes:
					sortedUserIds = this.run(new UserDtoSortByBackupStatCmd<Long>(filteredUserIdSet, null, sortDir, maxResults,
							this.data.getTargetComputerGuid(), new AggregateBackupStatsAccessors.GetArchiveBytes()), session);
					break;
				case billableBytes:
					sortedUserIds = this.run(new UserDtoSortByBackupStatCmd<Long>(filteredUserIdSet, null, sortDir, maxResults,
							this.data.getTargetComputerGuid(), new AggregateBackupStatsAccessors.GetBillableBytes()), session);
					break;
				case selectedBytes:
					sortedUserIds = this.run(new UserDtoSortByBackupStatCmd<Long>(filteredUserIdSet, null, sortDir, maxResults,
							this.data.getTargetComputerGuid(), new AggregateBackupStatsAccessors.GetSelectedBytes()), session);
					break;
				case percentComplete:
					sortedUserIds = this.run(new UserDtoSortByBackupStatCmd<Double>(filteredUserIdSet, null, sortDir, maxResults,
							this.data.getTargetComputerGuid(), new AggregateBackupStatsAccessors.GetPercentComplete()), session);
					break;
				case computerCount:
					sortedUserIds = this.run(new UserDtoSortByComputerCountCmd(filteredUserIdSet, sortDir, maxResults), session);
					break;
				default:
					throw new RuntimeException("Unsupported sortKey: " + this.data.getSortKey());
				}
			} else {
				// We sorted in SQL or no sort was requested
				sortedUserIds = filteredUserIds;
			}
		} // else many users

		// Filter hosted orgs
		boolean removed = false;
		if (!session.isSystem()) {
			Map<Integer, UserSso> users = this.runtime.run(new UserSsoFindMultipleByUserIdCmd(sortedUserIds), session);
			Set<Integer> orgIds = new HashSet<Integer>();
			for (UserSso u : users.values()) {
				if (u != null) {
					orgIds.add(u.getOrgId());
				}
			}
			Map<Integer, OrgSso> orgs = this.runtime.run(new OrgSsoFindMultipleByOrgIdCmd(orgIds), session);
			log.debug("Retrieved org sso: {}", orgs);
			for (Iterator<Integer> it = sortedUserIds.iterator(); it.hasNext();) {
				UserSso user = users.get(it.next());
				log.debug("Retrieved user: {}", user);
				OrgSso org = orgs.get(user.getOrgId());
				if (org != null && org.getMasterGuid() != null) {
					it.remove();
					removed = true;
				}
			}
		}

		int totalCount = sortedUserIds.size();
		if (removed && totalCount == 0) {
			throw new CommandException(CPErrors.Cluster.HOSTED_UNAVAILABLE, "Hosted users unavailable");
		}

		if (this.data.isExportAll()) {
			if (this.data.orgIds == null || this.data.orgIds.contains(CpcConstants.Orgs.CP_ID)) {
				this.ensureNotCPCentral();
			}
		} else {
			sortedUserIds = this.getPage(sortedUserIds);
		}

		// Create master list of DTOs
		List<UserDto> allDtos = new ArrayList<UserDto>();

		// Create an iterator that returns sublists of ids
		SublistIterator<Integer> sublistIterator = new SublistIterator<Integer>(sortedUserIds, SystemProperties
				.getMaxQueryInClauseSize());

		log.debug("Iterating over all user ids");
		while (sublistIterator.hasNext()) {
			List<Integer> userIds = sublistIterator.next();
			log.debug("Retrieving a subset of users: {}", userIds.size());

			List<UserDto> list = this.db.find(new UserDtoFindByUserQuery(userIds));
			// Add any requested additional information to each UserDto instance
			this.run(new UserDtoLoadCmd(list, this.data), session);

			allDtos.addAll(list);
		}

		log.debug("Found {} total users. Returning {} in {} ms", totalCount, allDtos.size(), sw.getElapsed());
		return new Pair<List<UserDto>, Integer>(allDtos, totalCount);
	}

	private List<Integer> getPage(List<Integer> userIds) throws CommandException {
		int start = this.data.getOffset();
		int end = start + this.data.getLimit();
		return LangUtils.slice(userIds, start, end);
	}

	/**
	 * Data builder for the query generator
	 */
	public static class Builder extends UserDtoFindByCriteriaBuilder<Builder, UserDtoFindByCriteriaCmd> {

		@Override
		public UserDtoFindByCriteriaCmd build() throws BuilderException {
			this.validate();
			return new UserDtoFindByCriteriaCmd(this);
		}

	}

}