package com.code42.smartsearch;

import com.code42.recent.RecentItem;

interface ISmartSearchRecentMatcher {
	
	/**
	 * return null if no match else return SmartSearchMatch
	 */
	public SmartSearchMatch match(RecentItem item);
	
}