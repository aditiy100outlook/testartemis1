package com.code42.user;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.backup42.common.OrgType;
import com.backup42.role.ConsumerUserRole;
import com.backup42.role.PROeUserRole;
import com.backup42.role.ProOnlineUserRole;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.org.Org;
import com.code42.org.OrgSettingsInfo;
import com.code42.org.OrgSettingsInfoFindByOrgCmd;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Helper command to assign roles to new (regular) users. Does not support role assignment for admin users.
 */
public class UserRoleAssignDefaultsCmd extends DBCmd<Void> {

	public static final String USER_DEFAULT_ROLES = "user_default_roles";

	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(UserRoleAssignDefaultsCmd.class);

	@Inject
	@Named(USER_DEFAULT_ROLES)
	private Set<String> defaultUserRoleNames;

	private final User user;
	private final Org org;

	public UserRoleAssignDefaultsCmd(User user, Org org) {
		super();
		this.user = user;
		this.org = org;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		this.run(new IsUserManageableCmd(this.user, C42PermissionApp.User.UPDATE), session);

		if (OrgType.CONSUMER == this.org.getType()) {
			this.assignRoles(session, this.addDefaultRoles(ConsumerUserRole.ROLE_NAME));
		} else if (OrgType.BUSINESS == this.org.getType()) {
			this.assignRoles(session, this.addDefaultRoles(ProOnlineUserRole.ROLE_NAME));
		} else {
			// enterprise - check to see if there's a custom default set in t_org_settings
			OrgSettingsInfo osi = this.run(new OrgSettingsInfoFindByOrgCmd.Builder().org(this.org).build(), session);
			List<String> defaultRoles = osi.getDefaultRolesAsList();
			if (defaultRoles.isEmpty()) {
				// no custom default - assign the default set of roles
				this.assignRoles(session, this.addDefaultRoles(PROeUserRole.ROLE_NAME));
			} else {
				// there is a custom default
				this.assignRoles(session, defaultRoles);
			}
		}
		return null;
	}

	/**
	 * Ensure that the roles (currently) contain at least the permissions required to log in and back up. If valid, assign
	 * those roles to the user.
	 * 
	 * @throws CommandException if a passed-in role isn't in the db.
	 */
	private void assignRoles(CoreSession session, List<String> roles) throws CommandException {
		for (String role : roles) {
			final UserRoleCreateCmd.Builder builder = new UserRoleCreateCmd.Builder(this.user, role);
			this.runtime.run(builder.build(), session);
		}
	}

	private List<String> addDefaultRoles(String... roles) {
		final List<String> allRoles = new ArrayList<String>(roles.length + this.defaultUserRoleNames.size());
		for (String r : roles) {
			allRoles.add(r);
		}
		allRoles.addAll(this.defaultUserRoleNames);
		return allRoles;
	}
}
