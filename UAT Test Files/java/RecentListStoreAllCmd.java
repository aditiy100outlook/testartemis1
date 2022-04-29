package com.code42.recent;

import java.util.List;
import java.util.Set;

import com.code42.core.CommandException;
import com.code42.core.auth.IAuthorizationService;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.core.space.DefaultSpaceNamespace;
import com.code42.core.space.ISpaceService;
import com.code42.core.space.SpaceException;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.google.inject.Inject;

/**
 * A scheduled job that routinely writes the recent lists stored in the space to the db.
 * 
 * @author mscorcio
 * 
 */
public class RecentListStoreAllCmd extends AbstractCmd<Void> {

	private static Logger log = LoggerFactory.getLogger(RecentListStoreAllCmd.class);

	private ISpaceService space;
	private IAuthorizationService auth;

	@Inject
	public void setSpaceService(ISpaceService space) {
		this.space = space;
	}

	@Inject
	public void setAuthorizationService(IAuthorizationService auth) {
		this.auth = auth;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		this.auth.isSysadmin(session);

		try {
			Set<String> localKeys = this.space.getLocalKeysInNamespace("default",
					DefaultSpaceNamespace.RECENT_ITEMS.getNamespace());

			// TODO: it would probably be more efficient to batch these up at some point
			for (String key : localKeys) {
				int userId = Integer.parseInt(key.substring(key.lastIndexOf('/') + 1));
				List<RecentItem> recentList = (List<RecentItem>) this.space.get(key);
				if (recentList != null && recentList.size() > 0) {
					this.runtime.run(new RecentListStoreCmd(userId, recentList), session);
				}
			}

			log.info("Found " + localKeys.size() + " recent lists to store in the db.");

		} catch (SpaceException e) {
			throw new CommandException("Error storing recent lists in db", e);
		}

		return null;
	}
}
