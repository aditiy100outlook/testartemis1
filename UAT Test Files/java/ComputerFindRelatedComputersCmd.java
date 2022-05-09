package com.code42.computer;

import java.util.ArrayList;
import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.user.User;
import com.code42.user.UserFindByGuidCmd;

/**
 * Find all active *related* Computers WITHOUT attached licences for the given computer (i.e. all other computers tied
 * to the user) assigned to the same server.
 * 
 * @param ownerGuid the uid of a computer
 * @return the related computers if any
 */
public class ComputerFindRelatedComputersCmd extends DBCmd<List<Computer>> {

	private static final Logger log = LoggerFactory.getLogger(ComputerFindRelatedComputersCmd.class);

	private final long guid;
	private final boolean includeSelf;

	public ComputerFindRelatedComputersCmd(long guid, boolean includeSelf) {
		this.guid = guid;
		this.includeSelf = includeSelf;
	}

	@Override
	public List<Computer> exec(CoreSession session) throws CommandException {
		final List<Computer> relatedComputers = new ArrayList<Computer>();
		final User user = this.run(new UserFindByGuidCmd(this.guid), session);
		if (user != null) {
			final List<Computer> computers = this.run(new ComputerFindByUserCmd(user.getUserId().intValue()), session);
			for (final Computer computer : computers) {
				// if not including source then don't include this computer
				if (this.includeSelf || this.guid != computer.getGuid()) {
					// add to the list
					relatedComputers.add(computer);
				}
			}
		} else {
			log.warn("User NOT FOUND from computer uid=" + this.guid);
		}
		return relatedComputers;
	}
}
