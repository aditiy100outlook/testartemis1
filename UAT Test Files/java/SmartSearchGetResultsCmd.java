package com.code42.smartsearch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.backup42.common.OrgType;
import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.AuthorizedOrgs;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.smartsearch.computer.SmartSearchComputerFindExactOnGuidQuery;
import com.code42.smartsearch.user.SmartSearchUserFindExactOnEmailQuery;
import com.code42.utils.Stopwatch;

public class SmartSearchGetResultsCmd extends DBCmd<Map<String, Object>> {

	private static final Logger log = LoggerFactory.getLogger(SmartSearchGetResultsCmd.class);

	private String term;

	public SmartSearchGetResultsCmd(String term) {
		this.term = term.trim().toLowerCase();
	}

	@Override
	public Map<String, Object> exec(CoreSession session) throws CommandException {

		int userId = session.getUser().getUserId();
		Set<OrgType> clusterOrgTypes = this.env.getClusterOrgTypes();
		AuthorizedOrgs authorizedOrgs = session.getAuthorizedOrgs();

		SmartSearchMatch hit = null;
		Map<SmartSearchType, Integer> counts = new HashMap<SmartSearchType, Integer>();
		List<SmartSearchMatch> results = new ArrayList<SmartSearchMatch>();

		// Try to short-circuit the expensive searching
		if (SmartSearchUtils.isGuid(this.term)) {
			hit = this.db.find(new SmartSearchComputerFindExactOnGuidQuery(authorizedOrgs, clusterOrgTypes, userId, this.term));
			if (hit != null) {
				counts.put(SmartSearchType.COMPUTER, 1);
			}
		} else if (SmartSearchUtils.isEmail(this.term)) {
			hit = this.db.find(new SmartSearchUserFindExactOnEmailQuery(authorizedOrgs, clusterOrgTypes, userId, this.term));
			if (hit != null) {
				counts.put(SmartSearchType.USER, 1);
			}
		}

		if (hit == null) {
			// Search all of the searchable types

			Stopwatch sw = new Stopwatch();
			long countMs = 0;
			long recentExactMs = 0;
			long dbExactMs = -1;
			long recentLikeMs = -1;
			long dbLikeMs = -1;
			long relatedMs = -1;

			counts = this.runtime.run(new SmartSearchCountCmd(this.term), session);
			countMs = sw.getElapsed();

			sw.reset();
			hit = this.runtime.run(new SmartSearchRecentFindExactCmd(this.term, counts), session);
			recentExactMs = sw.getElapsed();

			if (hit == null) {
				sw.reset();
				hit = this.runtime.run(new SmartSearchDBFindExactCmd(this.term, counts), session);
				dbExactMs = sw.getElapsed();
			}

			if (hit == null) {
				sw.reset();
				hit = this.runtime.run(new SmartSearchRecentFindLikeCmd(this.term, counts), session);
				recentLikeMs = sw.getElapsed();
			}

			if (hit == null) {
				sw.reset();
				hit = this.runtime.run(new SmartSearchDBFindLikeCmd(this.term, counts), session);
				dbLikeMs = sw.getElapsed();
			}

			if (hit != null) {
				try {
					sw.reset();
					results = this.runtime.run(new SmartSearchGetRelatedCmd(hit), session);
					relatedMs = sw.getElapsed();
				} catch (UnauthorizedException e) {
					results = new ArrayList<SmartSearchMatch>();
				}
			} else {
				results = new ArrayList<SmartSearchMatch>();
			}

			log.trace(
					"SmartSearch:: milliseconds:  counting:{}, recentExact:{}, dbExact:{}, recentLike:{}, dbLike:{}, related:{}",
					countMs, recentExactMs, dbExactMs, recentLikeMs, dbLikeMs, relatedMs);
		}

		if (!this.auth.hasPermission(session, C42PermissionPro.System.SYSTEM_SETTINGS)) {
			counts.remove(SmartSearchType.SERVER);
			counts.remove(SmartSearchType.MOUNT_POINT);
		}

		if (!this.auth.hasPermission(session, C42PermissionApp.Org.READ)) {
			counts.remove(SmartSearchType.ORG);
		}

		Map<String, Object> rv = new HashMap<String, Object>();
		rv.put("counts", counts);
		rv.put("results", results);
		rv.put("hit", hit);

		return rv;
	}

}
