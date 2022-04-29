package com.code42.activation;

import java.util.Properties;

import com.backup42.common.ComputerType;
import com.code42.backup.DataKey;
import com.code42.backup.SecureDataKey;
import com.code42.computer.Computer;
import com.code42.crypto.MD5Value;
import com.code42.crypto.X509PublicKey;
import com.google.common.annotations.VisibleForTesting;

/**
 * Describe the remote device. These details are request-agnostic.
 * 
 */
public class DeviceDetailPacket {

	private String address; // this.msg.getAddress()
	private String remoteAddress; // this.msg.getSession().getRemoteLocation().getAddress()
	private int port; // this.msg.getPort()
	private String computerName;
	private ComputerType computerType;
	private String productVersion;

	private Properties properties;
	private Long authDate;

	private DataKey dataKey;
	private SecureDataKey secureDataKey;
	private MD5Value dataKeyChecksum;
	private X509PublicKey transportPublicKey;

	public DeviceDetailPacket() {
		super();
	}

	/**
	 * Copies the basic fields for testing. Caller can set the specifics on their own.
	 */
	@VisibleForTesting
	DeviceDetailPacket(Computer c) {
		this();
		this.address = c.getAddress();
		this.remoteAddress = c.getRemoteAddress();
		this.port = 4242; // testing default
		this.computerName = c.getName();
		this.computerType = c.getType();
		this.productVersion = c.getProductVersion();
	}

	public String getAddress() {
		return this.address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getRemoteAddress() {
		return this.remoteAddress;
	}

	public void setRemoteAddress(String remoteAddress) {
		this.remoteAddress = remoteAddress;
	}

	public int getPort() {
		return this.port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getComputerName() {
		return this.computerName;
	}

	public void setComputerName(String computerName) {
		this.computerName = computerName;
	}

	public ComputerType getComputerType() {
		return this.computerType;
	}

	public void setComputerType(ComputerType computerType) {
		this.computerType = computerType;
	}

	public String getProductVersion() {
		return this.productVersion;
	}

	public void setProductVersion(String productVersion) {
		this.productVersion = productVersion;
	}

	public Properties getProperties() {
		return this.properties;
	}

	public void setProperties(Properties properties) {
		this.properties = properties;
	}

	public Long getAuthDate() {
		return this.authDate;
	}

	public void setAuthDate(Long authDate) {
		this.authDate = authDate;
	}

	public DataKey getDataKey() {
		return this.dataKey;
	}

	public void setDataKey(DataKey dataKey) {
		this.dataKey = dataKey;
	}

	public SecureDataKey getSecureDataKey() {
		return this.secureDataKey;
	}

	public void setSecureDataKey(SecureDataKey secureDataKey) {
		this.secureDataKey = secureDataKey;
	}

	public MD5Value getDataKeyChecksum() {
		return this.dataKeyChecksum;
	}

	public void setDataKeyChecksum(MD5Value dataKeyChecksum) {
		this.dataKeyChecksum = dataKeyChecksum;
	}

	public X509PublicKey getTransportPublicKey() {
		return this.transportPublicKey;
	}

	public void setTransportPublicKey(X509PublicKey transportPublicKey) {
		this.transportPublicKey = transportPublicKey;
	}

	/**
	 * CUSTOMIZED! Security keys are excluded from the output.
	 */
	@Override
	public String toString() {
		return "DeviceDetailPacket [address=" + this.address + ", remoteAddress=" + this.remoteAddress + ", port="
				+ this.port + ", computerName=" + this.computerName + ", computerType=" + this.computerType
				+ ", productVersion=" + this.productVersion + ", properties=" + this.properties + ", authDate=" + this.authDate
				+ "]";
	}
}
