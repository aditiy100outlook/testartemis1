package com.code42.webnotify;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.core.space.CoreSpace;
import com.code42.core.space.ISpaceService;
import com.code42.core.space.SpaceException;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.protos.v1.space.WebNotifyHazelcast;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

/**
 * Exactly what it says on the tin; a command for subscribing the specified user + WebSocket/poll session to web
 * notifications.
 */
public class WebNotifySubscribeCmd extends AbstractCmd<Boolean> {

	/* ================= Dependencies ================= */
	private ISpaceService space;

	/* ================= DI injection points ================= */
	@Inject
	private void setSpace(ISpaceService space) {

		this.space = space;
	}

	private static final Logger log = LoggerFactory.getLogger(WebNotifySubscribeCmd.class);

	private static final int DEFAULT_RETRIES = 3;

	private Predicate<WebNotifyHazelcast.WebNotifyRecord> nodeGuidPred = new Predicate<WebNotifyHazelcast.WebNotifyRecord>() {

		public boolean apply(WebNotifyHazelcast.WebNotifyRecord arg) {

			return arg.getNode() == WebNotifySubscribeCmd.this.nodeGuid;
		}
	};

	private final String userUid;
	private final long nodeGuid;

	private final int retries;

	public WebNotifySubscribeCmd(String userUid, long nodeGuid) {

		this(userUid, nodeGuid, DEFAULT_RETRIES);
	}

	public WebNotifySubscribeCmd(String userUid, long nodeGuid, int retries) {

		this.userUid = userUid;
		this.nodeGuid = nodeGuid;

		this.retries = retries;
	}

	public WebNotifyHazelcast.WebNotifySubscription buildNew(WebNotifyHazelcast.WebNotifySubscription oldsub) {

		WebNotifyHazelcast.WebNotifySubscription.Builder builder = WebNotifyHazelcast.WebNotifySubscription.newBuilder();

		/* Setup the common bits for the WebNotifyRecord we'll be adding here... */
		WebNotifyHazelcast.WebNotifyRecord.Builder newRecordBuilder = WebNotifyHazelcast.WebNotifyRecord.newBuilder();
		newRecordBuilder.setNode(this.nodeGuid).setLastModified(System.currentTimeMillis());

		/* If there's no existing subscription _at all_ then build a new one containing just us and return */
		if (oldsub == null) {

			return builder.addRecords(newRecordBuilder.setCount(1).build()).build();
		}

		/*
		 * There was an existing subscription, so let's see if there's a record for our node. We take different action based
		 * on the existence or absence of this record. If there is no existing record we're fine to add a new record with a
		 * count of one, otherwise we add a new record with a count of <existing count> + 1.
		 */
		Optional<WebNotifyHazelcast.WebNotifyRecord> oldRecordOptional = Iterables.tryFind(oldsub.getRecordsList(),
				this.nodeGuidPred);
		int newCount = oldRecordOptional.isPresent() ? (oldRecordOptional.get().getCount() + 1) : 1;
		return builder.addRecords(newRecordBuilder.setCount(newCount).build()).build();
	}

	@Override
	public Boolean exec(CoreSession session) throws CommandException {

		try {

			WebNotifyHazelcast.WebNotifySubscription oldsub;
			WebNotifyHazelcast.WebNotifySubscription newsub;

			/*
			 * Note that the do loop before is entirely concerned about the optimistic update process. The process for
			 * generating a new subscription from an old is entirely encapsulated in buildNew().
			 */
			int attempts = 1;
			do {

				/* Too many attempts, so bail out early */
				if (attempts > this.retries) {

					log.info("Too many retries, returning false");
					return false;
				}
				++attempts;

				oldsub = this.space.getAsType(CoreSpace.WEB_NOTIFY, this.userUid,
						WebNotifyHazelcast.WebNotifySubscription.class);
				newsub = this.buildNew(oldsub);

				/*
				 * In this case buildNew() will ALWAYS create a result that differs in some way from it's input. Note that
				 * oldsub may be null in which case newsub should still be different.
				 */
				if (oldsub == null) {
					assert (newsub != null);
				} else {
					assert (!oldsub.equals(newsub));
				}
			} while (!this.optimisticUpdate(oldsub, newsub));

			return true;
		} catch (SpaceException se) {

			throw new CommandException("SpaceException while subscribing to web notify", se);
		}
	}

	/*
	 * Do optimistic update in whatever way is appropriate. Hazelcast complains if we do a replace(old,new) with a null
	 * old value so in that case use putIfAbsent().
	 */
	private boolean optimisticUpdate(WebNotifyHazelcast.WebNotifySubscription oldsub,
			WebNotifyHazelcast.WebNotifySubscription newsub) throws SpaceException {

		/*
		 * putIfAbsent() will return null if the key wasn't there (and the current val for the key if something was there).
		 * In our case success only happens if we get back a null here.
		 */
		return oldsub == null ? (this.space.putIfAbsent(CoreSpace.WEB_NOTIFY, this.userUid, newsub) == null) : this.space
				.replace(CoreSpace.WEB_NOTIFY, this.userUid, oldsub, newsub);
	}
}
