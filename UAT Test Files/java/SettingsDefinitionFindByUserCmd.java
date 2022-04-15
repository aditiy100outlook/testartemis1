package com.code42.setting;

import java.util.List;
import java.util.Set;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.hierarchy.IHierarchyService;
import com.code42.core.impl.DBCmd;
import com.code42.utils.Pair;
import com.google.inject.Inject;

/**
 * Calculate a SettingsDefinition that exists for the User. In this case, any device-specific settings will be left out.
 * Only the org/user settings will be provided.
 * 
 */
public class SettingsDefinitionFindByUserCmd extends DBCmd<SettingsDefinition> {

	private final int userId;
	private final Set<String> keys;
	@Inject
	private IHierarchyService hierarchyService;

	/**
	 * Pull a settings defintion for the user (and higher) hierarchy.
	 * 
	 * @param userId
	 */
	public SettingsDefinitionFindByUserCmd(int userId) {
		this(userId, null);
	}

	/**
	 * Pull a settings definition for the user/org space that consists only of the provided keys.
	 * 
	 * @param userId
	 * @param keys
	 */
	public SettingsDefinitionFindByUserCmd(int userId, Set<String> keys) {
		super();
		this.userId = userId;
		this.keys = keys;
	}

	@Override
	public SettingsDefinition exec(CoreSession session) throws CommandException {

		this.run(new IsUserManageableCmd(this.userId, C42PermissionApp.User.READ), session);

		SettingsDefinition definition = null;
		try {
			final Pair<Integer, Integer> userAndOrg = this.hierarchyService.getHierarchyByUserID(this.userId);
			final List<Integer> parentOrgsAscending = this.hierarchyService.getAscendingOrgs(userAndOrg.getOne());

			final List<CoreSetting> userSettings = this.db.find(new SettingsFindAllByUserQuery(parentOrgsAscending,
					this.userId, this.keys));

			definition = SettingsDefinitionFactory.build(userSettings, parentOrgsAscending);
		} catch (Exception e) {
			throw new CommandException("Failed to build a user settings definition", e);
		}

		return definition;
	}
}
