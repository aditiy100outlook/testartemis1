package com.code42.computer;

import java.util.Date;

import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;

/**
 * Finds the last time a row changed in what would have been the result set.
 */
public class ComputerDtoFindByCriteriaLastModifiedCmd extends ComputerDtoFindByCriteriaBaseCmd<Date> {

	public ComputerDtoFindByCriteriaLastModifiedCmd(Builder data) {
		super(data);
	}

	@Override
	public Date exec(CoreSession session) throws CommandException {
		this.authorize(session);

		// Filter hosted organizations.
		if (!session.isSystem()) {
			this.data.filterHosted();
		}

		return this.db.find(new ComputerDtoFindByCriteriaLastModifiedQuery(this.data));
	}

	/**
	 * Builds the data used by the command to run the query. The superclass is shared with the query.
	 */
	public static class Builder extends ComputerDtoFindByCriteriaBuilder {

		@Override
		public ComputerDtoFindByCriteriaLastModifiedCmd build() throws BuilderException {
			this.validate();
			return new ComputerDtoFindByCriteriaLastModifiedCmd(this);
		}
	}

}
