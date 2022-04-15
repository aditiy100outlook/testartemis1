package com.code42.license;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.backup42.app.license.MasterLicenseService;
import com.code42.core.impl.CoreBridge;
import com.code42.product.Product;
import com.code42.product.ProductFindByIdQuery;
import com.code42.server.license.MasterLicense;
import com.code42.server.license.ProductLicense;
import com.code42.server.license.SeatUsage;
import com.code42.server.license.ServerLicense;

/**
 * DTO Bean for use by the REST API.
 */
public class MasterLicenseDto {

	private String masterRegistrationKey;
	private long seatsInUse;
	private boolean lockedDown;
	private long seatCount;

	private boolean isDemo = false;
	private int remainingDemoDays = 0;

	public boolean supported = false;
	private boolean eligibleForUpgrade = false;

	private Date perpetualSupportExpirationDate;
	private int perpetualSupportDaysRemaining;

	private int expiringLicenses;
	private Date saasExpirationDate;

	private int freeTrialCount;
	private Date nextFreeTrialExpirationDate;

	private MasterLicenseService mls = MasterLicenseService.getInstance();

	public MasterLicenseDto(ServerLicense sl, SeatUsage seatUsage, Integer expiringLicensesCount,
			Date nextSaasExpirationDate) {
		super();

		this.masterRegistrationKey = sl.getPrettyMasterLicenseRegKey();
		this.seatsInUse = seatUsage.getSeatsInUse();
		this.lockedDown = sl.isLockedDown();

		this.seatCount = sl.getMaxUsers();

		this.remainingDemoDays = sl.getDemoDaysRemaining();
		this.isDemo = sl.isDemo() && !sl.isPerpetualPresent() && !sl.isSaasPresent();

		this.supported = sl.isSupported();
		this.eligibleForUpgrade = sl.isSupported();

		this.perpetualSupportExpirationDate = sl.getPerpetualSupportExpirationDate();
		this.perpetualSupportDaysRemaining = sl.getPerpetualSupportDaysRemaining();

		this.freeTrialCount = seatUsage.getFreeTrialCount();
		this.nextFreeTrialExpirationDate = seatUsage.getNextFreeTrialExpiration();

		this.saasExpirationDate = nextSaasExpirationDate;
		this.expiringLicenses = expiringLicensesCount;
	}

	/**
	 * @return a "pretty" version of the master license registration key.
	 */
	public String getMasterLicenseId() {
		return this.masterRegistrationKey;
	}

	/**
	 * @return a list of valid ProductLicenseDto instances
	 */
	public List<ProductLicenseDto> getProductLicenses() {

		final List<ProductLicense> l = this.mls.getProductLicenses();
		final List<ProductLicenseDto> dtos = new ArrayList<ProductLicenseDto>();

		/* We return only the valid licenses */
		for (ProductLicense pl : l) {
			if (pl.isValid()) {
				final Product p = CoreBridge.find(new ProductFindByIdQuery(pl.getProductId()));
				dtos.add(new ProductLicenseDto(pl, p));
			}
		}

		return dtos;
	}

	/**
	 * @return true if a valid master license has been entered into the server.
	 */
	public boolean hasMasterLicense() {
		MasterLicense master = this.mls.getMasterLicense();
		return (master != null) && master.isValid();
	}

	/**
	 * @return true if this server is currently eligible to apply an upgrade archive. 100% of their seats MUST be
	 *         supported in order for them to be upgrade-able.
	 */
	public boolean getEligibleForUpgrade() {
		return this.eligibleForUpgrade;
	}

	public long getSeatsInUse() {
		return this.seatsInUse;
	}

	public boolean isLockedDown() {
		return this.lockedDown;
	}

	public long getSeatCount() {
		return this.seatCount;
	}

	public boolean isSupported() {
		return this.supported;
	}

	public boolean isDemo() {
		return this.isDemo;
	}

	public int getRemainingDemoDays() {
		return this.remainingDemoDays;
	}

	public Date getPerpetualSupportExpirationDate() {
		return this.perpetualSupportExpirationDate;
	}

	public int getPerpetualSupportDaysRemaining() {
		return this.perpetualSupportDaysRemaining;
	}

	public boolean isUnlimited() {
		boolean unlimited = false;
		if (this.seatCount >= Integer.MAX_VALUE) {
			unlimited = true;
		}

		return unlimited;
	}

	public int getExpiringLicenses() {
		return this.expiringLicenses;
	}

	public int getFreeTrialCount() {
		return this.freeTrialCount;
	}

	public Date getNextFreeTrialExpirationDate() {
		return this.nextFreeTrialExpirationDate;
	}

	public Date getSaasExpirationDate() {
		return this.saasExpirationDate;
	}

	/**
	 * CUSTOMIZED! Added logical methods to output.
	 */
	@Override
	public String toString() {
		return "MasterLicenseDto [masterRegistrationKey=" + this.masterRegistrationKey + ", seatsInUse=" + this.seatsInUse
				+ ", lockedDown=" + this.lockedDown + ", seatCount=" + this.seatCount + ", isDemo=" + this.isDemo
				+ ", remainingDemoDays=" + this.remainingDemoDays + ", supported=" + this.supported + ", eligibleForUpgrade="
				+ this.eligibleForUpgrade + ", perpetualSupportExpirationDate=" + this.perpetualSupportExpirationDate
				+ ", perpetualSupportDaysRemaining=" + this.perpetualSupportDaysRemaining + ", mls=" + this.mls
				+ ", hasMasterLicense()=" + this.hasMasterLicense() + ", isUnlimited()=" + this.isUnlimited()
				+ ", expiringLicenses=" + this.expiringLicenses + ", freeTrialCount=" + this.freeTrialCount
				+ ", nextFreeTrialExpiration=" + this.nextFreeTrialExpirationDate + ", nextSaasExpiration="
				+ this.saasExpirationDate + "]";
	}
}
