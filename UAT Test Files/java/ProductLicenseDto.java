package com.code42.license;

import java.util.Date;

import com.code42.product.Product;
import com.code42.server.license.ProductLicense;

public class ProductLicenseDto {

	private ProductLicense productLicense;
	private Product product;

	public ProductLicenseDto(ProductLicense productLicense, Product product) {
		super();
		this.productLicense = productLicense;
		this.product = product;
	}

	public int getId() {
		return this.productLicense.getProductLicenseId();
	}

	public Integer getProductID() {
		return this.productLicense.getProductId();
	}

	public String getProductName() {
		return this.product.getName();
	}

	public Integer getProductQuantity() {
		return this.productLicense.getProductQuantity();
	}

	public Integer getDuration() {
		return this.productLicense.getDuration();
	}

	public Date getExpirationDate() {
		return this.productLicense.getExpirationDate();
	}

	public Date getCreationDate() {
		return this.productLicense.getCreationDate();
	}

	public Integer getQuantity() {
		return this.productLicense.getQuantity();
	}

	public boolean isActive() {
		return this.productLicense.isActive();
	}

	public boolean isValid() {
		return this.productLicense.isValid();
	}

	public String getProductType() {
		return this.product.getType();
	}

	public String getProductLicenseKey() {
		return this.productLicense.getLicense();
	}

	@Override
	public String toString() {
		return "ProductLicenseDto [productLicense=" + this.productLicense + ", product=" + this.product + "]";
	}
}
