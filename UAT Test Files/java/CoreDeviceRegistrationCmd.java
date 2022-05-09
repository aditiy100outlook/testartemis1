package com.code42.activation;

import com.code42.activation.DeviceRegistrationCmd.DeviceRegistrationResult;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;

/**
 * Register a Core device.
 * 
 */
public class CoreDeviceRegistrationCmd extends AbstractCmd<DeviceRegistrationResult> {

	private final RegistrationPacket registration;
	private final DeviceDetailPacket device;

	public CoreDeviceRegistrationCmd(RegistrationPacket registration, DeviceDetailPacket device) {
		super();
		this.registration = registration;
		this.device = device;
	}

	@Override
	public DeviceRegistrationResult exec(CoreSession session) throws CommandException {

		final DeviceRegistrationCmd registerCmd = new DeviceRegistrationCmd(this.registration, this.device);
		final DeviceRegistrationResult result = this.run(registerCmd, session);

		return result;
	}
}
