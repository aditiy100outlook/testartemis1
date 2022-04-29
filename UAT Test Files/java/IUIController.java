package com.backup42.app.cpc.ui;

import java.io.IOException;

import com.backup42.proserver.message.LicenseRequestMessage;
import com.backup42.proserver.message.SetMasterLicenseRequestMessage;
import com.code42.messaging.message.Message;

public interface IUIController {

	/* =============================== Methods on CPCUIController =============================== */
	public void start() throws IOException;

	public void stop();

	/**
	 * License changed lets push everyone a new license message.
	 */
	public void notifyLicenseChange();

	/* ================================= Event and message handlers ================================= */
	public void receiveMessage(Message message);

	public void receiveMessage(LicenseRequestMessage msg);

	/**
	 * Record a new master license.
	 */
	public void receiveMessage(SetMasterLicenseRequestMessage msg);
}