package com.code42.server.license;

import com.backup42.common.OrgType;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.OrgSso;
import com.code42.org.OrgSsoFindByGuidCmd;
import com.google.inject.Inject;

/**
 * Determine if the provided guid has a proper backup license. This question is only answered for ENTERPRISE
 * organizations; all others are licensed by default.
 */
public class SeatIsLicensedByGuidCmd extends AbstractCmd<Boolean> {

	private final static Logger log = LoggerFactory.getLogger(SeatIsLicensedByGuidCmd.class);

	private final long guid;
	@Inject
	private ISeatUsageService seatUsageService;

	public SeatIsLicensedByGuidCmd(long guid) {
		super();
		this.guid = guid;
	}

	@Override
	public Boolean exec(CoreSession session) throws CommandException {

		// if I'm a slave then licensing is managed by the master
		if (!this.env.isMaster()) {
			return true;
		}

		// non-Enterprise orgs are not blocked by definition
		final OrgSso orgSso = this.run(new OrgSsoFindByGuidCmd(this.guid), session);
		if (orgSso.getType() != OrgType.ENTERPRISE) {
			return true;
		}

		// first check the server's status
		boolean licensed = this.seatUsageService.isGuidLicensedWithinServer(this.guid, session);
		if (log.isDebugEnabled()) {
			log.debug("SQC:: Calculated guid={} isProviderBlocked byServer=", this.guid, licensed);
		}

		// if the server, which trumps all, has licensed the guid, then the org might have a quota that is being exceeded;
		// we'll have to check
		if (licensed) {
			licensed = this.seatUsageService.isGuidLicensedWithinOrg(this.guid, orgSso.getOrgId(), session);
		}

		if (log.isDebugEnabled()) {
			log.debug("SQC:: Calculated guid={} SeatIsLicensedByGuidCmd={}", this.guid, licensed);
		}

		return licensed;
	}
}
