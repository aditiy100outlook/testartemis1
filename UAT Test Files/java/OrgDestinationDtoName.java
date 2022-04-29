package com.code42.org.destination;

import com.code42.computer.Computer;
import com.code42.server.destination.Destination;

/**
 * Defines the basics of a destination necessary to painting them into a UI.
 */
public class OrgDestinationDtoName {

	private final OrgDestination orgDestination;
	private final Destination destination;
	private final Computer destinationComputer;

	public OrgDestinationDtoName(OrgDestination orgDestination, Destination destination, Computer destinationComputer) {
		super();
		this.orgDestination = orgDestination;
		this.destination = destination;
		this.destinationComputer = destinationComputer;
	}

	public String getName() {
		return this.destination.getDestinationName();
	}

	public int getDestinationId() {
		return this.destination.getDestinationId();
	}

	public long getGuid() {
		return this.destinationComputer.getGuid();
	}

	@Override
	public String toString() {
		return "OrgDestinationDtoName [orgDestination=" + this.orgDestination + ", destination=" + this.destination
				+ ", destinationComputer=" + this.destinationComputer + "]";
	}
}
