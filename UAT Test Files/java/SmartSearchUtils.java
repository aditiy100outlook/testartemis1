package com.code42.smartsearch;

/**
 * This are some static functions that are used in multiple places for smart search.
 */
public class SmartSearchUtils {

	public static boolean isGuid(String s) {
		return s.matches("^\\d{18,19}$");
	}

	public static boolean isNumber(String s) {
		return s.matches("^\\d+$");
	}

	/**
	 * This is a very permissive email matcher. False negatives should be impossible.
	 */
	public static boolean isEmail(String s) {
		return s.matches("^.+@.+$");
	}

	public static String removePunctuation(String s) {
		return s.replaceAll("\\s|\\p{Punct}", "");
	}
}
