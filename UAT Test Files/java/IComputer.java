/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.computer;

import com.backup42.common.ComputerType;

public interface IComputer {

	public Long getComputerId();

	public long getGuid();

	public int getUserId();

	public boolean getActive();

	public boolean getBlocked();

	public ComputerType getType();

	public Computer toComputer();

}