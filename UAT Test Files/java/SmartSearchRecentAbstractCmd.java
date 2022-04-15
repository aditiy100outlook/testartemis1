package com.code42.smartsearch;

import java.util.List;
import java.util.Map;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.impl.AbstractCmd;
import com.code42.recent.RecentComputer;
import com.code42.recent.RecentItem;
import com.code42.recent.RecentOrg;
import com.code42.recent.RecentServer;
import com.code42.recent.RecentUser;

abstract class SmartSearchRecentAbstractCmd extends AbstractCmd<SmartSearchMatch> {

	protected String term;
	protected Map<SmartSearchType, Integer> counts;

	public SmartSearchRecentAbstractCmd(String term, Map<SmartSearchType, Integer> counts) {
		this.setTerm(term);
		this.setCounts(counts);
	}

	public void setTerm(String term) {
		this.term = term;
	}

	public void setCounts(Map<SmartSearchType, Integer> counts) {
		this.counts = counts;
	}

	protected SmartSearchMatch search(ISmartSearchRecentMatcher matcher, List<RecentItem> recent, CoreSession session) {
		SmartSearchMatch hit = null;
		synchronized (recent) {
			for (RecentItem r : recent) {
				SmartSearchMatch h = matcher.match(r);
				if (h != null && this.isValid(r, session)) {
					if (hit == null) {
						hit = h;
					} else if (hit.getId() != r.getId()) {
						hit = null;
						break;
					}
				}
			}
		}
		return hit;
	}

	/**
	 * Returns false if the match does not exist or is not manageable
	 */
	protected boolean isValid(RecentItem match, CoreSession session) {
		if (match instanceof RecentUser) {
			int userId = (int) match.getId();
			try {
				this.runtime.run(new IsUserManageableCmd(userId, C42PermissionApp.User.READ), session);
			} catch (UnauthorizedException e) {
				return false;
			} catch (CommandException e) {
				return false;
			}
			return true;
		} else if (match instanceof RecentComputer || match instanceof RecentServer) {
			long computerId = match.getId();
			try {
				this.runtime.run(new IsComputerManageableCmd(computerId, C42PermissionApp.Computer.READ), session);
			} catch (UnauthorizedException e) {
				return false;
			} catch (CommandException e) {
				return false;
			}
			return true;
		} else if (match instanceof RecentOrg) {
			int orgId = (int) match.getId();
			try {
				this.runtime.run(new IsOrgManageableCmd(orgId, C42PermissionApp.Org.READ), session);
			} catch (UnauthorizedException e) {
				return false;
			} catch (CommandException e) {
				return false;
			}
			return true;
		} else {
			// TODO: implement this check for other types
			throw new RuntimeException("isValid check not implemented for this type; match=" + match);
		}
	}
}
