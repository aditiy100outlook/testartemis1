package com.code42.auth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.backup42.app.cpc.clusterpeer.IMasterPeerController;
import com.backup42.common.perm.C42PermissionPro;
import com.backup42.history.CpcHistoryLogger;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.NotFoundException;
import com.code42.core.impl.DBCmd;
import com.code42.perm.PermissionUtils;
import com.code42.user.Role;
import com.code42.user.User;
import com.code42.user.UserFindByRoleQuery;
import com.code42.user.UserPublishUpdateCmd;
import com.code42.utils.LangUtils;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;
import com.google.inject.Inject;

public class RoleUpdateCmd extends DBCmd<RoleDto> {

	/* ================= Dependencies ================= */
	private IMasterPeerController master;

	/* ================= DI injection points ================= */
	@Inject
	public void setMaster(IMasterPeerController master) {
		this.master = master;
	}

	public enum Error {
		PERMISSIONS_MISSING, LOCKED_ROLE
	}

	private final Builder data;

	public RoleUpdateCmd(Builder role) {
		this.data = role;
	}

	@Override
	public RoleDto exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		Role role = null;

		role = this.runtime.run(new RoleFindByIdCmd(this.data.roleId), session);

		if (role == null || role.getRoleId() == null) {
			throw new NotFoundException("Unable to update role; key value is null or invalid: " + this.data.roleId);
		}

		if (role.isLocked()) {
			throw new CommandException(Error.LOCKED_ROLE, "Cannot update locked role.");
		}

		if (!(this.data.roleName instanceof None)) {
			role.setRoleName(this.data.roleName.get());
		}

		if (!(this.data.permissions instanceof None)) {
			ArrayList<IPermission> permissionList = new ArrayList<IPermission>();
			for (String permissionName : this.data.permissions.get()) {
				permissionList.add(PermissionUtils.getPermission(permissionName));
			}

			role.setPermissions(new HashSet<IPermission>(permissionList));
		}

		role = this.db.update(new RoleUpdateQuery(role));
		this.master.sendRoleChanged(role);

		List<User> users = this.db.find(new UserFindByRoleQuery(role.getRoleName()));
		// TODO: This could generate a LOT of commands and network traffic
		for (User user : users) {
			this.db.afterTransaction(new UserPublishUpdateCmd(user), session);
		}

		// LangUtils.toString(collection) is used below so we show all permissions.
		CpcHistoryLogger.info(session, "modified role: {}/{} permissions:{}", role.getRoleId(), role.getRoleName(),
				LangUtils.toString(role.getPermissions()));

		return new RoleDto(role, 0);
	}

	public static class Builder {

		public int roleId = 0;

		public Option<String> roleName = None.getInstance();
		public Option<List<String>> permissions = None.getInstance();

		public Builder(int roleId) {
			this.roleId = roleId;
		}

		public Builder name(String name) {
			if (LangUtils.hasValue(name)) {
				this.roleName = new Some<String>(name.trim());
			}
			return this;
		}

		public Builder permissions(List<String> permissions) {
			if (permissions != null) {
				this.permissions = new Some<List<String>>(permissions);
			}
			return this;
		}

		protected void validate() throws CommandException {
			if (this.permissions instanceof Some<?> && this.permissions.get().isEmpty()) {
				throw new CommandException(Error.PERMISSIONS_MISSING, "Missing Permissions.");
			}
		}

		public RoleUpdateCmd build() throws CommandException {
			this.validate();
			return new RoleUpdateCmd(this);
		}
	}
}
