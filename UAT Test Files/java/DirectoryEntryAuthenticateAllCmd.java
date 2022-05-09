package com.code42.auth;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import com.code42.core.CommandException;
import com.code42.core.auth.AuthenticationResult;
import com.code42.core.auth.Authenticator;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.directory.DirectoryEntry;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.google.common.collect.ImmutableList;

/**
 * Attempt to authenticate the input username and credentials against a collection of authenticators. The authenticators
 * are tried in the order defined by the input collection; if any of them fail an exception will be thrown, otherwise
 * the directory entry has successfully been authenticated. <br>
 * <br>
 * At the moment this does a bit more than PROe Server requires; as of this writing we only support authentication
 * against a single auth source for any one user. By supporting a collection of Authenticators out of the gate, however,
 * we add built-in support for something like two-factor authentication going forward.
 */
@Deprecated
public class DirectoryEntryAuthenticateAllCmd extends AbstractCmd<Void> {

	private static Logger log = LoggerFactory.getLogger(DirectoryEntryAuthenticateAllCmd.class);

	private final DirectoryEntry entry;
	private final String password;
	private final List<Authenticator> authenticators;
	private final int retries;

	private DirectoryEntryAuthenticateAllCmd(DirectoryEntry entry, String password, List<Authenticator> arg, int retries) {

		this.entry = entry;
		this.password = password;
		this.authenticators = ImmutableList.copyOf(arg);
		this.retries = retries;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

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
		List<Authenticator> working = new LinkedList<Authenticator>(this.authenticators);
		int iteration = 0;
		while (working.size() > 0) {

			/*
			 * Note that we don't catch ANY exceptions in the while loop below. Any type of exception constitutes a failure to
			 * complete authentication. If that exception is an AuthenticationException it may correspond to a specific
			 * authentication outcome but even a general exception prevents us from continuing. Remember that in order to
			 * satisfy the criteria of this command ALL authenticators must successfully approve the passed credentials and
			 * any kind of exception indicates that that clearly cannot happen.
			 */
			ListIterator<Authenticator> workingIter = working.listIterator();
			while (workingIter.hasNext()) {

				Authenticator auth = workingIter.next();

				AuthenticationResult result = auth.authenticate(this.entry, this.password);
				if (result == AuthenticationResult.SUCCESS) {

					/* Once authentication succeeds against a particular authenticator we're safe to remove it */
					workingIter.remove();
				} else if (result == AuthenticationResult.TIMEOUT) {

					StringBuilder builder = new StringBuilder();
					builder.append("Timeout (");
					builder.append((iteration + 1));
					builder.append(" of ");
					builder.append((this.retries + 1));
					builder.append(") while querying authenticator: ");
					builder.append(auth.toString());
					if (iteration <= this.retries) {
						builder.append(", will retry");
					} else {
						builder.append(", retries exhausted, will not retry");
						workingIter.remove();
					}
					log.info("AUTH:: " + builder.toString());
				} else {

					StringBuilder builder = new StringBuilder();
					builder.append("Unexpected return value [\"");
					builder.append(result.toString());
					builder.append("\"] (");
					builder.append((iteration + 1));
					builder.append(" of ");
					builder.append((this.retries + 1));
					builder.append(") while querying authenticator: ");
					builder.append(auth.toString());
					builder.append(", removing from working list");
					log.info("AUTH:: " + builder.toString());

					workingIter.remove();
				}
			}
			++iteration;
		}

		return null;
	}

	public static class Builder {

		private DirectoryEntry entry;
		private String password;
		private List<Authenticator> authenticators;
		private int retries;

		public Builder(DirectoryEntry entry, String password) {

			this.entry = entry;
			this.password = password;
			this.authenticators = new LinkedList<Authenticator>();
			this.retries = 3;
		}

		public Builder authenticator(Authenticator arg) {
			if (arg == null) {
				return this;
			}
			this.authenticators.add(arg);
			return this;
		}

		/*
		 * Note that the ordering of the input collection will be respected when it's contents are added to the list of
		 * collections managed by this class. The caller can control ordering by passing an ordered collection here, or they
		 * can pass in a Set implementation and take their chances.
		 */
		public Builder authenticators(Collection<Authenticator> arg) {
			if (arg == null) {
				return this;
			}
			this.authenticators.addAll(arg);
			return this;
		}

		public Builder retries(int arg) {
			this.retries = arg;
			return this;
		}

		public DirectoryEntryAuthenticateAllCmd build() {
			return new DirectoryEntryAuthenticateAllCmd(this.entry, this.password, this.authenticators, this.retries);
		}
	}
}
