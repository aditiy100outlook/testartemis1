package com.code42.license;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.server.license.ISeatUsageService;
import com.code42.server.license.SeatUsage;
import com.google.inject.Inject;

/**
 * Calculate seat usage numbers for the current server
 */
public class SeatUsageCalculateCmd extends AbstractCmd<SeatUsage> {

	@Inject
	ISeatUsageService seatUsage;

	private final int maxUsers;

	public SeatUsageCalculateCmd(int maxUsers) {
		this.maxUsers = maxUsers;
	}

	@Override
	public SeatUsage exec(CoreSession session) throws CommandException {
		return this.seatUsage.getSeatUsage(this.maxUsers);
	}
}
