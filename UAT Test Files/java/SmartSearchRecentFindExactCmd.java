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

class SmartSearchRecentFindExactCmd extends SmartSearchRecentAbstractCmd {

	public SmartSearchRecentFindExactCmd(String term, Map<SmartSearchType, Integer> counts) {
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
				hit = this.search(new ServerGuidExactMatcher(), recent, session);
			}

			if (hit == null && computerCount > 0 && SmartSearchUtils.isNumber(this.term)) {
				hit = this.search(new ComputerGuidExactMatcher(), recent, session);
			}

			if (hit == null && userCount > 0) {
				hit = this.search(new UserUsernameExactMatcher(), recent, session);
			}

			if (hit == null && userCount > 0) {
				hit = this.search(new UserEmailExactMatcher(), recent, session);
			}

			if (hit == null && userCount > 0) {
				hit = this.search(new UserFullNameExactMatcher(), recent, session);
			}

			if (hit == null && serverCount > 0) {
				hit = this.search(new ServerNameExactMatcher(), recent, session);
			}

			if (hit == null && computerCount > 0) {
				hit = this.search(new ComputerNameExactMatcher(), recent, session);
			}

			if (hit == null && orgCount > 0) {
				hit = this.search(new OrgNameExactMatcher(), recent, session);
			}

			if (hit == null && orgCount > 0 && (this.term.length() == 16 || this.term.length() == 19)) {
				hit = this.search(new OrgRegKeyExactMatcher(), recent, session);
			}
		}
		return hit;
	}

	private class ServerGuidExactMatcher implements ISmartSearchRecentMatcher {

		public SmartSearchMatch match(RecentItem r) {
			if (!(r instanceof RecentServer)) {
				return null;
			}
			RecentServer rs = (RecentServer) r;
			if (rs.getGuid() == Long.parseLong(SmartSearchRecentFindExactCmd.this.term)) {
				return new SmartSearchMatch(rs.getId(), rs.getGuid() + "", SmartSearchType.SERVER);
			}
			return null;
		}
	}

	private class ComputerGuidExactMatcher implements ISmartSearchRecentMatcher {

		public SmartSearchMatch match(RecentItem r) {
			if (!(r instanceof RecentComputer)) {
				return null;
			}
			RecentComputer rc = (RecentComputer) r;
			if (rc.getGuid() == Long.parseLong(SmartSearchRecentFindExactCmd.this.term)) {
				return new SmartSearchMatch(rc.getId(), rc.getGuid() + "", SmartSearchType.COMPUTER);
			}
			return null;
		}
	}

	private class UserUsernameExactMatcher implements ISmartSearchRecentMatcher {

		public SmartSearchMatch match(RecentItem r) {
			if (!(r instanceof RecentUser)) {
				return null;
			}
			RecentUser ru = (RecentUser) r;
			if (ru.getUsername().equals(SmartSearchRecentFindExactCmd.this.term)) {
				return new SmartSearchMatch(ru.getId(), ru.getUsername(), SmartSearchType.USER);
			}
			return null;
		}
	}

	private class UserEmailExactMatcher implements ISmartSearchRecentMatcher {

		public SmartSearchMatch match(RecentItem r) {
			if (!(r instanceof RecentUser)) {
				return null;
			}
			RecentUser ru = (RecentUser) r;
			String email = ru.getEmail();
			if (email != null && email.equals(SmartSearchRecentFindExactCmd.this.term)) {
				return new SmartSearchMatch(ru.getId(), ru.getEmail(), SmartSearchType.USER);
			}
			return null;
		}
	}

	private class UserFullNameExactMatcher implements ISmartSearchRecentMatcher {

		public SmartSearchMatch match(RecentItem r) {
			if (!(r instanceof RecentUser)) {
				return null;
			}
			RecentUser ru = (RecentUser) r;
			String cleanedTerm = SmartSearchUtils.removePunctuation(SmartSearchRecentFindExactCmd.this.term);
			if (cleanedTerm.equals(ru.getFirstLastSearch()) || cleanedTerm.equals(ru.getLastFirstSearch())) {
				return new SmartSearchMatch(ru.getId(), ru.getFirstName() + " " + ru.getLastName(), SmartSearchType.USER);
			}
			return null;
		}
	}

	private class ServerNameExactMatcher implements ISmartSearchRecentMatcher {

		public SmartSearchMatch match(RecentItem r) {
			if (!(r instanceof RecentServer)) {
				return null;
			}
			RecentServer rs = (RecentServer) r;
			String name = rs.getName().toLowerCase();
			if (name.equals(SmartSearchRecentFindExactCmd.this.term)) {
				return new SmartSearchMatch(rs.getId(), rs.getName(), SmartSearchType.SERVER);
			}
			return null;
		}
	}

	private class ComputerNameExactMatcher implements ISmartSearchRecentMatcher {

		public SmartSearchMatch match(RecentItem r) {
			if (!(r instanceof RecentComputer)) {
				return null;
			}
			RecentComputer rc = (RecentComputer) r;
			String name = rc.getName().toLowerCase();
			if (name.equals(SmartSearchRecentFindExactCmd.this.term)) {
				return new SmartSearchMatch(rc.getId(), rc.getName(), SmartSearchType.COMPUTER);
			}
			return null;
		}
	}

	private class OrgRegKeyExactMatcher implements ISmartSearchRecentMatcher {

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
			if (regKey.equals(SmartSearchRecentFindExactCmd.this.term.replaceAll("\\-", ""))) {
				return new SmartSearchMatch(ro.getId(), ro.getRegKey(), SmartSearchType.ORG);
			}
			return null;
		}
	}

	private class OrgNameExactMatcher implements ISmartSearchRecentMatcher {

		public SmartSearchMatch match(RecentItem r) {
			if (!(r instanceof RecentOrg)) {
				return null;
			}
			RecentOrg ro = (RecentOrg) r;
			if (ro.isAdminOrg()) {
				return null;
			}
			String name = ro.getName().toLowerCase();
			if (name.equals(SmartSearchRecentFindExactCmd.this.term)) {
				return new SmartSearchMatch(ro.getId(), ro.getName(), SmartSearchType.ORG);
			}
			return null;
		}
	}

}
