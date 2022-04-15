package com.code42.license;

import java.util.List;

import org.hibernate.Query;
import org.hibernate.Session;

import com.code42.core.CommandException;
import com.code42.core.annotation.CoreNamedQuery;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;

/**
 * Migration of com.backup42.computer.data.ext.ComputerLicenseDataProvider.findByComputer(final long computerId) to
 * command structure.
 * 
 * @author bmcguire
 */
public class ComputerLicenseFindByComputerIdCmd extends DBCmd<List<ComputerLicense>> {

	private long computerId;

	public ComputerLicenseFindByComputerIdCmd(long computerId) {

		this.computerId = computerId;
	}

	@Override
	public List<ComputerLicense> exec(CoreSession session) throws CommandException {

		/*
		 * TODO: Permissions for commands generated from the data providers is a fairly ambiguous topic. Checking for
		 * computer-read here seems reasonable but this could be entirely wrong.
		 */
		this.runtime.run(new IsComputerManageableCmd(this.computerId, C42PermissionApp.Computer.READ), session);

		try {

			return this.db.find(new ComputerLicenseFindByComputerIdQuery());
		} catch (DBServiceException dbe) {

			/* Old data provider threw RuntimeExceptions is any error occurred... so we do the same. */
			throw new RuntimeException("Failed to findByComputer with computerId=" + this.computerId, dbe.getCause());
		}
	}

	@CoreNamedQuery(name = "findComputerLicensesByComputer", query = "from ComputerLicense as cl where cl.computerId = :computerId")
	private class ComputerLicenseFindByComputerIdQuery extends FindQuery<List<ComputerLicense>> {

		@Override
		public List<ComputerLicense> query(Session session) throws DBServiceException {

			try {

				Query q = this.getNamedQuery(session);
				q.setLong("computerId", ComputerLicenseFindByComputerIdCmd.this.computerId);
				return q.list();
			} catch (Exception e) {

				throw new DBServiceException(e);
			}
		}
	}
}
