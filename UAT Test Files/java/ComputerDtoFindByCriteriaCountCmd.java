package com.code42.computer;

import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;

/**
 * Returns the total count of all the potential items in ComputerUsageDtoFindByCriteriaQuery.
 */
public class ComputerDtoFindByCriteriaCountCmd extends ComputerDtoFindByCriteriaBaseCmd<ComputerCountDto> {

	public ComputerDtoFindByCriteriaCountCmd(ComputerDtoFindByCriteriaBuilder data) {
		super(data);
	}

	@Override
	public ComputerCountDto exec(CoreSession session) throws CommandException {
		this.authorize(session);

		// Filter hosted organizations.
		if (!session.isSystem()) {
			this.data.filterHosted();
		}

		return this.db.find(new ComputerDtoFindByCriteriaCountQuery(this.data));
	}

	/**
	 * Builds the data used by the command to run the query. The superclass is shared with the query.
	 */
	public static class Builder extends ComputerDtoFindByCriteriaBuilder {

		@Override
		public ComputerDtoFindByCriteriaCountCmd build() throws BuilderException {
			this.validate();
			return new ComputerDtoFindByCriteriaCountCmd(this);
		}
	}

}
