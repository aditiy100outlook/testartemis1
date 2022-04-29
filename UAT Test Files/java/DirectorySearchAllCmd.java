package com.code42.directory;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.directory.Directory;
import com.code42.core.directory.DirectoryEntry;
import com.code42.core.directory.DirectoryException;
import com.code42.core.directory.DirectoryTimeoutException;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.google.common.collect.ImmutableList;

/**
 * Execute a search operation against a set of Directory instances. <br>
 * <br>
 * Per-server protocol configurations are managed by the Directory and the corresponding specification or "spec" object.
 * This command encapsulates the pro_core logic for iterating over this list of Directories, querying them via some set
 * of parameters and aggregating the results. <br>
 * <br>
 * Other things managed by this command (or that would be managed by it in the future):
 * <ol>
 * <li>Retry logic. Timeouts are considered a per-server configuration; they apply to every request made to a particular
 * directory. The retry policy is not configured per-server; retries determine how many of those connections we make to
 * a particular server but they aren't part of the connection itself</li>
 * <li>Order of traversal for the directories. This may be nothing more than traversing an ordered collection provided
 * via the constructor, however in the future we may want to add some prioritization or other sorting mechanism to the
 * directories themselves.</li>
 * <li>Aggregation of results: do we return all matches, the first one, or something in between?</li>
 * </ol>
 * 
 * @author bmcguire
 */
public class DirectorySearchAllCmd extends AbstractCmd<List<DirectoryEntry>> {

	private static Logger log = LoggerFactory.getLogger(DirectorySearchAllCmd.class);

	private final List<Directory> directories;
	private final String query;
	private final int retries;

	private DirectorySearchAllCmd(List<Directory> arg0, String arg1, int arg2) {

		/*
		 * In theory this list is already immutable (since it's provided by the builder) but this gives us an additional
		 * safeguard
		 */
		this.directories = ImmutableList.copyOf(arg0);
		this.query = arg1;
		this.retries = arg2;
	}

	@Override
	public List<DirectoryEntry> exec(CoreSession session) throws CommandException {

		/*
		 * TODO: Should this be limited to the sysadmin user? Seems like any user should be able to query directories in a
		 * system... ?
		 */

		/*
		 * Try each directory in sequence, gathering the results into a single collection. If a particular directory times
		 * out we'll give it another try up to some defined number of retries. If a search against a Directory throws
		 * anything other than a timeout exception that directory is simply removed from the list and no items are added to
		 * the return value.
		 */
		List<DirectoryEntry> rv = new LinkedList<DirectoryEntry>();
		List<Directory> working = new LinkedList<Directory>(this.directories);
		int iteration = 0;
		while (working.size() > 0) {

			ListIterator<Directory> workingIter = working.listIterator();
			while (workingIter.hasNext()) {

				Directory directory = workingIter.next();
				try {

					List<DirectoryEntry> results = directory.search(this.query);

					/* Our contract with a Directory implementation is that null should never get returned here... */
					assert (results != null);

					rv.addAll(results);

					/* Once we add results for a particular directory we're safe to remove it */
					workingIter.remove();

				} catch (DirectoryTimeoutException dte) {

					StringBuilder builder = new StringBuilder();
					builder.append("Non-fatal exception (");
					builder.append((iteration + 1));
					builder.append(" of ");
					builder.append((this.retries + 1));
					builder.append(") while querying directory: ");
					builder.append(directory.toString());
					if (iteration <= this.retries) {
						builder.append(", will retry");
					} else {
						builder.append(", retries exhausted, will not retry");
						workingIter.remove();
					}
					log.info(builder.toString(), dte);

				} catch (DirectoryException de) {

					/*
					 * Anything other than a timeout exception is considered fatal... all we can do is remove the directory and
					 * continue on
					 */
					log.info("Fatal exception while querying directory: " + directory.toString(), de);
					workingIter.remove();
				}
			}
			++iteration;
		}

		return rv;
	}

	public static class Builder {

		private List<Directory> directories;
		private String query;
		private int retries;

		public Builder(String query) {

			this.directories = new LinkedList<Directory>();
			this.query = query;
			this.retries = 3;
		}

		public Builder directory(Directory arg) {
			if (arg == null) {
				return this;
			}
			this.directories.add(arg);
			return this;
		}

		/*
		 * Note that the ordering of the input collection will be respected when it's contents are added to the
		 * list of collections managed by this class. The caller can control ordering by passing an ordered
		 * collection here, or they can pass in a Set implementation and take their chances.
		 */
		public Builder directories(Collection<Directory> arg) {
			if (arg == null) {
				return this;
			}
			this.directories.addAll(arg);
			return this;
		}

		public Builder retries(int arg) {
			this.retries = arg;
			return this;
		}

		public DirectorySearchAllCmd build() {
			return new DirectorySearchAllCmd(this.directories, this.query, this.retries);
		}
	}
}
