/*
 * Created on Dec 3, 2010 by Tony Lindquist <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.computer;

import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.impl.DBCmd;

/**
 * Command to find a list of computers for a user. An optional parameter for 'active' is also included.
 */
public class ComputerFindByOrgCmd extends DBCmd<List<Computer>> {

	private int orgId;
	private Boolean active;
	private Boolean blocked;
	private int offset;
	private int limit;

	/**
	 * Finds all computers in the given org (including deactivated and blocked).
	 * 
	 * @param orgId
	 */
	public ComputerFindByOrgCmd(int orgId) {
		this(orgId, null/* active */, null/* blocked */);
	}

	/**
	 * Finds computers for the given organization.
	 * 
	 * @param orgId - Returns only the computers for users in this org
	 * @param active - Optional. If null, no active flag filtering is done
	 * @param blocked - Optional. If null, no blocked flag filtering is done
	 */
	public ComputerFindByOrgCmd(int orgId, Boolean active, Boolean blocked) {
		this(orgId, active, blocked, 0/* offset */, 0/* limit */);
	}

	/**
	 * Finds computers for the given organization.
	 * 
	 * @param orgId - Returns only the computers for users in this org
	 * @param active - Optional. If null, no active flag filtering is done
	 * @param blocked - Optional. If null, no blocked flag filtering is done
	 * @param includeChildOrgs -
	 */
	public ComputerFindByOrgCmd(int orgId, Boolean active, Boolean blocked, int offset, int limit) {
		this.orgId = orgId;
		this.active = active;
		this.offset = offset;
		this.limit = limit;
	}

	@Override
	public List<Computer> exec(CoreSession session) throws CommandException {
		this.runtime.run(new IsOrgManageableCmd(this.orgId, C42PermissionApp.Org.READ), session);

		// Find the computers and return them
		ComputerFindByOrgQuery query = new ComputerFindByOrgQuery(this.orgId, this.active, this.blocked, this.offset,
				this.limit);
		List<Computer> list = this.db.find(query);

		return list;
	}

}
