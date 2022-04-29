package com.code42.smartsearch;

import java.util.Map;
import java.util.Set;

import com.backup42.common.OrgType;
import com.code42.core.CommandException;
import com.code42.core.IEnvironment;
import com.code42.core.auth.impl.AuthorizedOrgs;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.smartsearch.computer.SmartSearchComputerFindPartialOnGuidQuery;
import com.code42.smartsearch.computer.SmartSearchComputerFindPartialOnNameQuery;
import com.code42.smartsearch.mountpoint.SmartSearchMountPointFindPartialOnNameQuery;
import com.code42.smartsearch.org.SmartSearchOrgFindPartialOnNameQuery;
import com.code42.smartsearch.org.SmartSearchOrgFindPartialOnRegKeyQuery;
import com.code42.smartsearch.server.SmartSearchServerFindPartialOnGuidQuery;
import com.code42.smartsearch.server.SmartSearchServerFindPartialOnNameQuery;
import com.code42.smartsearch.user.SmartSearchUserFindPartialOnEmailQuery;
import com.code42.smartsearch.user.SmartSearchUserFindPartialOnNameQuery;
import com.code42.smartsearch.user.SmartSearchUserFindPartialOnUsernameQuery;
import com.google.inject.Inject;

class SmartSearchDBFindLikeCmd extends DBCmd<SmartSearchMatch> {

	@Inject
	private IEnvironment env;

	private String term;
	private Map<SmartSearchType, Integer> counts;

	public SmartSearchDBFindLikeCmd(String term, Map<SmartSearchType, Integer> counts) {
		this.setTerm(term);
		this.setCounts(counts);
	}

	public void setTerm(String term) {
		this.term = term;
	}

	public void setCounts(Map<SmartSearchType, Integer> counts) {
		this.counts = counts;
	}

	@Override
	public SmartSearchMatch exec(CoreSession session) throws CommandException {

		SmartSearchMatch hit = null;

		int userCount = this.counts.get(SmartSearchType.USER);
		int computerCount = this.counts.get(SmartSearchType.COMPUTER);
		int orgCount = this.counts.get(SmartSearchType.ORG);
		int serverCount = this.counts.get(SmartSearchType.SERVER);
		int mountPointCount = this.counts.get(SmartSearchType.MOUNT_POINT);

		int userId = session.getUser().getUserId();
		int myClusterId = this.env.getMyClusterId();
		Set<OrgType> clusterOrgTypes = this.env.getClusterOrgTypes();
		AuthorizedOrgs authorizedOrgs = session.getAuthorizedOrgs();

		if (serverCount > 0) {
			hit = this.db.find(new SmartSearchServerFindPartialOnGuidQuery(myClusterId, this.term));
		}

		if (hit == null && computerCount > 0) {
			hit = this.db.find(new SmartSearchComputerFindPartialOnGuidQuery(authorizedOrgs, clusterOrgTypes, userId,
					this.term));
		}

		if (hit == null && userCount > 0) {
			hit = this.db.find(new SmartSearchUserFindPartialOnUsernameQuery(authorizedOrgs, clusterOrgTypes, userId,
					this.term));
		}

		if (hit == null && userCount > 0) {
			hit = this.db
					.find(new SmartSearchUserFindPartialOnEmailQuery(authorizedOrgs, clusterOrgTypes, userId, this.term));
		}

		if (hit == null && userCount > 0) {
			hit = this.db.find(new SmartSearchUserFindPartialOnNameQuery(authorizedOrgs, clusterOrgTypes, userId, this.term));
		}

		if (hit == null && serverCount > 0) {
			hit = this.db.find(new SmartSearchServerFindPartialOnNameQuery(myClusterId, this.term));
		}

		if (hit == null && computerCount > 0) {
			hit = this.db.find(new SmartSearchComputerFindPartialOnNameQuery(authorizedOrgs, clusterOrgTypes, userId,
					this.term));
		}

		if (hit == null && mountPointCount > 0) {
			hit = this.db.find(new SmartSearchMountPointFindPartialOnNameQuery(myClusterId, this.term));
		}

		if (hit == null && orgCount > 0) {
			hit = this.db.find(new SmartSearchOrgFindPartialOnNameQuery(authorizedOrgs, clusterOrgTypes, this.term));
		}

		if (hit == null && orgCount > 0) {
			hit = this.db.find(new SmartSearchOrgFindPartialOnRegKeyQuery(authorizedOrgs, clusterOrgTypes, this.term));
		}

		return hit;
	}
}
