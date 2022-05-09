package com.code42.auth;

import java.util.List;

import org.hibernate.Session;

import com.backup42.app.cpc.clusterpeer.IMasterPeerController;
import com.backup42.common.perm.C42PermissionPro;
import com.backup42.history.CpcHistoryLogger;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.CreateQuery;
import com.code42.core.impl.DBCmd;
import com.code42.perm.PermissionUtils;
import com.code42.user.Role;
import com.code42.utils.LangUtils;
import com.google.inject.Inject;

public class RoleCreateCmd extends DBCmd<RoleDto> {

	/* ================= Dependencies ================= */
	private IMasterPeerController master;

	/* ================= DI injection points ================= */
	@Inject
	public void setMaster(IMasterPeerController master) {
		this.master = master;
	}

	public enum Error {
		DUPLICATE_NAME, ROLENAME_MISSING, PERMISSIONS_MISSING
	}

	private final Builder data;

	public RoleCreateCmd(Builder role) {
		this.data = role;
	}

	@Override
	public RoleDto exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);
		if (this.runtime.run(new RoleIsDuplicateCmd(this.data.roleName), session)) {
			throw new CommandException(Error.DUPLICATE_NAME, "Role by that name already exists.");
		}

		Role role = this.data.populateRole(new Role());
		RoleDto newRole = this.db.create(new RoleCreateQuery(role));
		this.master.sendRoleChanged(role);

		CpcHistoryLogger.info(session, "created role {} with permissions:{}", newRole.getRoleName(), LangUtils
				.toString(this.data.permissions));
		return newRole;
	}

	private static class RoleCreateQuery extends CreateQuery<RoleDto> {

		private Role role;

		public RoleCreateQuery(Role role) {
			this.role = role;
		}

		@Override
		public RoleDto query(Session session) throws DBServiceException {
			session.save(this.role);
			return new RoleDto(this.role, 0);
		}
	}

	public static class Builder {

		// Required fields
		protected String roleName;
		protected List<String> permissions;

		public Builder(String name, List<String> permissions) {
			this.name(name).permissions(permissions);
		}

		public Builder name(String name) {
			if (LangUtils.hasValue(name)) {
				this.roleName = name.trim();
			}
			return this;
		}

		public Builder permissions(List<String> permissions) {
			if (permissions != null) {
				this.permissions = permissions;
			}
			return this;
		}

		public Role populateRole(Role role) {
			role.setRoleName(this.roleName);
			for (String permissionName : this.permissions) {
				role.addPermission(PermissionUtils.getPermission(permissionName));
			}
			role.setLocked(false);

			return role;
		}

		protected void validate() throws CommandException {
			if (!LangUtils.hasValue(this.roleName)) {
				throw new CommandException(Error.ROLENAME_MISSING, "Missing Role Name.");
			}

			if (this.permissions == null || this.permissions.isEmpty()) {
				throw new CommandException(Error.PERMISSIONS_MISSING, "Missing Permissions.");
			}
		}

		public RoleCreateCmd build() throws CommandException {
			this.validate();
			return new RoleCreateCmd(this);
		}
	}
}
