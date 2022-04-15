package com.code42.org;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.backup42.CpcConstants;
import com.backup42.common.OrgType;
import com.backup42.server.MasterServices;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.RequestTooLargeException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.AuthorizedOrgs;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.hierarchy.HierarchyNotFoundException;
import com.code42.core.hierarchy.IHierarchyService;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.OrgDtoFindByCriteriaBuilder.SortDir;
import com.code42.org.OrgDtoFindByCriteriaBuilder.SortKey;
import com.code42.stats.AggregateBackupStatsAccessors;
import com.code42.util.SublistIterator;
import com.code42.utils.LangUtils;
import com.code42.utils.Pair;
import com.code42.utils.SystemProperties;
import com.code42.utils.SystemProperty;
import com.code42.utils.option.None;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

/**
 * Finds OrgDto instances using any one of multiple criteria
 */
public class OrgDtoFindByCriteriaCmd extends DBCmd<Pair<List<OrgDto>, Integer>> {

	private static Logger log = LoggerFactory.getLogger(OrgDtoFindByCriteriaCmd.class);

	@Inject
	private IHierarchyService hier;

	private Builder builder;

	public OrgDtoFindByCriteriaCmd(Builder builder) {
		super();
		this.builder = builder;
	}

	/**
	 * @return requested page of OrgDtos and the total count of OrgDtos that match the given criteria
	 */
	@Override
	public Pair<List<OrgDto>, Integer> exec(CoreSession session) throws CommandException {

		Set<Integer> orgIds = this.getRequestedOrgIds(session);

		/*
		 * This is a very rough guess on whether or not the user is attempting to sort too much data. If so, throw
		 * RequestTooLargeException.
		 */
		if (orgIds == null && !LangUtils.hasValue(this.builder.search)) {
			boolean checkQueryLimit = this.builder.isObeyQueryLimit()
					&& SystemProperties.getOptionalBoolean(SystemProperty.QUERY_LIMIT, false);
			if (checkQueryLimit && this.auth.hasPermission(session, C42PermissionApp.AllOrg.READ)) {
				throw new RequestTooLargeException();
			}
		}

		orgIds = this.getAuthorizedOrgIds(orgIds, session);
		orgIds = this.filterOrgIds(orgIds, session);

		List<Integer> orderedOrgIds = null;
		if (this.builder.isExportAll()) {
			this.ensureNotCPCentral();

			orgIds = this.getAllOrgsFromRootIds(orgIds);

			log.debug("Exporting all organizations");
			orderedOrgIds = new ArrayList<Integer>(orgIds);
		} else {
			orderedOrgIds = this.getPage(this.sortOrgIds(orgIds, session));
		}

		// Retrieve organization DTOs in batches
		List<OrgDto> orgs = new ArrayList<OrgDto>();

		SublistIterator<Integer> orgIdIterator = new SublistIterator<Integer>(orderedOrgIds, SystemProperties
				.getMaxQueryInClauseSize());
		while (orgIdIterator.hasNext()) {
			List<Integer> orgIdSublist = orgIdIterator.next();
			log.debug("Retrieving a subset of organizations: {}", orgIdSublist.size());
			List<OrgDto> dtos = this.db.find(new OrgDtoFindByCriteriaQuery(orgIdSublist, this.builder));
			orgs.addAll(dtos);
		}

		if (orgs.isEmpty()) {
			// Nothing to load so just return what we have
			return new Pair<List<OrgDto>, Integer>(orgs, orgIds.size());
		}

		if (this.builder.isExcludeHosted()) {
			Iterables.removeIf(orgs, new Predicate<OrgDto>() {

				public boolean apply(OrgDto dto) {
					if (dto != null) {
						return MasterServices.getInstance().isHostedOrg(dto.getOrgId());
					} else {
						return false;
					}
				}
			});
		}

		// Augment the organization DTOs in batches
		List<OrgDto> augmentedOrgs = new ArrayList<OrgDto>();

		SublistIterator<OrgDto> sublistIterator = new SublistIterator<OrgDto>(orgs, SystemProperties
				.getMaxQueryInClauseSize());
		while (sublistIterator.hasNext()) {
			List<OrgDto> orgDtos = sublistIterator.next();

			// Optionally load additional data to each DTO
			this.run(new OrgDtoLoadCmd(orgDtos, this.builder), session);

			// Optionally load the org hierarchy
			if (this.builder.isIncludeInheritedOrgInfo()) {
				this.run(new OrgDtoLoadInheritedInfoCmd(orgDtos), session);
			}

			augmentedOrgs.addAll(orgDtos);
		}

		return new Pair<List<OrgDto>, Integer>(augmentedOrgs, orgIds.size());
	}

	private Set<Integer> getAllOrgsFromRootIds(Set<Integer> orgIds) {
		final Set<Integer> allOrgIds = new LinkedHashSet<Integer>();

		// Starting with each root, include the root org id and all of the child org's ids
		for (final Integer orgId : orgIds) {
			allOrgIds.add(orgId);
			try {
				allOrgIds.addAll(this.hier.getAllChildOrgs(orgId));
			} catch (final HierarchyNotFoundException hnfe) {
				log.warn("Attempt to visit the descending tree from orgId {} failed", orgId, hnfe);
			}
		}

		return allOrgIds;
	}

	// Returns null if no specific orgs were requested
	private Set<Integer> getRequestedOrgIds(CoreSession session) throws CommandException {
		// if parentOrgId is Some(null) then user is requesting the top-level orgs.
		if (!LangUtils.hasValue(this.builder.orgIds) && (this.builder.parentOrgId instanceof None)) {
			return null;
		}

		Set<Integer> orgIds = new HashSet<Integer>();
		if (!(this.builder.orgIds instanceof None)) {
			orgIds.addAll(this.builder.getOrgIds());
		}
		if (!(this.builder.parentOrgId instanceof None)) {
			Integer parentOrgId = this.builder.getParentOrgId();
			try {
				Set<Integer> childOrgIds;
				if (parentOrgId != null) {
					childOrgIds = this.hier.getChildOrgs(parentOrgId);
				} else if (this.auth.hasPermission(session, C42PermissionApp.AllOrg.READ)) {
					childOrgIds = this.hier.getChildOrgs();
				} else {
					childOrgIds = new HashSet<Integer>();
					childOrgIds.add(session.getUser().getOrgId());
				}
				orgIds.addAll(childOrgIds);
			} catch (HierarchyNotFoundException e) {
				log.warn("Unable to find child orgs for orgId: {}", parentOrgId, e);
				return new HashSet<Integer>();
			}
		}

		return orgIds;
	}

	// Returns null if all orgs is authorized
	private Set<Integer> getAuthorizedOrgIds(Set<Integer> requestedOrgIds, CoreSession session) throws CommandException {
		AuthorizedOrgs authorizedOrgs = session.getAuthorizedOrgs();

		Set<Integer> orgIds;

		switch (authorizedOrgs.getOrgListType()) {
		case ALL:
			orgIds = requestedOrgIds != null ? requestedOrgIds : null;
			break;
		case NONE:
			throw new UnauthorizedException("Subject " + session + " does not have read permission for any org.");
		case SOME:
			if (requestedOrgIds != null) {
				// Make sure the requested orgIds are allowed for the subject to read
				if (!authorizedOrgs.isAuthorized(requestedOrgIds)) {
					throw new UnauthorizedException("Subject " + session
							+ " does not have org read permission for all the requested orgs: " + LangUtils.toString(requestedOrgIds));
				}
				orgIds = requestedOrgIds;
			} else {
				// limit the query to just the orgs that the user is allowed to read
				orgIds = new HashSet<Integer>(authorizedOrgs.getAuthorizedOrgIds());
			}
			break;
		default:
			throw new CommandException("Unrecognized Option; option=" + authorizedOrgs.getOrgListType().name());
		}

		return orgIds;
	}

	private Set<Integer> filterOrgIds(Set<Integer> orgIds, CoreSession session) throws CommandException {

		final Boolean active = !(this.builder.active instanceof None) ? this.builder.active.get() : null;
		final Boolean blocked = !(this.builder.blocked instanceof None) ? this.builder.blocked.get() : null;
		final Set<OrgType> orgTypes = this.env.getClusterOrgTypes();
		final boolean filterOrgTypes = ((this.builder.orgIds instanceof None) || this.builder.orgIds.get().size() != 1);

		// Search filter
		if (LangUtils.hasValue(this.builder.search)) {
			final String search = this.builder.getSearch();
			orgIds = this.db.find(new OrgDtoFilterBySearchQuery(orgIds, search, this.env.getClusterOrgTypes()));
		}

		if (orgIds == null) {
			orgIds = new HashSet<Integer>(this.hier.getAllOrgs());
		}

		// Filter admin org
		if (!this.builder.isIncludeAdmin()) {
			orgIds.remove(CpcConstants.Orgs.ADMIN_ID);
		}

		Map<Integer, OrgSso> orgs = this.run(new OrgSsoFindMultipleByOrgIdCmd(orgIds), session);
		for (Iterator<Integer> it = orgIds.iterator(); it.hasNext();) {
			int orgId = it.next();

			OrgSso org = orgs.get(orgId);

			if (org == null) {
				log.debug("Org not found: {}", orgId);
				it.remove();
				continue;
			}

			// Filter hosted orgs
			if (!org.isMaster() && !session.isSystem()) {
				it.remove();
			}

			// Filter by active
			else if (active != null && active.booleanValue() != org.isActive()) {
				it.remove();
			}

			// Filter by org type
			else if (filterOrgTypes && !orgTypes.contains(org.getType())) {
				it.remove();
			}

			// Filter by blocked
			else if (blocked != null && blocked.booleanValue() != org.isBlocked()) {
				it.remove();
			}

		} // for each orgId

		return orgIds;
	}

	private List<Integer> sortOrgIds(Set<Integer> orgIds, CoreSession session) throws CommandException {
		SortKey sortKey = this.builder.getSortKey();

		if (sortKey == null) {
			return new ArrayList<Integer>(orgIds);
		}

		int maxResults = this.builder.offset + this.builder.limit;
		SortDir sortDir = this.builder.sortDir;

		final List<Integer> sortedOrgIds;
		switch (this.builder.getSortKey()) {
		case computerCount:
			sortedOrgIds = this.run(new OrgDtoSortByComputerCountCmd(orgIds, sortDir, maxResults), session);
			break;
		case userCount:
			sortedOrgIds = this.run(new OrgDtoSortByUserCountCmd(orgIds, sortDir, maxResults), session);
			break;
		case orgName:
			sortedOrgIds = this.run(new OrgDtoSortByOrgNameCmd(orgIds, sortDir, maxResults), session);
			break;
		case billableBytes:
			sortedOrgIds = this.run(new OrgDtoSortByBackupStatCmd<Long>(orgIds, sortDir, maxResults, this.builder
					.getTargetComputerGuid(), new AggregateBackupStatsAccessors.GetBillableBytes()), session);
			break;
		case archiveBytes:
			sortedOrgIds = this.run(new OrgDtoSortByBackupStatCmd<Long>(orgIds, sortDir, maxResults, this.builder
					.getTargetComputerGuid(), new AggregateBackupStatsAccessors.GetArchiveBytes()), session);
			break;
		case backupSessionCount:
			sortedOrgIds = this.run(new OrgDtoSortByBackupStatCmd<Integer>(orgIds, sortDir, maxResults, this.builder
					.getTargetComputerGuid(), new AggregateBackupStatsAccessors.GetBackupSessionCount()), session);
			break;
		case selectedBytes:
			sortedOrgIds = this.run(new OrgDtoSortByBackupStatCmd<Long>(orgIds, sortDir, maxResults, this.builder
					.getTargetComputerGuid(), new AggregateBackupStatsAccessors.GetSelectedBytes()), session);
			break;
		default:
			throw new RuntimeException("Unsupported sortKey: " + this.builder.getSortKey());
		}

		return sortedOrgIds;
	}

	private List<Integer> getPage(List<Integer> orgIds) throws CommandException {
		int start = this.builder.getOffset();
		int end = start + this.builder.getLimit();
		return LangUtils.slice(orgIds, start, end);
	}

	/**
	 * Builder for the command
	 */
	public static class Builder extends OrgDtoFindByCriteriaBuilder<Builder, OrgDtoFindByCriteriaCmd> {

		@Override
		public OrgDtoFindByCriteriaCmd build() throws BuilderException {
			this.validate();
			return new OrgDtoFindByCriteriaCmd(this);
		}
	}
}
