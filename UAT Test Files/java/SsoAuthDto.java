/**
 * 
 */
package com.code42.ssoauth;

import java.util.Date;

/**
 * Generic data object containing all the info needed to authenticate a person with Single Sign-On.
 */
public class SsoAuthDto {

	SsoAuth ssoAuth = null;

	public SsoAuthDto(SsoAuth server) {
		this.ssoAuth = server;
	}

	public int getSsoAuthId() {
		return this.ssoAuth.getSsoAuthId();
	}

	public String getSsoAuthName() {
		return this.ssoAuth.getSsoAuthName();
	}

	public String getIdentityProviderMetadata() {
		return this.ssoAuth.getIdentityProviderMetadata();
	}

	public String getIdentityProviderMetadataUrl() {
		return this.ssoAuth.getIdentityProviderMetadataUrl();
	}

	public boolean isEnabled() {
		return this.ssoAuth.isEnabled();
	}

	public Date getCreationDate() {
		return this.ssoAuth.getCreationDate();
	}

	public Date getModificationDate() {
		return this.ssoAuth.getModificationDate();
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append(this.getClass().getSimpleName()).append("[");
		str.append("id:").append(this.getSsoAuthId());
		str.append(", name:").append(this.getSsoAuthName());
		str.append(", enabled:").append(this.isEnabled());
		str.append(", idpMetadata.length()=").append(
				this.getIdentityProviderMetadata() == null ? 0 : this.getIdentityProviderMetadata().length());
		str.append("]");
		return str.toString();
	}
}