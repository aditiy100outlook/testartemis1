package com.code42.computer;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.geo.GeoLocation;
import com.code42.core.geo.IGeoService;
import com.code42.core.geo.impl.MockGeoService;
import com.code42.core.geo.impl.MockRoundRobinGeoService;
import com.code42.core.impl.AbstractCmd;
import com.code42.email.EmailUtil;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.utils.LangUtils;
import com.google.inject.Inject;

/**
 * Returns geographical location information for a computer.
 */
public class GeoLocationFindByRemoteAddressCmd extends AbstractCmd<GeoLocation> {

	private static final Logger log = LoggerFactory.getLogger(GeoLocationFindByRemoteAddressCmd.class);

	private static final long DEFAULT_TIMEOUT_SECS = 30;

	@Inject
	private IGeoService geo;

	private final String ipAddress;
	private final Long timeoutSecs;
	private IGeoService geoLocationOverride;

	public GeoLocationFindByRemoteAddressCmd(String ipAddress) {
		this(ipAddress, DEFAULT_TIMEOUT_SECS);
	}

	public GeoLocationFindByRemoteAddressCmd(String ipAddress, long timeoutSecs) {
		this.ipAddress = ipAddress;
		this.timeoutSecs = timeoutSecs;
		this.geoLocationOverride = null;
	}

	private void setGeoServiceOverride(IGeoService geoService) {
		this.geoLocationOverride = geoService;
	}

	/**
	 * Static builder that checks for an override to the GeoLocation service, if the email address matches a test pattern
	 * like foo+countryau@code42.com
	 * 
	 * @see EmailUtil#isCode42CountryCodeAddress
	 */
	public static GeoLocationFindByRemoteAddressCmd withEmailAddressOverrideCheck(String ipAddress, long timeoutSecs,
			String email) {
		GeoLocationFindByRemoteAddressCmd cmd = new GeoLocationFindByRemoteAddressCmd(ipAddress, timeoutSecs);

		if (EmailUtil.isCode42CountryCodeAddress(email)) {
			String countryCode = EmailUtil.getTestCountryCodeFromAddress(email);
			if (countryCode.equals("AU")) {
				cmd.setGeoServiceOverride(MockGeoService.AU);
			} else if (countryCode.equals("US")) {
				cmd.setGeoServiceOverride(MockGeoService.US);
			} else if (countryCode.equals("XX")) {
				cmd.setGeoServiceOverride(MockRoundRobinGeoService.INSTANCE);
			}
		}
		return cmd;
	}

	@Override
	public GeoLocation exec(CoreSession session) throws CommandException {
		// no auth for this command, may be called from GeoLocationResource which is not authenticated

		if (this.timeoutSecs < 1) {
			throw new CommandException("Illegal timeout value: {}", this.timeoutSecs);
		}

		if (!LangUtils.hasValue(this.ipAddress)) {
			return null;
		}

		// remove port number, if present
		String filteredIp = this.ipAddress.split(":")[0];

		IGeoService geoService = this.geo;
		if (this.geoLocationOverride != null) {
			log.info("Overriding GeoLocation lookup with mock service: {}", this.geoLocationOverride.toString());
			geoService = this.geoLocationOverride;
		}

		return geoService.findLocation(filteredIp, this.timeoutSecs);
	}
}