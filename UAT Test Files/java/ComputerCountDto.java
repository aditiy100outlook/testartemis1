/**
 * <a href="http://www.code42.com">(c)Code 42 Software, Inc.</a>
 * $Id: $
 */
package com.code42.computer;

public class ComputerCountDto {

	int totalCount = 0;
	int warningCount = 0;
	int criticalCount = 0;

	/** @return the total number of computers in this query if there were no paging */
	public int getTotalCount() {
		return this.totalCount;
	}

	/** @return the number of computers with warning connection alerts in this query if there were no paging */
	public int getWarningCount() {
		return this.warningCount;
	}

	/** @return the number of computers with critical connection alerts in this query if there were no paging */
	public int getCriticalCount() {
		return this.criticalCount;
	}
}