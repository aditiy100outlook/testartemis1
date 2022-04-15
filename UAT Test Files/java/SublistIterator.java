package com.code42.util;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A utility class that is used to split an existing list of items of type T into several sublists. This is generally
 * used so that the impact of individual requests to the database are minimized (i.e. multiple small requests rather
 * than a single very large one).
 */
public class SublistIterator<T> implements Iterator<List<T>> {

	private final Iterator<List<T>> subListIterator;

	public SublistIterator(List<T> fullList, int sublistSize) {
		if (fullList == null) {
			throw new IllegalArgumentException("fullList cannot be null or empty");
		}
		if (sublistSize <= 0) {
			throw new IllegalArgumentException("sublistSize must be a positive integer");
		}

		List<List<T>> subLists = new LinkedList<List<T>>();
		if (fullList.size() == 0) {
			subLists.add(Collections.EMPTY_LIST);
		} else {
			int calculatedSize = sublistSize > fullList.size() ? fullList.size() : sublistSize;

			int numberOfSublists = fullList.size() / calculatedSize;
			for (int i = 0; i < numberOfSublists; i++) {
				int startIdx = i * calculatedSize;
				subLists.add(fullList.subList(startIdx, startIdx + calculatedSize));
			}

			int itemsInFinalSublist = fullList.size() % calculatedSize;
			if (itemsInFinalSublist != 0) {
				subLists.add(fullList.subList(numberOfSublists * calculatedSize, fullList.size()));
			}
		}

		this.subListIterator = subLists.iterator();
	}

	public boolean hasNext() {
		return this.subListIterator.hasNext();
	}

	public List<T> next() {
		return this.subListIterator.next();
	}

	public void remove() {
		throw new UnsupportedOperationException("SublistIterator does not support the remove operation");
	}
}