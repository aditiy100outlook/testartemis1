package com.code42.recent;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.core.space.DefaultSpaceNamespace;
import com.code42.core.space.ISpaceService;
import com.code42.core.space.SpaceNamingStrategy;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.google.inject.Inject;

/*
 * TODO: we are currently just storing a list of recent items under a single key in the space. In the future, for
 * performance reasons we may want to implement a truly distributed list in the space where separate elements in the
 * list are stored on different nodes in the space.
 */
class RecentListAddItemCmd extends AbstractCmd<Void> {

	private static Logger log = LoggerFactory.getLogger(RecentListAddItemCmd.class);

	static int MAX_RECENT_TO_STORE = 1000;

	private ISpaceService space;

	@Inject
	public void setSpaceService(ISpaceService space) {
		this.space = space;
	}

	private RecentItem item;

	public RecentListAddItemCmd(RecentItem item) {
		this.item = item;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		int userId = session.getUser().getUserId();
		String key = SpaceNamingStrategy.createKey(DefaultSpaceNamespace.RECENT_ITEMS.getNamespace(), userId + "");

		try {
			/*
			 * Hazelcast locks are per thread so we don't have to worry about also synchronizing between the local threads on
			 * this server.
			 */
			this.space.lock(key);
			try {
				List<RecentItem> recentItems = (List<RecentItem>) this.space.get(key);

				if (recentItems == null) {
					recentItems = this.runtime.run(new RecentListLoadCmd(userId), session);
				}

				synchronized (recentItems) {
					while (recentItems.size() >= MAX_RECENT_TO_STORE) {
						recentItems.remove(recentItems.size() - 1);
					}

					recentItems.add(0, this.item);
					/*
					 * TODO: Note that we're assuming that the concrete implementation of recentItems is actually Serializable
					 * here
					 */
					this.space.put(key, (Serializable) recentItems, (RecentListFindByUserIdCmd.HOURS_TO_LIVE * 3600),
							TimeUnit.SECONDS);
				}
			} finally {
				this.space.unlock(key);
			}

		} catch (Throwable t) {
			log.info("Unable to add item to recent list; item=" + this.item, t);
		}

		return null;
	}
}
