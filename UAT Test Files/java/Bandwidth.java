package com.code42.stats;

import java.io.Serializable;

/**
 * Really not much more than a struct. We could accomplish the same thing with Pair<Long,Long> but using primitive types
 * rather than objects gives this class an (admittedly marginal) advantage over the tuple approach.
 * 
 * @author bmcguire
 */
public class Bandwidth implements Serializable {

	private static final long serialVersionUID = -4701187414239214831L;

	private long bwIn;
	private long bwOut;

	public Bandwidth(long bwIn, long bwOut) {
		this.bwIn = bwIn;
		this.bwOut = bwOut;
	}

	public long getBandwidthIn() {
		return this.bwIn;
	}

	public long getBandwidthOut() {
		return this.bwOut;
	}

	@Override
	public String toString() {
		return "Bandwidth [bwIn=" + this.bwIn + ", bwOut=" + this.bwOut + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (this.bwIn ^ (this.bwIn >>> 32));
		result = prime * result + (int) (this.bwOut ^ (this.bwOut >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (this.getClass() != obj.getClass()) {
			return false;
		}
		Bandwidth other = (Bandwidth) obj;
		if (this.bwIn != other.bwIn) {
			return false;
		}
		if (this.bwOut != other.bwOut) {
			return false;
		}
		return true;
	}
}
