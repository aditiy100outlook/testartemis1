package com.code42.radius;

/**
 * Specifies a data type for
 */
public enum RadiusAttributeType {

	NASIPAddress("NAS-IP-Address", 4, DataType.IP), //
	NASPort("NAS-Port", 5, DataType.INTEGER), //
	NASPortType("NAS-Port-Type", 61, DataType.INTEGER), //
	ServiceType("Service-Type", 6, DataType.INTEGER), //
	NASIdentifier("NAS-Identifier", 32, DataType.STRING);

	/**
	 * @param radiusId - a radius attribute identifier
	 * @param type - a data type (integer or string) identifier
	 */
	private RadiusAttributeType(String radiusName, int radiusId, DataType type) {
		this.radiusName = radiusName;
		this.radiusId = radiusId;
		this.type = type; // String, Integer, etc
	}

	public enum DataType {
		INTEGER, STRING, IP
	}

	private final String radiusName;
	private final int radiusId;
	private final DataType type;

	@Override
	public String toString() {
		return this.name() + "[radiusName:" + this.radiusName + ", id:" + this.radiusId + ", type:" + this.type.name()
				+ "]";
	}

	public String getRadiusName() {
		return this.radiusName;
	}

	public int getRadiusId() {
		return this.radiusId;
	}

	public DataType getType() {
		return this.type;
	}

	public boolean isIntegerType() {
		return this.type == DataType.INTEGER;
	}

	public boolean isStringType() {
		return this.type == DataType.STRING;
	}

	public boolean isIpType() {
		return this.type == DataType.IP;
	}
}
