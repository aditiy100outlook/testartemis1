package com.code42.webnotify;

import java.util.concurrent.TimeUnit;

import org.joda.time.DateTime;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.core.space.CoreSpace;
import com.code42.core.space.ISpaceService;
import com.code42.core.space.SpaceException;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.messaging.MessagingTransport;
import com.code42.protos.v1.space.WebNotifyHazelcast;
import com.code42.protos.v1.sps.authority.WebNotifyMessages;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

/**
 * Command for sending notifications to all Web sessions for the specified user.
 */
public class WebNotifySendCmd extends AbstractCmd<Void> {

	/* ================= Dependencies ================= */
	private ISpaceService space;
	private MessagingTransport messaging;

	/* ================= DI injection points ================= */
	@Inject
	private void setSpace(ISpaceService space) {

		this.space = space;
	}

	@Inject
	private void setMessaging(MessagingTransport messaging) {

		this.messaging = messaging;
	}

	private static final Logger log = LoggerFactory.getLogger(WebNotifyUnsubscribeCmd.class);

	/* A fixed value for now... should probably be made configurable */
	private static final int DEFAULT_RETRIES = 3;

	private final String userUid;
	private final String msg;

	/*
	 * Representing these two values under the assumption that we'll want them to be configurable at some point... and the
	 * standard way for doing that is a long/TimeUnit pair.
	 */
	private final long expiredInterval = 5;
	private final TimeUnit expiredUnits = TimeUnit.MINUTES;

	public WebNotifySendCmd(String userUid, String msg) {

		this.userUid = userUid;
		this.msg = msg;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		try {

			WebNotifyHazelcast.WebNotifySubscription sub = this.getSubscription(this.userUid);

			/* If there's no subscription (or it's empty) then return immediately */
			if (sub == null) {
				return null;
			}
			if (sub.getRecordsList().isEmpty()) {
				return null;
			}

			WebNotifyMessages.WebNotify.Builder builder = WebNotifyMessages.WebNotify.newBuilder();
			for (WebNotifyHazelcast.WebNotifyRecord record : sub.getRecordsList()) {

				builder.clear();

				/* TODO: Do we want to worry about PeerCommunicationExceptions here? */
				this.messaging.sendToPeer(record.getNode(), builder.setNotification(this.msg).setUserUid(this.userUid).build());
			}

			return null;
		} catch (SpaceException se) {

			throw new CommandException("SpaceException while sending to web notify", se);
		}
	}

	/*
	 * Get the appropriate WebNotifySubscription for the specified userUid. We're enforcing expired subscription records
	 * primarily at read time so note that this method will also update the space if an expired record is detected.
	 */
	private WebNotifyHazelcast.WebNotifySubscription getSubscription(String userUid) throws SpaceException {

		final DateTime expired = new DateTime().minusMillis((int) this.expiredUnits.toMillis(this.expiredInterval));
		Predicate<WebNotifyHazelcast.WebNotifyRecord> expiredPredicate = new Predicate<WebNotifyHazelcast.WebNotifyRecord>() {

			public boolean apply(WebNotifyHazelcast.WebNotifyRecord arg) {

				return new DateTime(arg.getLastModified()).isAfter(expired);
			}
		};

		WebNotifyHazelcast.WebNotifySubscription oldsub;
		WebNotifyHazelcast.WebNotifySubscription newsub;
		WebNotifyHazelcast.WebNotifySubscription.Builder builder = WebNotifyHazelcast.WebNotifySubscription.newBuilder();

		int attempts = 1;
		do {

			/* Too many attempts, so bail out early */
			if (attempts > DEFAULT_RETRIES) {

				log.info("Too many retries, returning null");
				return null;
			}
			++attempts;

			oldsub = this.space.getAsType(CoreSpace.WEB_NOTIFY, this.userUid, WebNotifyHazelcast.WebNotifySubscription.class);
			if (oldsub == null) {
				return null;
			}

			/* A subscription exists for this user so filter it and see what's left */
			builder.clear();
			newsub = builder.addAllRecords(Iterables.filter(oldsub.getRecordsList(), expiredPredicate)).build();

			/* If we wind up with the same thing we're safe to just return... no need to update anything. */
			if (oldsub.equals(newsub)) {
				return oldsub;
			}

		} while (!this.space.replace(CoreSpace.WEB_NOTIFY, this.userUid, oldsub, newsub));

		/* Optimistic update completed, time to return something... */
		return newsub;
	}
}
