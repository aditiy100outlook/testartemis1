/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.computer;

import java.util.List;

import org.hibernate.Query;
import org.hibernate.Session;

import com.code42.archiverecord.ArchiveRecordDeleteByComputerCmd;
import com.code42.auth.DeleteAuthCheckCmd;
import com.code42.auth.DeleteAuthCheckCmd.CheckType;
import com.code42.config.ConfigFindByComputerIdQuery;
import com.code42.core.CommandException;
import com.code42.core.annotation.CoreNamedQuery;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsSysadminCmd;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.DeleteQuery;
import com.code42.core.impl.DBCmd;
import com.code42.scheduler.ComputerNotification;
import com.code42.user.destination.UserDestinationDeleteByComputerCmd;

/**
 * BE VERY CAREFUL!!!
 * 
 * This command will completely delete and remove all references to a computer, its social network, settings, history,
 * archives, etc.
 * 
 * It is EXTREMELY destructive and executable only by a System Administrator.
 */
public class ComputerDeleteCmd extends DBCmd<Void> {

	private final long computerId;
	private final boolean safetyChecks;

	public ComputerDeleteCmd(long computerId) {
		this(computerId, true);
	}

	/**
	 * @param computerId
	 * @param safetyChecks - if false, don't do safety checks
	 */
	public ComputerDeleteCmd(long computerId, boolean safetyChecks) {
		this.computerId = computerId;
		this.safetyChecks = safetyChecks;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		if (this.safetyChecks) {
			this.run(new DeleteAuthCheckCmd(CheckType.COMPUTER, this.computerId), session);
		} else {
			// OK, just make sure this is a System Administrator
			this.run(new IsSysadminCmd(), session);
		}

		try {
			this.db.beginTransaction();

			this.run(new ArchiveRecordDeleteByComputerCmd(this.computerId, this.safetyChecks), session);
			this.run(new ComputerRemoveFromSocialNetworkCmd(this.computerId, this.safetyChecks), session);
			this.run(new UserDestinationDeleteByComputerCmd(this.computerId), session);

			Computer computer = this.db.find(new ComputerFindByIdQuery(this.computerId));

			if (computer != null) {
				Config config = this.db.find(new ConfigFindByComputerIdQuery(this.computerId));
				List<ComputerNotification> cns = this.db.find(new ComputerNotificationsFindByComputerQuery(this.computerId));
				if (cns != null) {
					for (ComputerNotification cn : cns) {
						this.db.delete(new ComputerNotificationDeleteQuery(cn));
					}
				}

				List<Computer> computers = this.db.find(new ComputerFindByParentIdQuery(this.computerId));
				if (computers != null) {
					for (Computer c : computers) {
						this.run(new ComputerDeleteCmd(c.getComputerId()), session);
					}
				}

				this.db.delete(new RestoreRecordDeleteByComputerQuery(computer));
				this.db.delete(new ComputerDeauthDeleteByComputerQuery(computer));
				this.db.delete(new ConfigDeleteQuery(config));
				this.db.delete(new ComputerDeleteQuery(computer));

			}

			this.db.afterTransaction(new ComputerPublishDeleteCmd(computer), session);

			this.db.commit();
			return null;
		} finally {
			this.db.endTransaction();
		}
	}

	private static class ComputerNotificationDeleteQuery extends DeleteQuery<Void> {

		private final ComputerNotification cn;

		private ComputerNotificationDeleteQuery(ComputerNotification cn) {
			this.cn = cn;
		}

		@Override
		public void query(Session session) throws DBServiceException {
			if (this.cn != null) {
				session.delete(this.cn);
			}
		}
	}

	private static class ConfigDeleteQuery extends DeleteQuery<Void> {

		private final Config config;

		private ConfigDeleteQuery(Config config) {
			this.config = config;
		}

		@Override
		public void query(Session session) throws DBServiceException {
			if (this.config != null) {
				session.delete(this.config);
			}
		}
	}

	@CoreNamedQuery(name = "RestoreRecordDeleteByComputerQuery", query = "delete from RestoreRecord r where r.sourceComputerId = :computerId")
	private static class RestoreRecordDeleteByComputerQuery extends DeleteQuery<Void> {

		private final Computer computer;

		private RestoreRecordDeleteByComputerQuery(Computer computer) {
			this.computer = computer;
		}

		@Override
		public void query(Session session) throws DBServiceException {
			if (this.computer != null) {
				final Query q = this.getNamedQuery(session);
				q.setLong("computerId", this.computer.getComputerId());
				q.executeUpdate();
			}
		}
	}

	@CoreNamedQuery(name = "ComputerDeauthDeleteByComputerQuery", query = "delete from ComputerDeauth r where r.originalGuid = :guid")
	private static class ComputerDeauthDeleteByComputerQuery extends DeleteQuery<Void> {

		private final Computer computer;

		private ComputerDeauthDeleteByComputerQuery(Computer computer) {
			this.computer = computer;
		}

		@Override
		public void query(Session session) throws DBServiceException {
			if (this.computer != null) {
				final Query q = this.getNamedQuery(session);
				q.setLong("guid", this.computer.getGuid());
				q.executeUpdate();
			}
		}
	}

	@CoreNamedQuery(name = "ComputerDeleteQuery", query = "delete from Computer c where c.computerId = :computerId")
	private static class ComputerDeleteQuery extends DeleteQuery<Void> {

		private final Computer computer;

		private ComputerDeleteQuery(Computer computer) {
			this.computer = computer;
		}

		@Override
		public void query(Session session) throws DBServiceException {
			if (this.computer != null) {
				final Query q = this.getNamedQuery(session);
				q.setLong("computerId", this.computer.getComputerId());
				q.executeUpdate();
			}
		}
	}

}
