package com.code42.computer;

import java.io.Serializable;

import com.backup42.common.ComputerType;
import com.code42.core.BuilderException;
import com.code42.core.impl.CoreBridge;

/**
 * A Computer "space" storage object.
 */
public class ComputerSso implements Serializable, IComputer {

	private static final long serialVersionUID = -5700692767726443631L;

	// Computer attributes
	private final long computerId;
	private final long guid;
	private final int userId;
	private final boolean active;
	private final boolean blocked;
	private final ComputerType type;

	public ComputerSso(Computer c) {

		this.computerId = c.getComputerId();
		this.guid = c.getGuid();
		this.userId = c.getUserId();
		this.active = c.getActive();
		this.blocked = c.getBlocked();
		this.type = c.getType();
	}

	private ComputerSso(Builder builder) {

		this.computerId = builder.computerId;
		this.guid = builder.guid;
		this.userId = builder.userId;
		this.active = builder.active;
		this.blocked = builder.blocked;
		this.type = builder.type;
	}

	/*
	 * @see com.code42.computer.IComputer#getComputerId()
	 */
	public Long getComputerId() {
		return this.computerId;
	}

	/*
	 * @see com.code42.computer.IComputer#getGuid()
	 */
	public long getGuid() {
		return this.guid;
	}

	/*
	 * @see com.code42.computer.IComputer#getUserId()
	 */
	public int getUserId() {
		return this.userId;
	}

	public boolean getActive() {
		return this.active;
	}

	public boolean getBlocked() {
		return this.blocked;
	}

	public ComputerType getType() {
		return this.type;
	}

	public Computer toComputer() {
		return CoreBridge.runNoException(new ComputerFindByIdCmd(this.computerId));
	}

	@Override
	public String toString() {
		return "ComputerSso [computerId=" + this.computerId + ", guid=" + this.guid + ", userId=" + this.userId
				+ ", active=" + this.active + ", blocked=" + this.blocked + ", type=" + this.type + "]";
	}

	public static class Builder {

		private Long computerId;
		private Long guid;
		private Integer userId;
		private Boolean active;
		private Boolean blocked;
		private ComputerType type;

		public Builder computerId(long computerId) {

			this.computerId = computerId;
			return this;
		}

		public Builder guid(long guid) {

			this.guid = guid;
			return this;
		}

		public Builder userId(int userId) {

			this.userId = userId;
			return this;
		}

		public Builder active(boolean active) {

			this.active = active;
			return this;
		}

		public Builder blocked(boolean blocked) {

			this.blocked = blocked;
			return this;
		}

		public Builder type(ComputerType type) {

			this.type = type;
			return this;
		}

		public void reset() {

			this.computerId = null;
			this.guid = null;
			this.userId = null;
			this.active = null;
			this.blocked = null;
			this.type = null;
		}

		public void validate() throws BuilderException {

			if (this.computerId == null || this.guid == null || this.userId == null) {

				throw new BuilderException("All fields must be set");
			}
			if (this.active == null || this.blocked == null || this.type == null) {

				throw new BuilderException("All fields must be set");
			}
		}

		public ComputerSso build() throws BuilderException {

			this.validate();
			return new ComputerSso(this);
		}
	}
}
