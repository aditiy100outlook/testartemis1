package com.code42.computer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.backup42.CpcConstants;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.RequestTooLargeException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.AuthorizedOrgs;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.util.SublistIterator;
import com.code42.utils.LangUtils;
import com.code42.utils.SystemProperties;
import com.code42.utils.SystemProperty;

/**
 * Finds ComputerDto instances using any one of multiple criteria
 */
public class ComputerDtoFindByCriteriaCmd extends ComputerDtoFindByCriteriaBaseCmd<List<ComputerDto>> {

	private static final Logger log = LoggerFactory.getLogger(ComputerDtoFindByCriteriaCmd.class);

	public ComputerDtoFindByCriteriaCmd(Builder data) {
		super(data);
	}

	@Override
	public List<ComputerDto> exec(CoreSession session) throws CommandException {

		if (this.data.getOrgIds() != null && this.data.getOrgIds().isEmpty()) {
			// There is no use continuing
			return Collections.emptyList();
		}

		// Filter the userId and/or orgIds (or throw UnauthorizedException)
		this.authorize(session);

		// If requesting a particular computer, verify the subject has authority for it.
		if (this.data.getComputerGuid() != null) {
			ComputerSso sso = this.run(new ComputerSsoFindByGuidCmd(this.data.getComputerGuid()), session);
			this.data.computerId(sso.getComputerId());
		}
		if (this.data.getComputerId() != null) {
			this.run(new IsComputerManageableCmd(this.data.getComputerId(), C42PermissionApp.Computer.READ), session);
		}

		// Filter hosted organizations.
		if (!session.isSystem()) {
			this.data.filterHosted();
		}

		/*
		 * This is a very rough guess on whether or not the user is attempting to sort too much data. If so, throw
		 * RequestTooLargeException.
		 */
		if (this.data.getComputerId() == null && this.data.getComputerGuid() == null && this.data.getUserId() == null
				&& this.data.search == null) {
			AuthorizedOrgs authOrgs = session.getAuthorizedOrgs();
			boolean checkQueryLimit = this.data.isObeyQueryLimit()
					&& SystemProperties.getOptionalBoolean(SystemProperty.QUERY_LIMIT, false);

			if (checkQueryLimit && this.data.getOrgIds() == null && authOrgs.isAll()) {
				throw new RequestTooLargeException();
			}
			if (checkQueryLimit && this.data.orgIds != null && this.env.isConsumerCluster()
					&& this.data.orgIds.contains(CpcConstants.Orgs.CP_ID)) {
				throw new RequestTooLargeException();
			}
		}

		if (this.data.isExportAll()) {
			if (LangUtils.hasElements(this.data.orgIds)) {
				for (Integer orgId : this.data.orgIds) {
					this.ensureNotProtectedOrg(orgId);
				}
			} else {
				this.ensureNotCPCentral();
			}
		}

		// Find the computers
		List<ComputerDto> cList = this.db.find(new ComputerDtoFindByCriteriaQuery(this.data));

		List<ComputerDto> allComputers = new ArrayList<ComputerDto>();
		SublistIterator<ComputerDto> sublistIterator = new SublistIterator<ComputerDto>(cList, SystemProperties
				.getMaxQueryInClauseSize());
		log.debug("Iterating over all computers");
		while (sublistIterator.hasNext()) {
			List<ComputerDto> computers = sublistIterator.next();
			log.debug("Retrieving a subset of computers: {}", computers.size());

			// Find and add any additional information requested for each computer
			this.runtime.run(new ComputerDtoLoadCmd(computers, this.data), session);

			allComputers.addAll(computers);
		}

		return allComputers;
	}

	/**
	 * Builds the data used by the command to run the query. The superclass is shared with the query.
	 */
	public static class Builder extends ComputerDtoFindByCriteriaBuilder<Builder, ComputerDtoFindByCriteriaCmd> {

		@Override
		public ComputerDtoFindByCriteriaCmd build() throws BuilderException {
			this.validate();
			return new ComputerDtoFindByCriteriaCmd(this);
		}
	}

}
