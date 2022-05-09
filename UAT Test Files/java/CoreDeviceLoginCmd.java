package com.code42.activation;

import com.code42.activation.DeviceLoginControllerCmd.DeviceLoginResult;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.messaging.Session;
import com.code42.peer.RemotePeer;

/**
 * Log in Core devices.
 * 
 */
public class CoreDeviceLoginCmd extends AbstractCmd<DeviceLoginResult> {

	private final LoginPacket login;
	private final DeviceDetailPacket device;

	private final RemotePeer remotePeer;
	private final Session messageSession;

	public CoreDeviceLoginCmd(LoginPacket login, DeviceDetailPacket device, RemotePeer remotePeer, Session messageSession) {
		super();
		this.login = login;
		this.device = device;
		this.remotePeer = remotePeer;
		this.messageSession = messageSession;
	}

	@Override
	public DeviceLoginResult exec(CoreSession session) throws CommandException {

		// call: DeviceLoginCmd
		final DeviceLoginResult authResult = this.run(new DeviceLoginControllerCmd(this.login, this.device), session);

		// if successful, call: DeviceLoggedInNotificationCmd
		if (authResult.getErrors().isEmpty()) {

			DeviceLoggedInNotificationCmd notificationCmd = new DeviceLoggedInNotificationCmd(authResult.getUser(),
					authResult.getComputer(), this.remotePeer, this.messageSession);
			this.run(notificationCmd, session);
		}

		return authResult;
	}
}
