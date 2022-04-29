package com.code42.auth;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import com.code42.core.CommandException;
import com.code42.core.auth.AuthenticationCommunicationException;
import com.code42.core.auth.AuthenticationResult;
import com.code42.core.auth.Authenticator;
import com.code42.core.auth.PasswordAuthenticationFailedException;
import com.code42.core.auth.UserNotFoundAuthenticationException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.directory.DirectoryEntry;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.google.common.collect.ImmutableList;

/**
 * Attempt to authenticate the input username and credentials against a collection of authenticators. The authenticators
 * are tried in the order defined by the input collection; if any of them fail with an invalid password, false will be
 * returned. If any succeed, true will be returned. <br>
 * <br>
 * At the moment this does a bit more than PROe Server requires; as of this writing we only support authentication
 * against a single auth source for any one user. By supporting a collection of Authenticators out of the gate, however,
 * we add built-in support for something like two-factor authentication going forward.
 */
public class DirectoryEntryAuthenticateAnyCmd extends AbstractCmd<Boolean> {

	private static Logger log = LoggerFactory.getLogger(DirectoryEntryAuthenticateAnyCmd.class);

	private final DirectoryEntry entry;
	private final String password;
	private final List<Authenticator> authenticators;
	private final int retries;

	private DirectoryEntryAuthenticateAnyCmd(DirectoryEntry entry, String password, List<Authenticator> arg, int retries) {

		this.entry = entry;
		this.password = password;
		this.authenticators = ImmutableList.copyOf(arg);
		this.retries = retries;
	}

	@Override
	public Boolean exec(CoreSession session) throws CommandException {

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
		List<AuthenticatorErrorHolder> exceptions = new ArrayList<AuthenticatorErrorHolder>();
		int iteration = 0;
		int failureCount = 0;
		while (working.size() > 0) {

			ListIterator<Authenticator> workingIter = working.listIterator();
			while (workingIter.hasNext()) {

				Authenticator auth = workingIter.next();

				try {
					log.debug("AUTH:: Attempting... Authenticater: {} Entry: {}", auth, this.entry);

					// Authenticate
					AuthenticationResult result = auth.authenticate(this.entry, this.password);

					// There are only two non-exception cases
					if (result == AuthenticationResult.SUCCESS) {
						log.debug("AUTH:: Successfully authenticated {} after {} failures", this.entry.getIdentity(), failureCount);
						return true;

					} else if (result == AuthenticationResult.TIMEOUT) {
						StringBuilder builder = new StringBuilder();
						builder.append("Timeout (").append((iteration + 1));
						builder.append(" of ").append((this.retries + 1));
						builder.append(") while querying authenticator: ");
						builder.append(auth.toString());
						if (iteration <= this.retries) {
							// Do not remove authenticator because we'll try again
							builder.append(", will retry");
						} else {
							builder.append(", retries exhausted, will not retry");
							workingIter.remove();
						}
						log.info("AUTH:: " + builder.toString());
					}

				} catch (AuthenticationCommunicationException e) {
					if (iteration <= this.retries) {
						// Leave it in the list to try again
					} else {
						workingIter.remove();
					}

				} catch (PasswordAuthenticationFailedException e) {
					// End of the road. User was found, but password was incorrect.
					log.debug("AUTH:: Invalid password determined for {} after {} failures", this.entry.getIdentity(),
							failureCount);
					return false;
				} catch (UserNotFoundAuthenticationException e) {
					// User was not found in this authenticator (it must have originally come from a different one).
					// No need to log the exception... too much log noise otherwise
					workingIter.remove();
				} catch (Exception e) {
					// Don't try this authenticator anymore.
					// Assume that an error now will still be an error later.
					exceptions.add(new AuthenticatorErrorHolder(auth, e));
					workingIter.remove();
				}
			}
			++iteration;
		}

		// At this point we've failed authenticating against any server.

		log.warn("AUTH:: Unable to authenticate entry {}. {} errors listed below:", this.entry.getIdentity(), exceptions
				.size());
		for (AuthenticatorErrorHolder error : exceptions) {
			log.warn("AUTH:: " + error.toString());
		}

		return false;
	}

	/**
	 * Simple container class for associating an authenticator with an exception
	 */
	public static class AuthenticatorErrorHolder {

		private Authenticator authenticator;
		private Exception exception;

		public AuthenticatorErrorHolder(Authenticator authenticator, Exception exception) {
			super();
			this.authenticator = authenticator;
			this.exception = exception;
		}

		public Authenticator getAuthenticator() {
			return this.authenticator;
		}

		public Exception getException() {
			return this.exception;
		}

		@Override
		public String toString() {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			PrintWriter writer = new PrintWriter(baos);
			this.exception.printStackTrace(writer);

			StringBuilder s = new StringBuilder("AuthenticatorErrorHolder[");
			s.append(this.exception.getMessage()).append(", ").append(this.authenticator).append("\n").append(
					writer.toString()).append("\n]");
			return s.toString();
		}
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

		public DirectoryEntryAuthenticateAnyCmd build() {
			return new DirectoryEntryAuthenticateAnyCmd(this.entry, this.password, this.authenticators, this.retries);
		}
	}
}
