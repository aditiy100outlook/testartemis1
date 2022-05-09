package com.code42.balance;

import java.util.List;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.google.inject.Inject;

public class BalanceActivityDtoFindCurrentByMountCmd extends DBCmd<List<BalanceActivityDto>> {

	private final int mountId;
	@Inject
	private IBalanceActivityService activityService;
	private List<CurrentActivityBean> activity = null;

	public BalanceActivityDtoFindCurrentByMountCmd(int mountId) {
		super();
		this.mountId = mountId;
	}

	/**
	 * Test helper; this overrides the search for current activity within the balancer.
	 */
	void setTestBeans(List<CurrentActivityBean> testBeans) {
		this.activity = testBeans;
	}

	@Override
	public List<BalanceActivityDto> exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		if (this.activity == null) {
			this.activity = this.activityService.findByMount(this.mountId);
		}

		final List<BalanceActivityDto> dtos = this.run(new BalanceActivityDtoFindByCurrentActivityCmd(this.activity),
				session);
		return dtos;
	}
}
