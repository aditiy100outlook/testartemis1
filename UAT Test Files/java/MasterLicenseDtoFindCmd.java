package com.code42.license;

import java.util.Date;

import org.joda.time.DateTime;
import org.joda.time.Interval;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.commerce.util.CommerceTime;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.server.license.ProductLicense;
import com.code42.server.license.SeatUsage;
import com.code42.server.license.ServerLicense;
import com.code42.utils.Pair;
import com.code42.utils.SystemProperties;
import com.code42.utils.SystemProperty;
import com.code42.utils.Time;
import com.google.common.annotations.VisibleForTesting;

public class MasterLicenseDtoFindCmd extends AbstractCmd<MasterLicenseDto> {

	@Override
	public MasterLicenseDto exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.ALL);

		final ServerLicense serverLicense = ServerLicense.getInstance();
		final SeatUsage seatUsage = this.run(new SeatUsageCalculateCmd(serverLicense.getMaxUsers()), session);

		final Pair<Integer, Date> saasExpirationInfo = this.getSaasExpirationData(serverLicense);
		final MasterLicenseDto dto = new MasterLicenseDto(serverLicense, seatUsage, saasExpirationInfo.getOne(),
				saasExpirationInfo.getTwo());

		return dto;
	}

	@VisibleForTesting
	Pair<Integer, Date> getSaasExpirationData(final ServerLicense sl) {
		final int evalPeriod = SystemProperties.getOptionalInt(SystemProperty.SEAT_USAGE_EVALUATION_PERIOD_IN_DAYS,
				SystemProperty.SEAT_USAGE_EVALUATION_PERIOD_DEFAULT);

		final DateTime beginOfInterval = new DateTime(Time.getNow());

		final DateTime endOfInterval = beginOfInterval.plusDays(evalPeriod);
		// Look for any licenses that expire in this interval
		final Interval expirationInterval = new Interval(beginOfInterval, endOfInterval);

		int expiringLicenseCount = 0;
		Date nextExpiring = null;
		for (ProductLicense saas : sl.getActiveSaasLicenses()) {
			if (expirationInterval.contains(new DateTime(saas.getExpirationDate().getTime()))) {
				expiringLicenseCount += saas.getQuantity();
				if (nextExpiring == null) {
					nextExpiring = saas.getExpirationDate();
				} else {
					if (CommerceTime.after(nextExpiring, saas.getExpirationDate(), false)) {
						nextExpiring = saas.getExpirationDate();
					}
				}
			}
		}

		return new Pair<Integer, Date>(expiringLicenseCount, nextExpiring);
	}
}
