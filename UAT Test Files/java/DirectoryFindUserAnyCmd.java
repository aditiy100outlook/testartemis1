package com.code42.directory;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.directory.Directory;
import com.code42.core.directory.DirectoryCommunicationException;
import com.code42.core.directory.DirectoryEntry;
import com.code42.core.directory.DirectoryEntryProperty;
import com.code42.core.directory.DirectoryException;
import com.code42.core.directory.DirectoryMapping;
import com.code42.core.directory.DirectoryMappingException;
import com.code42.core.directory.DirectoryTimeoutException;
import com.code42.core.impl.AbstractCmd;
import com.code42.exception.DebugRuntimeException;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.utils.LangUtils;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

/**
 * Execute findUser() against a set of directories, returning the first non-null response we receive. Note that this
 * command will always returns a single user (or null if one cannot be found, or an exception if multiple are found). If
 * you want to return an aggregate of results from all directories you'll need to use (or perhaps create) the
 * DirectoryFindUserAllCmd command.<br>
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
 * <br>
 * <br>
 * Note that the working assumption of this code is that usernames are unique at least within an org. Or, to put it
 * another way, username and org name are enough by themselves to uniquely identify a CrashPlan user. This is actually a
 * slightly looser version of the constraint in play in the current (as of this writing) code base: at the moment
 * username is regarded as globally unique by most sections of the code even though (as discussed) the database doesn't
 * enforce this constraint.
 */
public class DirectoryFindUserAnyCmd extends AbstractCmd<DirectoryEntry> {

	private static Logger log = LoggerFactory.getLogger(DirectoryFindUserAnyCmd.class);

	private final Builder data;

	private DirectoryFindUserAnyCmd(Builder data) {
		this.data = data;
	}

	@Override
	public DirectoryEntry exec(CoreSession session) throws CommandException {

		/*
		 * Try each directory in sequence, gathering the results into a single collection. If a particular directory times
		 * out we'll give it another try up to some defined number of retries. If a search against a Directory throws
		 * anything other than a timeout exception that directory is simply removed from the list and no items are added to
		 * the return value.
		 * 
		 * Note that we don't maintain a separate retry counter for each directory. For a given iteration every candidate
		 * either successfully executes (and thus is no longer a candidate) or uses up a retry. Net result is that retry
		 * counter is common to all candidates.
		 */
		Set<DirectoryEntry> candidates = new HashSet<DirectoryEntry>();
		List<Directory> working = new LinkedList<Directory>(this.data.directories);
		int iteration = 0;
		while (working.size() > 0) {

			ListIterator<Directory> workingIter = working.listIterator();
			while (workingIter.hasNext()) {

				Directory directory = workingIter.next();
				try {

					List<DirectoryEntry> results = directory.findUser(this.data.username);

					/* Our contract with a Directory implementation is that null should never get returned here... */
					assert (results != null);

					candidates.addAll(results);

					/* Once we add results for a particular directory we're safe to remove it */
					workingIter.remove();

				} catch (DirectoryTimeoutException dte) {

					StringBuilder str = new StringBuilder();
					str.append("Non-fatal exception (");
					str.append((iteration + 1));
					str.append(" of ");
					str.append((this.data.retries + 1));
					str.append(") while querying directory: ");
					str.append(directory.toString());
					if (iteration <= this.data.retries) {
						str.append(", will retry");
					} else {
						str.append(", retries exhausted, will not retry");
						workingIter.remove();
					}
					log.info("DIR:: {}", str.toString(), dte);

				} catch (DirectoryCommunicationException dce) {
					// The LDAP Server is probably down. This is not a reason to print the stack trace.
					log.info("DIR:: Unable to query directory: {} message: {}", directory.toString(), dce.getMessage());
					log.debug("DIR:: Stack trace:", dce); // To see the stack trace, set DEBUG logging for this class

					// Nothing we can do but move on to the next server
					workingIter.remove();

				} catch (DirectoryException de) {
					log.info("DIR:: Unable to query directory: {}", directory.toString(), de);
					// All we can do here is remove the directory and continue on to the next server
					workingIter.remove();
				}
			}
			++iteration;
		}

		/* If we have an org name constraint check each candidate and remove them if they don't match that constraint */
		if (!(this.data.orgName instanceof None)) {

			candidates = new HashSet<DirectoryEntry>(Collections2.filter(candidates, new Predicate<DirectoryEntry>() {

				public boolean apply(DirectoryEntry entry) {

					if (!entry.hasProperty(DirectoryEntryProperty.MAPPING)) {
						return false;
					}
					DirectoryMapping mapping = entry.getPropertyAsType(DirectoryEntryProperty.MAPPING, DirectoryMapping.class);
					if (mapping == null) {
						return false;
					}
					String argOrgName = null;
					try {
						argOrgName = mapping.getOrgName(entry);
					} catch (DirectoryMappingException e) {
						throw new DebugRuntimeException("Error mapping orgName", e);
					}
					if (argOrgName == null) {
						return false;
					}
					return argOrgName.equals(DirectoryFindUserAnyCmd.this.data.orgName.get());
				}
			}));
		}

		/*
		 * We now should have exactly one user (if something was found) or zero users (if there was nothing to find).
		 * Anything else is an exception case.
		 */
		if (candidates.size() == 0) {
			return null;
		}

		if (candidates.size() == 1) {
			List<DirectoryEntry> tmp = new LinkedList(candidates);
			return tmp.get(0);
		}

		throw new NoUniqueDirectoryEntryException("Couldn't find a unique directory entry for username "
				+ this.data.username);
	}

	public static class Builder {

		List<Directory> directories;
		String username;
		Option<String> orgName;
		int retries;

		/* A username must be provided but the orgName is optional */
		public Builder(String username) {
			this.directories = new LinkedList<Directory>();
			this.username = username;
			this.orgName = None.getInstance();
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
		 * Note that the ordering of the input collection will be respected when it's contents are added to the list of
		 * collections managed by this class. The caller can control ordering by passing an ordered collection here, or they
		 * can pass in a Set implementation and take their chances.
		 */
		public Builder directories(Collection<Directory> arg) {
			if (arg == null) {
				return this;
			}
			this.directories.addAll(arg);
			return this;
		}

		public Builder orgName(String arg) {
			this.orgName = new Some<String>(arg);
			return this;
		}

		public Builder retries(int arg) {
			this.retries = arg;
			return this;
		}

		public void validate() throws BuilderException {
			if (!LangUtils.hasValue(this.username)) {
				throw new BuilderException("Username is required");
			}
			if (this.directories.isEmpty()) {
				throw new BuilderException("Directories are required");
			}
		}

		public DirectoryFindUserAnyCmd build() throws BuilderException {
			this.validate();
			this.directories = ImmutableList.copyOf(this.directories);
			return new DirectoryFindUserAnyCmd(this);
		}
	}
}
