package com.code42.radius;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.code42.core.radius.RADIUSException;
import com.code42.encryption.EncryptionServices;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.user.RadiusServer;
import com.code42.utils.LangUtils;
import com.code42.utils.Pair;
import com.google.common.collect.Sets;

public class RadiusServerDto {

	private static final Logger log = LoggerFactory.getLogger(RadiusServerDto.class);

	private RadiusServer radiusServer;

	public RadiusServerDto(RadiusServer radiusServer) {
		super();
		this.radiusServer = radiusServer;
	}

	public Integer getRadiusServerId() {
		return this.radiusServer.getRadiusServerId();
	}

	public String getRadiusServerName() {
		return this.radiusServer.getRadiusServerName();
	}

	public String getAddress() {
		return this.radiusServer.getAddress();
	}

	public String getSharedSecret() {
		String encrypted = this.radiusServer.getSharedSecret();
		String decrypted = null;
		if (LangUtils.hasValue(encrypted)) {
			try {
				decrypted = EncryptionServices.getCrypto().decrypt(encrypted);
			} catch (Throwable t) {
				log.error("RADIUS:: Error decrypting RADIUS shared password", t);
				decrypted = "";
			}
		}
		return decrypted;
	}

	public Collection<RadiusAttribute> getAttributes() {
		Pair<Set<RadiusAttribute>, List<String>> pair = parseAttributes(this.radiusServer.getAttributes());
		Set<RadiusAttribute> attrs = pair.getOne();
		// List<String> errors = pair.getTwo(); // Not used here
		return attrs;
	}

	public String getAttributeString() {
		return this.radiusServer.getAttributes();
	}

	public int getTimeoutSeconds() {
		return this.radiusServer.getTimeoutSeconds();
	}

	public boolean isNoRadius() {
		return this.radiusServer.isNoRadius();
	}

	public Date getCreationDate() {
		return this.radiusServer.getCreationDate();
	}

	public Date getModificationDate() {
		return this.radiusServer.getModificationDate();
	}

	/**
	 * Parses an attributes string into RadiusAttribute objects.
	 * 
	 * @param attributeStrings
	 * @return a set of RadiusAttributes and a list of lines that were in error
	 */
	public static Pair<Set<RadiusAttribute>, List<String>> parseAttributes(String attributeStrings) {
		Set<RadiusAttribute> attributes = Sets.newHashSet();
		List<String> errorLines = new ArrayList<String>();

		// Parse the attribute strings
		if (LangUtils.hasValue(attributeStrings)) {
			String[] lines = attributeStrings.split("\\s*\n\\s*");
			for (String line : lines) {
				String[] nameValue = line.split("\\s*=\\s*");
				if (nameValue.length == 0 || !LangUtils.hasValue(nameValue[0])) {
					System.out.println("Ignoring");
					// Ignore blank lines
				} else if (nameValue.length == 2) {
					try {
						attributes.add(new RadiusAttribute(nameValue[0].trim(), nameValue[1].trim()));
					} catch (RADIUSException e) {
						errorLines.add(line);
						// Log and ignore this attribute problem... Bad data must have gotten into the database
						log.warn("Invalid RadiusAttribute: {}={}", nameValue[0], nameValue[1]);
					}
				} else {
					errorLines.add(line);
				}
			}
		}
		return new Pair(attributes, errorLines);
	}

	public static String stringifyAttributes(Collection<RadiusAttribute> attrs) {
		StringBuilder str = new StringBuilder();
		for (RadiusAttribute attr : attrs) {
			attr.appendTo(str);
		}
		return str.toString();
	}
}
