package com.code42.recent;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.impl.AbstractCmd;
import com.code42.core.space.DefaultSpaceNamespace;
import com.code42.core.space.ISpaceService;
import com.code42.core.space.SpaceException;
import com.code42.core.space.SpaceNamingStrategy;
import com.google.inject.Inject;

/**
 * Retrieves the user's recent list from the space.
 * 
 * IMPORTANT: Any code that uses the recent list will want to synchronize on it when iterating or modifying because
 * Hazelcast will return a reference to a local list if it comes from the current node.
 * 
 * @author mscorcio
 */
public class RecentListFindByUserIdCmd extends AbstractCmd<List<RecentItem>> {

	private ISpaceService space;

	@Inject
	public void setSpaceService(ISpaceService space) {
		this.space = space;
	}

	/**
	 * Time 'til the list is removed from the space. It is regularly written to the db once an hour, so nothing will be
	 * lost when it expires. We just don't want to fill up the space with unused recent lists that never disappear.
	 */
	public static int HOURS_TO_LIVE = 24;

	private int userId;

	public RecentListFindByUserIdCmd(int userId) {
		this.userId = userId;
	}

	@Override
	public List<RecentItem> exec(CoreSession session) throws CommandException {

		this.run(new IsUserManageableCmd(this.userId, C42PermissionApp.User.READ), session);

		List<RecentItem> recentItems;
		try {
			String key = SpaceNamingStrategy.createKey(DefaultSpaceNamespace.RECENT_ITEMS.getNamespace(), this.userId + "");
			recentItems = (List<RecentItem>) this.space.get(key);
			if (recentItems == null) {
				/*
				 * Hazelcast locks are per thread so we don't have to worry about also synchronizing between the local threads
				 * on this server.
				 */
				this.space.lock(key);
				try {
					recentItems = (List<RecentItem>) this.space.get(key);
					if (recentItems == null) {
						recentItems = this.runtime.run(new RecentListLoadCmd(this.userId), session);
						/*
						 * TODO: Note that we're assuming that the concrete implementation of recentItems is actually Serializable
						 * here
						 */
						this.space.put(key, (Serializable) recentItems, (HOURS_TO_LIVE * 3600), TimeUnit.SECONDS);
					}
				} finally {
					this.space.unlock(key);
				}
			}
		} catch (SpaceException e) {
			return new LinkedList<RecentItem>();
		}

		return recentItems;
	}
}
