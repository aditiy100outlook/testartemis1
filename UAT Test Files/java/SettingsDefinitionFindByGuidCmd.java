package com.code42.setting;

import com.code42.computer.ComputerSso;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.businessobjects.BusinessObjectsException;
import com.code42.core.businessobjects.IBusinessObjectsService;
import com.code42.core.impl.DBCmd;
import com.google.inject.Inject;

/**
 * Find the full SettingsDefinition for a given guid.
 * 
 */
public class SettingsDefinitionFindByGuidCmd extends DBCmd<SettingsDefinition> {

	private final long guid;
	@Inject
	private IBusinessObjectsService bos;

	public SettingsDefinitionFindByGuidCmd(long guid) {
		super();
		this.guid = guid;
	}

	@Override
	public SettingsDefinition exec(CoreSession session) throws CommandException {

		if (!this.env.isMaster()) {
			throw new CommandException("Settings may only be retrieved on an authority node");
		}

		final ComputerSso cSso;
		try {
			cSso = this.bos.getComputerByGuid(this.guid);
		} catch (BusinessObjectsException e) {
			throw new CommandException("Failed to identify a computer from the source guid", this.guid);
		}
		this.run(new IsComputerManageableCmd(cSso.getComputerId(), C42PermissionApp.Computer.READ), session);

		final SettingsDefinition settingsDefinition = this.run(
				new SettingsDefinitionFindByComputerCmd(cSso.getComputerId()), session);
		return settingsDefinition;
	}
}
