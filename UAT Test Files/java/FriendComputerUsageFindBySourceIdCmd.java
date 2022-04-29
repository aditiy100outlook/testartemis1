package com.code42.computer;

import java.util.List;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.social.FriendComputerUsage;

public class FriendComputerUsageFindBySourceIdCmd extends DBCmd<List<FriendComputerUsage>> {

	long computerId;

	public FriendComputerUsageFindBySourceIdCmd(long computerId) {
		super();
		this.computerId = computerId;
	}

	@Override
	public List<FriendComputerUsage> exec(CoreSession session) throws CommandException {
		FriendComputerUsageFindBySourceIdQuery query = new FriendComputerUsageFindBySourceIdQuery(this.computerId);
		List<FriendComputerUsage> list = this.db.find(query);
		return list;
	}

}