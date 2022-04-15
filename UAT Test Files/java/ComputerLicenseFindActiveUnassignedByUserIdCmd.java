package com.code42.license;

import java.util.List;

import org.hibernate.Query;
import org.hibernate.Session;

import com.code42.core.CommandException;
import com.code42.core.annotation.CoreNamedQuery;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsUserManageableCmd;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;

/**
 * Migration of com.backup42.computer.data.ext.ComputerLicenseDataProvider.findActiveUnassignedByUser(final int userId)
 * to command structure.
 * 
 * @author bmcguire
 */
public class ComputerLicenseFindActiveUnassignedByUserIdCmd extends DBCmd<List<ComputerLicense>> {

	private int userId;

	public ComputerLicenseFindActiveUnassignedByUserIdCmd(int userId) {

		this.userId = userId;
	}

	@Override
	public List<ComputerLicense> exec(CoreSession session) throws CommandException {

		/*
		 * TODO: Permissions for commands generated from the data providers is a fairly ambiguous topic. Checking for
		 * user-read here seems reasonable but this could be entirely wrong.
		 */
		this.runtime.run(new IsUserManageableCmd(this.userId, C42PermissionApp.User.READ), session);

		/*
		 * This query _shouldn't_ ever result in a checked DBServiceException but do "the right thing" if it does. Runtime
		 * exceptions aren't handled since we want those to filter back to the caller (in order to be consistent with the
		 * existing data provider).
		 * 
		 * "the right thing" here basically means "something consistent with the existing API".
		 * 
		 * Note that this try/catch infrastructure isn't necessary for type checking to be successful; DBServiceException is
		 * (as of this writing) a CommandException so the types will work out. That probably won't last, however, since
		 * DBServiceException has absolutely no business being a CommandException. In order to guard against that expected
		 * change we include a specific handler for the checked exception.
		 */
		try {

			return this.db.find(new ComputerLicenseFindActiveUnassignedByUserIdQuery());
		} catch (DBServiceException dbe) {

			throw new RuntimeException("Unexpected DBServiceException", dbe);
		}
	}

	@CoreNamedQuery(name = "findActiveUnassignedComputerLicenseByUser", query = "from ComputerLicense as cl where cl.userId = :userId and cl.active = true and cl.computerId is null")
	private class ComputerLicenseFindActiveUnassignedByUserIdQuery extends FindQuery<List<ComputerLicense>> {

		@Override
		public List<ComputerLicense> query(Session session) throws DBServiceException {

			/*
			 * The original provider didn't have any try/catch infrastructure so we won't either. This code throws no checked
			 * exceptions so runtime stuff is our only concern... and we have no reason to do anything other than allow
			 * runtime exceptions to percolate up to (and presumably out of) the command.
			 */
			Query q = this.getNamedQuery(session);
			q.setInteger("userId", ComputerLicenseFindActiveUnassignedByUserIdCmd.this.userId);
			return q.list();
		}
	}
}
