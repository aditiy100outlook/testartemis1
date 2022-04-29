package com.code42.alert;

import java.util.List;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.alert.SystemAlert;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.core.space.ISpaceService;
import com.code42.core.space.SpaceException;
import com.code42.space.SpaceKeyUtils;
import com.google.inject.Inject;

/**
 * Finds and returns the system alerts stored in space
 * 
 * @author <a href="mailto:jon@code42.com">Jon Carlson</a>
 */
public class SystemAlertFindAllCmd extends DBCmd<List<SystemAlert>> {

	@Inject
	private ISpaceService space;

	@Override
	public List<SystemAlert> exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		try {

			String spaceKey = SpaceKeyUtils.getSystemAlertsKey();
			return (List<SystemAlert>) this.space.get(spaceKey);
		} catch (SpaceException se) {

			throw new CommandException("Exception performing space operations", se);
		}
	}

}
