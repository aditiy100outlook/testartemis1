package com.code42.smartsearch;

import java.util.List;
import java.util.Map;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.recent.RecentComputer;
import com.code42.recent.RecentItem;
import com.code42.recent.RecentListFindByUserIdCmd;
import com.code42.recent.RecentOrg;
import com.code42.recent.RecentServer;
import com.code42.recent.RecentUser;

class SmartSearchRecentFindLikeCmd extends SmartSearchRecentAbstractCmd {

	public SmartSearchRecentFindLikeCmd(String term, Map<SmartSearchType, Integer> counts) {
		super(term, counts);
	}

	@Override
	public SmartSearchMatch exec(CoreSession session) throws CommandException {
		int userId = session.getUser().getUserId();
		SmartSearchMatch hit = null;

		int userCount = this.counts.get(SmartSearchType.USER);
		int computerCount = this.counts.get(SmartSearchType.COMPUTER);
		int orgCount = this.counts.get(SmartSearchType.ORG);
		int serverCount = this.counts.get(SmartSearchType.SERVER);

		List<RecentItem> recent = this.runtime.run(new RecentListFindByUserIdCmd(userId), session);
		synchronized (recent) {
			if (serverCount > 0 && SmartSearchUtils.isNumber(this.term)) {
				hit = this.search(new ServerGuidPartialMatcher(), recent, session);
			}

			if (hit == null && computerCount > 0 && SmartSearchUtils.isNumber(this.term)) {
				hit = this.search(new ComputerGuidPartialMatcher(), recent, session);
			}

			if (hit == null && userCount > 0) {
				hit = this.search(new UserUsernamePartialMatcher(), recent, session);
			}

			if (hit == null && userCount > 0) {
				hit = this.search(new UserEmailPartialMatcher(), recent, session);
			}

			if (hit == null && userCount > 0) {
				hit = this.search(new UserFullNamePartialMatcher(), recent, session);
			}

			if (hit == null && serverCount > 0) {
				hit = this.search(new ServerNamePartialMatcher(), recent, session);
			}

			if (hit == null && computerCount > 0) {
				hit = this.search(new ComputerNamePartialMatcher(), recent, session);
			}

			if (hit == null && orgCount > 0) {
				hit = this.search(new OrgNamePartialMatcher(), recent, session);
			}

			if (hit == null && orgCount > 0) {
				hit = this.search(new OrgRegKeyPartialMatcher(), recent, session);
			}
		}

		return hit;
	}

	private class ServerGuidPartialMatcher implements ISmartSearchRecentMatcher {

		public SmartSearchMatch match(RecentItem r) {
			if (!(r instanceof RecentServer)) {
				return null;
			}
			RecentServer rs = (RecentServer) r;
			if (Long.toString(rs.getGuid()).startsWith(SmartSearchRecentFindLikeCmd.this.term)) {
				return new SmartSearchMatch(rs.getId(), rs.getGuid() + "", SmartSearchType.SERVER);
			}
			return null;
		}
	}

	private class ComputerGuidPartialMatcher implements ISmartSearchRecentMatcher {

		public SmartSearchMatch match(RecentItem r) {
			if (!(r instanceof RecentComputer)) {
				return null;
			}
			RecentComputer rc = (RecentComputer) r;
			if (Long.toString(rc.getGuid()).startsWith(SmartSearchRecentFindLikeCmd.this.term)) {
				return new SmartSearchMatch(rc.getId(), rc.getGuid() + "", SmartSearchType.COMPUTER);
			}
			return null;
		}
	}

	private class UserUsernamePartialMatcher implements ISmartSearchRecentMatcher {

		public SmartSearchMatch match(RecentItem r) {
			if (!(r instanceof RecentUser)) {
				return null;
			}
			RecentUser ru = (RecentUser) r;
			if (ru.getUsername().startsWith(SmartSearchRecentFindLikeCmd.this.term)) {
				return new SmartSearchMatch(ru.getId(), ru.getUsername(), SmartSearchType.USER);
			}
			return null;
		}
	}

	private class UserEmailPartialMatcher implements ISmartSearchRecentMatcher {

		public SmartSearchMatch match(RecentItem r) {
			if (!(r instanceof RecentUser)) {
				return null;
			}
			RecentUser ru = (RecentUser) r;
			String email = ru.getEmail();
			if (email != null && email.startsWith(SmartSearchRecentFindLikeCmd.this.term)) {
				return new SmartSearchMatch(ru.getId(), ru.getEmail(), SmartSearchType.USER);
			}
			return null;
		}
	}

	private class UserFullNamePartialMatcher implements ISmartSearchRecentMatcher {

		public SmartSearchMatch match(RecentItem r) {
			if (!(r instanceof RecentUser)) {
				return null;
			}
			String cleanedTerm = SmartSearchUtils.removePunctuation(SmartSearchRecentFindLikeCmd.this.term);
			RecentUser ru = (RecentUser) r;
			String firstLast = ru.getFirstLastSearch();
			String lastFirst = ru.getLastFirstSearch();
			if ((firstLast != null && firstLast.startsWith(cleanedTerm))
					|| (lastFirst != null && lastFirst.startsWith(cleanedTerm))) {
				return new SmartSearchMatch(ru.getId(), ru.getFirstName() + " " + ru.getLastName(), SmartSearchType.USER);
			}
			return null;
		}
	}

	private class ServerNamePartialMatcher implements ISmartSearchRecentMatcher {

		public SmartSearchMatch match(RecentItem r) {
			if (!(r instanceof RecentServer)) {
				return null;
			}
			RecentServer rs = (RecentServer) r;
			String name = rs.getName().toLowerCase();
			if (name.startsWith(SmartSearchRecentFindLikeCmd.this.term)) {
				return new SmartSearchMatch(rs.getId(), rs.getName(), SmartSearchType.SERVER);
			}
			return null;
		}
	}

	private class ComputerNamePartialMatcher implements ISmartSearchRecentMatcher {

		public SmartSearchMatch match(RecentItem r) {
			if (!(r instanceof RecentComputer)) {
				return null;
			}
			RecentComputer rc = (RecentComputer) r;
			String name = rc.getName().toLowerCase();
			if (name.startsWith(SmartSearchRecentFindLikeCmd.this.term)) {
				return new SmartSearchMatch(rc.getId(), rc.getName(), SmartSearchType.COMPUTER);
			}
			return null;
		}
	}

	private class OrgRegKeyPartialMatcher implements ISmartSearchRecentMatcher {

		public SmartSearchMatch match(RecentItem r) {
			if (!(r instanceof RecentOrg)) {
				return null;
			}
			RecentOrg ro = (RecentOrg) r;
			if (ro.isAdminOrg()) {
				return null;
			}
			if (ro.getRegKey() == null) {
				return null;
			}
			String regKey = ro.getRegKey().toLowerCase();
			if (regKey.startsWith(SmartSearchRecentFindLikeCmd.this.term.replaceAll("\\-", ""))) {
				return new SmartSearchMatch(ro.getId(), ro.getRegKey(), SmartSearchType.ORG);
			}
			return null;
		}
	}

	private class OrgNamePartialMatcher implements ISmartSearchRecentMatcher {

		public SmartSearchMatch match(RecentItem r) {
			if (!(r instanceof RecentOrg)) {
				return null;
			}
			RecentOrg ro = (RecentOrg) r;
			if (ro.isAdminOrg()) {
				return null;
			}
			String name = ro.getName().toLowerCase();
			if (name.startsWith(SmartSearchRecentFindLikeCmd.this.term)) {
				return new SmartSearchMatch(ro.getId(), ro.getName(), SmartSearchType.ORG);
			}
			return null;
		}
	}

}
