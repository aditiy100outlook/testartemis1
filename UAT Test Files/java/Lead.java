/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.lead;


public class Lead {

	private String email;
	private String referrer;
	private String source;

	protected Lead(LeadCreateCmd.Builder builder) {

		this.email = builder.email;
		this.referrer = builder.referrer;
		this.source = builder.source;
	}

	public String getEmail() {
		return this.email;
	}

	public String getReferrer() {
		return this.referrer;
	}

	public String getSource() {
		return this.source;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Lead [email=");
		builder.append(this.email);
		builder.append(", referrer=");
		builder.append(this.referrer);
		builder.append(", source=");
		builder.append(this.source);
		builder.append("]");
		return builder.toString();
	}
}
