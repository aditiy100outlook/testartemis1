package com.code42.computer;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.geo.GeoLocation;
import com.code42.core.geo.IGeoService;
import com.code42.core.impl.AbstractCmd;
import com.code42.utils.LangUtils;
import com.google.inject.Inject;

/**
 * Returns geographical location information for a computer.
 */
public class GeoLocationFindByComputerIdCmd extends AbstractCmd<GeoLocation> {

	private IGeoService geo;

	@Inject
	public void setGeoService(IGeoService geo) {
		this.geo = geo;
	}

	private long computerId;

	public GeoLocationFindByComputerIdCmd(long computerId) {
		this.computerId = computerId;
	}

	@Override
	public GeoLocation exec(CoreSession session) throws CommandException {
		Computer computer = this.runtime.run(new ComputerFindByIdCmd(this.computerId), session);

		if (computer == null) {
			return null;
		}

		String remoteAddress = computer.getRemoteAddress();

		if (!LangUtils.hasValue(remoteAddress)) {
			return null;
		}

		String[] parts = remoteAddress.split(":");
		String ipAddress = parts[0];

		return this.geo.findLocation(ipAddress);
	}
}