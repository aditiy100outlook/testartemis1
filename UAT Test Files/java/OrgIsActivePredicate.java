package com.code42.org;

import com.google.common.base.Predicate;

public class OrgIsActivePredicate implements Predicate<IOrg> {

	public boolean apply(IOrg o) {
		return o != null && o.isActive();
	}
}