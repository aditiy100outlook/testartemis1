package com.code42.env;

import com.code42.core.CommandException;
import com.code42.core.IEnvironment;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.google.inject.Inject;

/**
 * Command to provide access to IEnvironment.getMyNodeGuid()... for contexts that have access to a runtime but not
 * dependency injection proper.
 */
public class GetMyNodeGuidCmd extends AbstractCmd<Long> {

	private IEnvironment env;

	@Inject
	private void setEnv(IEnvironment env) {

		this.env = env;
	}

	@Override
	public Long exec(CoreSession session) throws CommandException {

		return this.env.getMyNodeGuid();
	}
}
