package com.code42.smartsearch;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.smartsearch.computer.SmartSearchComputerCountCmd;
import com.code42.smartsearch.mountpoint.SmartSearchMountPointCountCmd;
import com.code42.smartsearch.org.SmartSearchOrgCountCmd;
import com.code42.smartsearch.server.SmartSearchServerCountCmd;
import com.code42.smartsearch.user.SmartSearchUserCountCmd;
import com.code42.utils.Stopwatch;
import com.code42.utils.SystemProperties;

class SmartSearchCountCmd extends AbstractCmd<Map<SmartSearchType, Integer>> {

	private static final Logger log = LoggerFactory.getLogger(SmartSearchCountCmd.class);

	private static final String SMART_SEARCH_ASYNC_ENABLED = "b42.smartSearch.async.enabled";

	private String term;

	public SmartSearchCountCmd(String term) {
		this.setTerm(term);
	}

	public void setTerm(String term) {
		this.term = term;
	}

	@Override
	public Map<SmartSearchType, Integer> exec(CoreSession session) throws CommandException {

		Map<SmartSearchType, Integer> counts = new HashMap<SmartSearchType, Integer>();

		Stopwatch sw = new Stopwatch();

		// We don't know if database connection contention is what is causing search slowness or not.
		// So this will allow us to turn it on and off to see if it helps.
		boolean async = SystemProperties.getOptionalBoolean(SMART_SEARCH_ASYNC_ENABLED, true);

		if (async) {

			Map<SmartSearchType, Future<Integer>> futures = new HashMap<SmartSearchType, Future<Integer>>();

			futures.put(SmartSearchType.COMPUTER, this.runtime.runAsync(new SmartSearchComputerCountCmd(this.term), session));
			futures.put(SmartSearchType.ORG, this.runtime.runAsync(new SmartSearchOrgCountCmd(this.term), session));
			futures.put(SmartSearchType.USER, this.runtime.runAsync(new SmartSearchUserCountCmd(this.term), session));
			futures.put(SmartSearchType.SERVER, this.runtime.runAsync(new SmartSearchServerCountCmd(this.term), session));
			futures.put(SmartSearchType.MOUNT_POINT, this.runtime.runAsync(new SmartSearchMountPointCountCmd(this.term),
					session));

			try {
				for (SmartSearchType type : futures.keySet()) {
					Integer count = futures.get(type).get();
					counts.put(type, count);
				}
			} catch (InterruptedException e) {
				throw new CommandException("Unable to count smart search matches", e);
			} catch (ExecutionException e) {
				throw new CommandException("Unable to count smart search matches", e);
			}

		} else {

			// Run each synchronously
			counts.put(SmartSearchType.COMPUTER, this.runtime.run(new SmartSearchComputerCountCmd(this.term), session));
			counts.put(SmartSearchType.ORG, this.runtime.run(new SmartSearchOrgCountCmd(this.term), session));
			counts.put(SmartSearchType.USER, this.runtime.run(new SmartSearchUserCountCmd(this.term), session));
			counts.put(SmartSearchType.SERVER, this.runtime.run(new SmartSearchServerCountCmd(this.term), session));
			counts.put(SmartSearchType.MOUNT_POINT, this.runtime.run(new SmartSearchMountPointCountCmd(this.term), session));
		}

		log.trace(
				"SmartSearch:: Counting of computers, orgs, users, servers, mount_points for term '{}' took {} ms in {} mode.  To change mode, change property: {}",
				this.term, sw.getElapsed(), async ? "asynchronous" : "synchronous", SMART_SEARCH_ASYNC_ENABLED);

		return counts;
	}

}
