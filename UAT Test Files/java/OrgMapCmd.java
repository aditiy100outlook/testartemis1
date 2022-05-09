package com.code42.org;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.hibernate.Query;
import org.hibernate.Session;

import com.code42.core.CommandException;
import com.code42.core.IEnvironment;
import com.code42.core.annotation.CoreNamedQuery;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;
import com.code42.server.mount.MountPoint;
import com.code42.server.mount.MountPointFindByServerCmd;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

/**
 * Apply a function to every org in the system, returning the results. <br>
 * <br>
 * This command is designed to be as lazy as possible. We never instantiate all orgs in the system, although we are
 * forced to keep a reference to the results of applying the function to each org. If we were to make this as lazy as
 * possible we'd delay even that, applying the function (and returning the result) only when absolutely necessary.
 * Unfortunately this is a bit tricky to do when dealing with persistent entities managed by Hibernate... so we split
 * the difference here by maintaining references to the (presumably much smaller) function return values rather than the
 * (presumably much larger) Org object itself. <br>
 * <br>
 * The implementation has shifted a bit. Previously the DB query was returning a minimal set of information and the
 * function was being applied within the command. In order to conserve DB ops we're now treating this as a
 * "decorated query"; the input Function decorates query results, transforming them into something else.
 * 
 * @type RT return type of the map function
 * 
 * @author bmcguire
 */
public class OrgMapCmd<RT> extends DBCmd<List<RT>> {

	/* ================= Dependencies ================= */
	private IEnvironment env;

	/* ================= DI injection points ================= */
	@Inject
	public void setEnv(IEnvironment env) {
		this.env = env;
	}

	private final Function<Org, RT> fn;
	private final int windowSize;

	public OrgMapCmd(Function<Org, RT> fn, int windowSize) {

		this.fn = fn;
		this.windowSize = windowSize;
	}

	@Override
	public List<RT> exec(CoreSession session) throws CommandException {

		/* Only sysadmins get to see every org */
		this.auth.isSysadmin(session);

		return ImmutableList.copyOf(this.db.find(new OrgMapQuery(session)));
	}

	@CoreNamedQuery(name = "findAllLocalOrgs", query = "select o from Org o, User u, Computer c, FriendComputerUsage fcu where o.orgId = u.orgId and u.userId = c.userId and c.computerId = fcu.sourceComputerId and fcu.mountPointId in :mpids")
	private class OrgMapQuery extends FindQuery<List<RT>> {

		private CoreSession session;

		public OrgMapQuery(CoreSession session) {

			this.session = session;
		}

		@Override
		public List<RT> query(Session session) throws DBServiceException {

			LinkedList<RT> rv = new LinkedList<RT>();

			int first = 0;
			Collection<RT> buffer = new LinkedList<RT>();

			Query query = this.getNamedQuery(session);
			try {

				Collection<Integer> mpids = Collections2.transform(OrgMapCmd.this.runtime.run(new MountPointFindByServerCmd(
						OrgMapCmd.this.env.getMyNodeId()), this.session), new Function<MountPoint, Integer>() {

					public Integer apply(MountPoint arg) {
						return arg.getMountPointId();
					}
				});
				query.setParameterList("mpids", mpids);
			} catch (CommandException ce) {

				throw new DBServiceException("Exception finding mount point IDs", ce);
			}
			query.setMaxResults(OrgMapCmd.this.windowSize);

			do {

				buffer.clear();
				query.setFirstResult(first);
				buffer.addAll(Collections2.transform(query.list(), OrgMapCmd.this.fn));
				rv.addAll(buffer);
				first += OrgMapCmd.this.windowSize;
			} while (buffer.size() > 0);

			return rv;
		}
	}
}
