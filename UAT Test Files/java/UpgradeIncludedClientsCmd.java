package com.code42.client;

import java.util.Properties;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;

/**
 * Determine, from the upgrade properties file, whether or not clients were included in this upgrade. <br/>
 * The properties file should include a property <code>includeClient=(true|false)</code>
 */
public class UpgradeIncludedClientsCmd extends AbstractCmd<Boolean> {

	static final String INCLUDE_CLIENT_KEY = "includeClient";

	private final Properties upgradeProperties;

	public UpgradeIncludedClientsCmd(final Properties upgradeProperties) {
		super();
		this.upgradeProperties = upgradeProperties;
	}

	@Override
	public Boolean exec(CoreSession session) throws CommandException {
		final Object value = this.upgradeProperties.get(INCLUDE_CLIENT_KEY);

		return value != null && Boolean.parseBoolean(value.toString());
	}

}
