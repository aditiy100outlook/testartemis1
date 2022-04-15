package com.code42.radius;

import org.tinyradius.attribute.IpAttribute;

import com.code42.core.radius.RADIUSException;

public class RadiusAttribute {

	private RadiusAttributeType type;
	private String value;

	public RadiusAttribute(String name, String value) throws RADIUSException {

		for (RadiusAttributeType type : RadiusAttributeType.values()) {
			if (type.getRadiusName().equalsIgnoreCase(name)) {
				this.type = type;
			}
		}

		if (this.type == null) {
			throw new RADIUSException("Unknown Attribute type: {}", name);
		}

		this.value = value;
	}

	public org.tinyradius.attribute.RadiusAttribute getTinyRadiusAttribute() throws RADIUSException {
		if (this.type.isIntegerType()) {
			try {
				int value = Integer.valueOf(this.value);
				return new org.tinyradius.attribute.IntegerAttribute(this.type.getRadiusId(), value);
			} catch (NumberFormatException nfe) {
				throw new RADIUSException("Value is not an integer: {}" + this.value);
			}

		} else if (this.type.isIpType()) {
			// IP address type
			return new IpAttribute(this.type.getRadiusId(), this.value);

		} else if (this.type.isStringType()) {
			return new org.tinyradius.attribute.StringAttribute(this.type.getRadiusId(), this.value);

		} else {
			throw new RADIUSException("Unable to handle this RADIUS attribute type: {}", this.type);
		}
	}

	public RadiusAttributeType getType() {
		return this.type;
	}

	public String getName() {
		return this.type.getRadiusName();
	}

	public void appendTo(StringBuilder str) {
		str.append(this.type.getRadiusName());
		str.append("=");
		str.append(this.value);
		str.append("\n");
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + "[type:" + this.type + ", value:" + this.value + "]";
	}

}
