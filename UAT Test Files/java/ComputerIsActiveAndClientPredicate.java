package com.code42.computer;

import com.backup42.common.ComputerType;
import com.google.common.base.Predicate;

public class ComputerIsActiveAndClientPredicate implements Predicate<IComputer> {

	public boolean apply(IComputer c) {
		return c != null && c.getActive() && (c.getType() == ComputerType.COMPUTER || c.getType() == ComputerType.MOBILE);
	}
}