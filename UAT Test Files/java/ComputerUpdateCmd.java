/*
 * <a href="http://www.code42.com">(c) 2011 Code 42 Software, Inc.</a>
 */
package com.code42.computer;

import com.backup42.common.command.ServiceCommand;
import com.backup42.computer.EncryptionKeyServices;
import com.backup42.history.CpcHistoryLogger;
import com.code42.backup.SecureDataKey;
import com.code42.backup.SecurityKeyType;
import com.code42.config.ComputerConfigUpdateCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.code42.hibernate.aftertx.AfterTxRunnable;
import com.code42.hibernate.aftertx.IAfterTxRunnable.Priority;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.user.DataEncryptionKeyFindByUserQuery;
import com.code42.utils.LangUtils;

/**
 * Validate and update a few fields on the given computer.<br>
 * <br>
 * As of Bugzilla 1587 this command cannot block, unblock, activate, deactivate or deauthorize a computer. Since the
 * only thing left for this command to accomplish is a name change we've removed the Builder implementation and replaced
 * it with a straightforward constructor.
 */
public class ComputerUpdateCmd extends DBCmd<Computer> {

	private static final Logger log = LoggerFactory.getLogger(ComputerUpdateCmd.class);

	public enum Error {
		SECURITY_KEY_DOWNGRADE
		// they can't ever downgrade their security key type for security reasons
	}

	private Builder data;

	public ComputerUpdateCmd(Builder builder) {
		this.data = builder;
	}

	/**
	 * @return null if the computerId does not exist.
	 */
	@Override
	public Computer exec(final CoreSession session) throws CommandException {

		this.runtime.run(new IsComputerManageableCmd(this.data.computerId, C42PermissionApp.Computer.UPDATE), session);

		Computer computer = null;
		this.db.beginTransaction();
		try {
			// Return null if this computer does not exist.
			computer = this.runtime.run(new ComputerFindByIdCmd(this.data.computerId), session);
			if (computer == null) {
				return null; // Computer does not exist
			}

			boolean changed = false;

			// Save the config
			if (this.data.configXml != null) {
				final ComputerConfigUpdateCmd.Builder builder = new ComputerConfigUpdateCmd.Builder(computer,
						this.data.configXml).force();
				this.runtime.run(builder.build(), session);
				changed = true;
			}

			if (this.data.name != null) {
				computer.setName(this.data.name);
				changed = true;
			}

			// Save the security key type
			if (this.data.securityKeyType != null) {
				final int userId = computer.getUserId();
				final DataEncryptionKey dataEncryptionKey = this.db.find(new DataEncryptionKeyFindByUserQuery(userId));
				if (dataEncryptionKey == null) {
					throw new CommandException("Unable to set security key type for userId=" + userId
							+ ", failed to find DataEncryptionKey row.");
				}
				final SecurityKeyType prevType = dataEncryptionKey.getSecurityKeyType();

				// Make certain they aren't downgrading security.
				if (this.data.securityKeyType.ordinal() > prevType.ordinal()) {
					SecureDataKey secureKey = null;
					if (this.data.securityKeyType.equals(SecurityKeyType.PrivatePassword)) {
						secureKey = new SecureDataKey(dataEncryptionKey.getSecureDataKey().getBytes());
					}
					EncryptionKeyServices.getInstance().storeKey(userId, null, secureKey);

					// If we reauthorize then lets make sure they are getting an updated social network, in case it changed.
					this.run(new ComputerSocialNetworkDateUpdateCmd(computer), session);

					this.db.afterTransaction(new AfterTxRunnable(Priority.NORMAL) {

						public void run() {
							// Let all my computers know that the key has changed.
							CoreBridge.getCentralService().getPeer().sendServiceCommandToUser(userId, ServiceCommand.REAUTHORIZE);
						}
					});
					CpcHistoryLogger.info(session,
							"Change SecurityKeyType from " + prevType + " to " + this.data.securityKeyType, computer.getGuid());
					changed = true;
				} else if (this.data.securityKeyType.equals(prevType)) {
					// No change, do nothing
				} else {
					// Downgrade, this is not allowed
					throw new CommandException(Error.SECURITY_KEY_DOWNGRADE, "Unable to set security key type for userId="
							+ computer.getUserId() + ". Can't downgrade security from " + prevType.name() + " to "
							+ this.data.securityKeyType.name());
				}
			}

			if (changed) {
				computer = this.db.update(new ComputerUpdateQuery(computer));

				// Notify social network after transaction is complete
				this.db.afterTransaction(new ComputerPublishUpdateCmd(computer), session);
			}

			this.db.commit();

			if (changed) {
				CpcHistoryLogger.info(session, "modified computer: {}", computer);
			}

		} catch (CommandException e) {
			this.db.rollback();
			throw e;
		} catch (Throwable e) {
			this.db.rollback();
			log.error("Unexpected: ", e);
		} finally {
			this.db.endTransaction();
		}

		return computer;
	}

	public static class Builder {

		long computerId;
		String name = null;
		String configXml = null;
		SecurityKeyType securityKeyType = null;

		public Builder(long computerId) {
			this.computerId = computerId;
		}

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder configXml(String xml) {
			this.configXml = xml;
			return this;
		}

		public Builder securityKeyType(String type) {
			if (LangUtils.hasValue(type)) {
				this.securityKeyType = SecurityKeyType.valueOf(type);
			} else {
				this.securityKeyType = null;
			}
			return this;
		}

		public ComputerUpdateCmd build() {
			return new ComputerUpdateCmd(this);
		}

	}
}
