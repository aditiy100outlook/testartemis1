package com.code42.alert;

import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.alert.ISystemAlertService;
import com.code42.core.alert.SystemAlert;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.google.inject.Inject;

/**
 * Finds and returns the alerts from the SystemAlertService
 * 
 * @author <a href="mailto:jon@code42.com">Jon Carlson</a>
 */
public class SystemAlertFindLocalCmd extends DBCmd<List<SystemAlert>> {

	@Inject
	private ISystemAlertService alertSvc;

	@Override
	public List<SystemAlert> exec(CoreSession session) throws CommandException {
		return this.alertSvc.getAll();
	}

}
