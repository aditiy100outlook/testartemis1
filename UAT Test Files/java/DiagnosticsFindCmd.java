package com.code42.diagnostics;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.alert.Diagnostics;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;

/**
 * Provides access to the Diagnostics instance
 * 
 * @author <a href="mailto:jon@code42.com">Jon Carlson</a>
 */
public class DiagnosticsFindCmd extends AbstractCmd<Diagnostics> {

	@Override
	public Diagnostics exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		return Diagnostics.getInstance();
	}

}
