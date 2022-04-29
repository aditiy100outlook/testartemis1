package com.code42.activation;

import com.backup42.common.CPConstant;
import com.code42.CPCSessionUtil;
import com.code42.backup.central.ICentralService;
import com.code42.computer.Computer;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.hibernate.aftertx.AfterTxRunnable;
import com.code42.hibernate.aftertx.IAfterTxRunnable.Priority;
import com.code42.messaging.Session;
import com.code42.peer.RemotePeer;
import com.code42.user.User;
import com.google.inject.Inject;

/**
 * Notify interested parties that the provided computer has been AUTHORIZED and should be considered logged in.
 * 
 * Note that this is a core function; this class is primarily concerned with notifying the peer network that this client
 * should be considered authorized.
 * 
 */
public class DeviceLoggedInNotificationCmd extends DBCmd<Void> {

	private final User user;
	private final Computer computer;
	private final RemotePeer remotePeer;
	private final Session messageSession;

	@Inject
	private ICentralService central;

	public DeviceLoggedInNotificationCmd(User user, Computer computer, RemotePeer remotePeer, Session messageSession) {
		super();
		this.user = user;
		this.computer = computer;
		this.remotePeer = remotePeer;
		this.messageSession = messageSession;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.db.afterTransaction(new AfterTxRunnable(Priority.NORMAL) {

			public void run() {

				// if we commit successfully; notify the session that it's online
				CPCSessionUtil.login(DeviceLoggedInNotificationCmd.this.messageSession,
						DeviceLoggedInNotificationCmd.this.user, DeviceLoggedInNotificationCmd.this.computer.getGuid(),
						CPConstant.AppCode.CPS);

				DeviceLoggedInNotificationCmd.this.central.getPeer().loggedIn(DeviceLoggedInNotificationCmd.this.remotePeer);
			}
		});

		return null;
	}
}
