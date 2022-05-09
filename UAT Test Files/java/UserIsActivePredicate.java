package com.code42.user;

import com.google.common.base.Predicate;

public class UserIsActivePredicate implements Predicate<IUser> {

	public boolean apply(IUser u) {
		return u != null && u.isActive() && !u.isInvited();
	}
}
