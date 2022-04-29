package com.code42.license;

import java.util.TreeSet;

import com.backup42.computer.AuthLicenseHolder;
import com.code42.computer.IComputer;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.user.User;

public interface LicenseCallback {

	public boolean hasDependentComputers(License license, CoreSession session) throws CommandException;

	public void transferLicenseToUser(License license, User targetUser, CoreSession session) throws CommandException;

	public TreeSet<AuthLicenseHolder> getConsumerLicenses(int userId, IComputer computer, boolean registerTrial,
			int... excludeSubscriptionIds);
}
