package com.code42.smartsearch;

public class SmartSearchMatch {

	private long id; // Some IDs are ints and some longs
	private String name;
	private SmartSearchType type;
	
	public SmartSearchMatch(long id, String name, SmartSearchType type) {
		this.id = id;
		this.name = name;
		this.type = type;
	}
	
	public long getId() {
		return this.id;
	}
	
	public String getName() {
		return this.name;
	}
	
	public SmartSearchType getType() {
		return this.type;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (id ^ (id >>> 32));
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SmartSearchMatch other = (SmartSearchMatch) obj;
		if (id != other.id)
			return false;
		if (type != other.type)
			return false;
		return true;
	}
	
}
