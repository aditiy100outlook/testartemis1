package com.code42.smartsearch;

import java.util.Map;
import java.util.Set;

import com.backup42.common.OrgType;
import com.code42.core.CommandException;
import com.code42.core.IEnvironment;
import com.code42.core.auth.impl.AuthorizedOrgs;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.smartsearch.computer.SmartSearchComputerFindExactOnGuidQuery;
import com.code42.smartsearch.computer.SmartSearchComputerFindExactOnNameQuery;
import com.code42.smartsearch.mountpoint.SmartSearchMountPointFindExactOnNameQuery;
import com.code42.smartsearch.org.SmartSearchOrgFindExactOnNameQuery;
import com.code42.smartsearch.org.SmartSearchOrgFindExactOnRegKeyQuery;
import com.code42.smartsearch.server.SmartSearchServerFindExactOnGuidQuery;
import com.code42.smartsearch.server.SmartSearchServerFindExactOnNameQuery;
import com.code42.smartsearch.user.SmartSearchUserFindExactOnEmailQuery;
import com.code42.smartsearch.user.SmartSearchUserFindExactOnNameQuery;
import com.code42.smartsearch.user.SmartSearchUserFindExactOnUsernameQuery;
import com.code42.utils.Stopwatch;
import com.google.inject.Inject;

class SmartSearchDBFindExactCmd extends DBCmd<SmartSearchMatch> {

	private static final Logger log = LoggerFactory.getLogger(SmartSearchDBFindExactCmd.class);

	@Inject
	private IEnvironment env;

	private String term;
	private Map<SmartSearchType, Integer> counts;

	public SmartSearchDBFindExactCmd(String term, Map<SmartSearchType, Integer> counts) {
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

		Stopwatch sw = new Stopwatch();
		long serverGuidMs = -1;
		long computerGuidMs = -1;
		long usernameMs = -1;
		long emailMs = -1;
		long userNameMs = -1; // Note the capitalized Name, not the same as username
		long mountNameMs = -1;
		long serverNameMs = -1;
		long computerNameMs = -1;
		long orgNameMs = -1;
		long regkeyMs = -1;

		if (serverCount > 0) {
			sw.reset();
			hit = this.db.find(new SmartSearchServerFindExactOnGuidQuery(myClusterId, this.term));
			serverGuidMs = sw.getElapsed();
		}

		if (hit == null && computerCount > 0) {
			sw.reset();
			hit = this.db.find(new SmartSearchComputerFindExactOnGuidQuery(authorizedOrgs, clusterOrgTypes, userId, this.term));
			computerGuidMs = sw.getElapsed();
		}

		if (hit == null && userCount > 0) {
			sw.reset();
			hit = this.db.find(new SmartSearchUserFindExactOnUsernameQuery(authorizedOrgs, clusterOrgTypes, userId, this.term));
			usernameMs = sw.getElapsed();
		}

		if (hit == null && userCount > 0) {
			sw.reset();
			hit = this.db.find(new SmartSearchUserFindExactOnEmailQuery(authorizedOrgs, clusterOrgTypes, userId, this.term));
			emailMs = sw.getElapsed();
		}

		if (hit == null && userCount > 0) {
			sw.reset();
			hit = this.db.find(new SmartSearchUserFindExactOnNameQuery(authorizedOrgs, clusterOrgTypes, userId, this.term));
			userNameMs = sw.getElapsed();
		}

		if (hit == null && mountPointCount > 0) {
			sw.reset();
			hit = this.db.find(new SmartSearchMountPointFindExactOnNameQuery(myClusterId, this.term));
			mountNameMs = sw.getElapsed();
		}

		if (hit == null && serverCount > 0) {
			sw.reset();
			hit = this.db.find(new SmartSearchServerFindExactOnNameQuery(myClusterId, this.term));
			serverNameMs = sw.getElapsed();
		}

		if (hit == null && computerCount > 0) {
			sw.reset();
			hit = this.db.find(new SmartSearchComputerFindExactOnNameQuery(authorizedOrgs, clusterOrgTypes, userId, this.term));
			computerNameMs = sw.getElapsed();
		}

		if (hit == null && orgCount > 0) {
			sw.reset();
			hit = this.db.find(new SmartSearchOrgFindExactOnNameQuery(authorizedOrgs, clusterOrgTypes, this.term));
			orgNameMs = sw.getElapsed();
		}

		if (hit == null && orgCount > 0 && (this.term.length() == 16 || this.term.length() == 19)) {
			sw.reset();
			hit = this.db.find(new SmartSearchOrgFindExactOnRegKeyQuery(authorizedOrgs, clusterOrgTypes, this.term));
			regkeyMs = sw.getElapsed();
		}

		log.trace(
				"SmartSearch:: milliseconds:  serverGuid:{}, computerGuid:{}, username:{}, email:{}, user name:{}, mountName:{}, serverName:{}, computerName:{}, orgName:{}, regkey:{}",
				serverGuidMs, computerGuidMs, usernameMs, emailMs, userNameMs, mountNameMs, serverNameMs, computerNameMs,
				orgNameMs, regkeyMs);

		return hit;
	}
}
