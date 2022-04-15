package com.code42.org;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.auth.IPermission;
import com.code42.auth.RoleFindByNameCmd.RoleFindByNameQuery;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.user.Role;
import com.code42.utils.LangUtils;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Validate the default roles that will be applied to new non-admin users at registration time. If they are invalid,
 * throw a CommandException.
 * 
 * @author mharper
 */
public class OrgSettingsValidateCustomDefaultRolesCmd extends DBCmd<Void> {

	private static final Logger log = LoggerFactory.getLogger(OrgSettingsValidateCustomDefaultRolesCmd.class);

	public enum Errors {
		ROLE_NOT_FOUND, MISSING_REQUIRED_PERMISSION
	}

	/**
	 * Custom default roles must implement at least these permissions.
	 */
	private final static Collection<String> REQUIRED_PERMISSIONS = Lists.newArrayList( //
			C42PermissionPro.CPD.LOGIN.getValue(), //
			C42PermissionPro.CPS.LOGIN.getValue());

	private final int orgId;
	private final String defaultRoles; // comma-separated string

	public OrgSettingsValidateCustomDefaultRolesCmd(int orgId, String defaultRoles) {
		this.orgId = orgId;
		this.defaultRoles = defaultRoles;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		this.run(new IsOrgManageableCmd(this.orgId, C42PermissionApp.Org.UPDATE_BASIC), session);

		if (!LangUtils.hasValue(this.defaultRoles)) {
			// empty is ok - they are clearing the setting (resetting to default)
			log.info("Clearing custom default roles for org {}", this.orgId);
			return null; // short-circuit
		}

		// validate
		List<String> roles = Lists.newArrayList(Splitter.on(',').trimResults().split(this.defaultRoles));
		this.validateRoles(roles);

		// save
		log.info("Setting custom default roles for org {} - {}", this.orgId, this.defaultRoles);
		return null;
	}

	/**
	 * @throws CommandException if the list of roles contains a role that isn't in the db, or does not contain at least
	 *           the permissions to log in and back up.
	 */
	private void validateRoles(List<String> roles) throws CommandException {
		// build a set of all the permissions in the list of roles
		HashSet<String> allPerms = Sets.newHashSet();

		// validate that the role is in the DB, then add its permissions to the allPerms set
		for (String roleStr : roles) {
			final Role role = this.db.find(new RoleFindByNameQuery(roleStr));
			if (role == null) {
				throw new CommandException(Errors.ROLE_NOT_FOUND,
						"Invalid default role for new users, because it includes a role that's not found in the db ({})", roleStr);
			}
			for (IPermission perm : role.getPermissions()) {
				allPerms.add(perm.getValue());
			}
		}

		// validate that the required permissions are contained in the list of roles
		for (String perm : REQUIRED_PERMISSIONS) {
			if (!allPerms.contains(perm)) {
				throw new CommandException(Errors.MISSING_REQUIRED_PERMISSION,
						"Invalid default role for new users, because it is missing a required permission ({})", perm);
			}
		}
	}

}
