package com.code42.computer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.businessobjects.BusinessObjectsException;
import com.code42.core.businessobjects.IBusinessObjectsService;
import com.code42.core.impl.AbstractCmd;
import com.google.inject.Inject;

public class ComputerSsoFindMultipleByGuidCmd extends AbstractCmd<Map<Long, ComputerSso>> {

	@Inject
	private IBusinessObjectsService busObj;

	private final Set<Long> guids;

	public ComputerSsoFindMultipleByGuidCmd(Collection<Long> guids) {
		this(new HashSet<Long>(guids));
	}

	public ComputerSsoFindMultipleByGuidCmd(Set<Long> guids) {
		this.guids = guids;
	}

	@Override
	public Map<Long, ComputerSso> exec(CoreSession session) throws CommandException {

		final Map<Long, ComputerSso> rv;
		try {
			rv = this.busObj.getComputersByGuid(this.guids);
		} catch (BusinessObjectsException e) {
			throw new CommandException("Error loading ComputerSsos", e);
		}

		return rv;
	}
}
