package com.code42.server.license;

import java.security.PrivateKey;
import java.util.Date;

import org.joda.time.DateMidnight;

import com.backup42.app.license.LicenseUtils;
import com.backup42.app.license.data.ext.ProductLicenseRecordDataProvider;
import com.code42.commerce.product.ProductConstants.BlackProductRef;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.IAuthorizationService;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.licenserecord.MasterLicenseRecordFindByRegistrationKeyCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.product.Product;
import com.code42.product.ProductDetailFindByReferenceIdCmd;
import com.code42.server.license.AccountTrialExtensionCmd.TrialExtensionDto;
import com.code42.utils.Time;
import com.google.inject.Inject;

/**
 * Extend a PROe trial. The logic here is a conversion from the older trialExtension.groovy resource (see CP-7421).
 * 
 * @author ahelgeso
 * 
 */
public class AccountTrialExtensionCmd extends DBCmd<TrialExtensionDto> {

	private static Logger log = LoggerFactory.getLogger(AccountTrialExtensionCmd.class);

	@Inject
	private IAuthorizationService auth;

	private final String regKey;
	private final int duration;
	private final Integer backdate;

	public AccountTrialExtensionCmd(String regKey, int duration) {
		this(regKey, duration, null);
	}

	public AccountTrialExtensionCmd(String regKey, int duration, Integer backdate) {

		this.regKey = regKey.toLowerCase().replace("-", "");
		this.duration = duration;
		this.backdate = backdate;
	}

	public enum Result {
		NO_PRODUCT, //
		NO_MLR
	}

	@Override
	public TrialExtensionDto exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionApp.AllOrg.UPDATE_BASIC);

		Product demoProduct = this.run(new ProductDetailFindByReferenceIdCmd(BlackProductRef.B42_MASTER), session);
		if (demoProduct == null) {
			throw new CommandException(Result.NO_PRODUCT, "Could not find product with id {}", BlackProductRef.B42_MASTER);
		}

		MasterLicenseRecord mlr = this.run(new MasterLicenseRecordFindByRegistrationKeyCmd(this.regKey), session);
		if (mlr == null) {
			throw new CommandException(Result.NO_MLR, "No MLR found for registration key");
		}

		DateMidnight creationDate = new DateMidnight(Time.getNow());
		if (this.backdate != null) {
			log.info("Back-dating license creation by {} days", this.backdate);
			creationDate = creationDate.minusDays(this.backdate);
		}

		int epochPrimeOffset = LicenseUtils.computeDateOffset(creationDate.toDate());

		log.info("TRIAL EXT:: mlr = {}", mlr);
		log.info("TRIAL EXT:: product = {}", demoProduct);

		/*
		 * At this point we're finally ready to create the product license. We also have to validate the license immediately
		 * after creation, in part to make sure it's good but mostly to setup it's internal state before proceeding.
		 */
		PrivateKey mlrPrivateKey = mlr.retrieveKeyPair().getPrivate();
		ProductLicense productLicense = ProductLicense.generateInstance(1, demoProduct, this.duration, epochPrimeOffset,
				mlrPrivateKey);

		productLicense.setMasterLicense(mlr.getMasterLicense());
		ValidationResult validation = productLicense.validate();

		if (validation.getStatus() != License.VALIDATION_SUCCESSFUL) {
			log.info("Validation result: {}", validation.getMessage());
		}

		log.info("TRIAL EXT:: productLicense: {}", productLicense);

		ProductLicenseRecord productLicenseRecord = ProductLicenseRecord.generateInstance(mlr.getId(), productLicense,
				productLicense.getQuantity());

		ProductLicenseRecordDataProvider productRecordProvider = new ProductLicenseRecordDataProvider();
		productRecordProvider.save(productLicenseRecord);

		TrialExtensionDto dto = new TrialExtensionDto();
		dto.setRegKey(this.regKey);
		dto.setLicense(productLicense.getLicense());
		dto.setDuration(this.duration);
		dto.setCreationDate(productLicense.getCreationDate());
		dto.setExpirationDate(productLicense.getExpirationDate());
		dto.setProductLicense(productLicense);

		return dto;
	}

	public static class TrialExtensionDto {

		String regKey;
		String license;
		int duration;
		Date creationDate;
		Date expirationDate;
		ProductLicense productLicense;

		public String getRegKey() {
			return this.regKey;
		}

		public void setRegKey(String regKey) {
			this.regKey = regKey;
		}

		public String getLicense() {
			return this.license;
		}

		public void setLicense(String license) {
			this.license = license;
		}

		public int getDuration() {
			return this.duration;
		}

		public void setDuration(int duration) {
			this.duration = duration;
		}

		public Date getCreationDate() {
			return this.creationDate;
		}

		public void setCreationDate(Date creationDate) {
			this.creationDate = creationDate;
		}

		public Date getExpirationDate() {
			return this.expirationDate;
		}

		public void setExpirationDate(Date expirationDate) {
			this.expirationDate = expirationDate;
		}

		public ProductLicense getProductLicense() {
			return this.productLicense;
		}

		public void setProductLicense(ProductLicense productLicense) {
			this.productLicense = productLicense;
		}

	}
}
