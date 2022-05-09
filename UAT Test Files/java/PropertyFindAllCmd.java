/**
 * <a href="http://www.code42.com">(c)Code 42 Software, Inc.</a> $Id: $
 */
package com.code42.property;

import java.util.List;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

/**
 * @author <a href="mailto:brian@code42.com">Brian Bispala </a>
 */
public class PropertyFindAllCmd extends DBCmd<List<Property>> {

	public PropertyFindAllCmd() {
		super();
	}

	@Override
	public List<Property> exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		final int myClusterId = this.env.getMyClusterId();
		List<Property> all = this.db.find(new PropertyFindAllQuery(myClusterId));
		return all;
	}

}
