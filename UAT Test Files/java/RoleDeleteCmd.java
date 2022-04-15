package com.code42.auth;

import java.util.List;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import com.backup42.app.cpc.clusterpeer.IMasterPeerController;
import com.backup42.common.perm.C42PermissionPro;
import com.backup42.history.CpcHistoryLogger;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.DeleteQuery;
import com.code42.core.impl.DBCmd;
import com.code42.exception.DebugRuntimeException;
import com.code42.hibernate.aftertx.AfterTxRunnable;
import com.code42.hibernate.aftertx.IAfterTxRunnable.Priority;
import com.code42.user.Role;
import com.code42.user.UserRole;
import com.code42.user.UserRoleFindByRoleQuery;
import com.google.inject.Inject;

public class RoleDeleteCmd extends DBCmd<Void> {

	/* ================= Dependencies ================= */
	private IMasterPeerController master;

	/* ================= DI injection points ================= */
	@Inject
	public void setMaster(IMasterPeerController master) {
		this.master = master;
	}

	public enum Error {
		LOCKED_ROLE, HAS_USERS
	}

	private Role role;
	private int roleId;

	public RoleDeleteCmd(Role role) {
		this.role = role;
	}

	public RoleDeleteCmd(int roleId) {
		this.roleId = roleId;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);
		try {
			if (this.role == null) {
				this.role = this.runtime.run(new RoleFindByIdCmd(this.roleId), session);
			}
			if (this.roleId == 0) {
				this.roleId = this.role.getRoleId();
			}

			if (this.role.isLocked()) {
				throw new CommandException(Error.LOCKED_ROLE, "Cannot delete a locked role");
			}

			if (this.role != null && this.roleId != 0) {
				if (this.role.getRoleId() != null) {

					try {
						this.db.beginTransaction();
						List<UserRole> userRoles = this.db.find(new UserRoleFindByRoleQuery(this.role));
						if (userRoles.size() > 0) {
							throw new CommandException(Error.HAS_USERS, "Cannot delete a role with users");
						}

						this.db.delete(new RoleDeleteQuery(this.role));
						this.db.afterTransaction(new AfterTxRunnable(Priority.HIGH) {

							public void run() {
								RoleDeleteCmd.this.master.sendRoleDeleted(RoleDeleteCmd.this.role);
							}
						});

						this.db.commit();

						CpcHistoryLogger.info(session, "deleted role {}", this.role.getRoleName());
					} catch (Throwable t) {
						this.db.rollback();
						throw new DebugRuntimeException(t.getMessage(), t);
					} finally {
						this.db.endTransaction();
					}
				}
			} else {
				throw new CommandException("Error Deleting Role; null argument: ");
			}
		} catch (HibernateException e) {
			throw new CommandException("Error Deleting Role; role=" + this.role);
		}
		return null;
	}

	private static class RoleDeleteQuery extends DeleteQuery<Role> {

		private Role role;

		public RoleDeleteQuery(Role role) {
			this.role = role;
		}

		@Override
		public void query(Session session) throws DBServiceException {
			try {
				session.delete(this.role);
			} catch (HibernateException e) {
				throw new DBServiceException("Error Deleting Role; role=" + this.role);
			}
		}
	}
}
