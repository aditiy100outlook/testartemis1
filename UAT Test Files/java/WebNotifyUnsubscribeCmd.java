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
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

/**
 * Exactly what it says on the tin; a command for unsubscribing the specified user + WebSocket/poll session to web
 * notifications.
 */
public class WebNotifyUnsubscribeCmd extends AbstractCmd<Boolean> {

	/* ================= Dependencies ================= */
	private ISpaceService space;

	/* ================= DI injection points ================= */
	@Inject
	private void setSpace(ISpaceService space) {

		this.space = space;
	}

	private static final Logger log = LoggerFactory.getLogger(WebNotifyUnsubscribeCmd.class);

	private static final int DEFAULT_RETRIES = 3;

	private Predicate<WebNotifyHazelcast.WebNotifyRecord> nodePred = new Predicate<WebNotifyHazelcast.WebNotifyRecord>() {

		public boolean apply(WebNotifyHazelcast.WebNotifyRecord arg) {

			return arg.getNode() == WebNotifyUnsubscribeCmd.this.nodeGuid;
		}
	};

	private final String userUid;
	private final long nodeGuid;

	private final int retries;

	public WebNotifyUnsubscribeCmd(String userUid, long nodeGuid) {

		this(userUid, nodeGuid, DEFAULT_RETRIES);
	}

	public WebNotifyUnsubscribeCmd(String userUid, long nodeGuid, int retries) {

		this.userUid = userUid;
		this.nodeGuid = nodeGuid;

		this.retries = retries;
	}

	public WebNotifyHazelcast.WebNotifySubscription buildNew(WebNotifyHazelcast.WebNotifySubscription oldsub) {

		WebNotifyHazelcast.WebNotifySubscription.Builder builder = WebNotifyHazelcast.WebNotifySubscription.newBuilder();

		/* No existing subscription, so no action to take... just return null */
		if (oldsub == null) {

			return null;
		}

		/*
		 * There was an existing subscription, so let's see if there's a record for our node. We take different action based
		 * on the existence or absence of this record.
		 */
		Optional<WebNotifyHazelcast.WebNotifyRecord> recordOptional = Iterables.tryFind(oldsub.getRecordsList(),
				this.nodePred);
		if (!recordOptional.isPresent()) {

			/* No record for the target node in the subscription so nothing further to do... return what we got */
			return oldsub;
		}
		WebNotifyHazelcast.WebNotifyRecord record = recordOptional.get();

		/*
		 * There was a record for the target node so we'll need to replace it with a new record. Under all remaining
		 * circumstances we'll need all records _other than_ the target record so start by adding those... we call this the
		 * "others" collection.
		 * 
		 * Note that an unsubscribe operation has no effect on the last_modified values of all other records in the
		 * subscription.
		 */
		builder.addAllRecords(Iterables.filter(oldsub.getRecordsList(), Predicates.not(Predicates.equalTo(record))));

		/*
		 * Node record has a count greater than one, so decrement and re-add. There's no impact on the last_modified value.
		 */
		if (record.getCount() > 1) {

			builder.addRecords(
					WebNotifyHazelcast.WebNotifyRecord.newBuilder().setCount(record.getCount() - 1).setNode(this.nodeGuid)
							.setLastModified(record.getLastModified()).build()).build();
		}

		/*
		 * We observed a count of something larger than one (meaning the if block above got triggered or something less than
		 * that... meaning we just want to return the "others" we added originally. Either way we're safe to just return
		 * here.
		 */
		return builder.build();
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
				 * buildNew() could return the something that's equal to oldsub... in that case just return true here (i.e.
				 * don't bother with the replace() if the end result is the same)
				 */
				if (newsub.equals(oldsub)) {
					return true;
				}
			} while (!this.space.replace(CoreSpace.WEB_NOTIFY, this.userUid, oldsub, newsub));

			return true;
		} catch (SpaceException se) {

			throw new CommandException("SpaceException while unsubscribing to web notify", se);
		}
	}
}
