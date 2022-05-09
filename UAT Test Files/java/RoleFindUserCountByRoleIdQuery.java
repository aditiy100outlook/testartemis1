package com.code42.auth;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.Hibernate;
import org.hibernate.HibernateException;
import org.hibernate.SQLQuery;
import org.hibernate.Session;

import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.executor.jsr166.Arrays;
import com.code42.utils.LangUtils;

public class RoleFindUserCountByRoleIdQuery extends FindQuery<Map<Integer, Integer>> {

	private Collection<Integer> roleIds;

	public RoleFindUserCountByRoleIdQuery(int roleId) {
		this(Arrays.asList(new Integer[] { roleId }));
	}

	public RoleFindUserCountByRoleIdQuery(Collection<Integer> roleIds) {
		this.roleIds = roleIds;
	}

	@Override
	public Map<Integer, Integer> query(Session session) throws DBServiceException {
		try {
			Map<Integer, Integer> counts = new HashMap<Integer, Integer>();
			if (!this.roleIds.isEmpty()) {
				String sql = "SELECT  ROLE_ID as roleId, COUNT(*) as count FROM T_USER_ROLE WHERE ROLE_ID IN ( :roleIds ) GROUP BY ROLE_ID";
				SQLQuery query = null;

				query = session.createSQLQuery(sql);
				query.setParameterList("roleIds", this.roleIds, Hibernate.INTEGER);
				query.addScalar("roleId", Hibernate.INTEGER);
				query.addScalar("count", Hibernate.INTEGER);

				List<Object[]> results = query.list();

				for (Object[] role : results) {
					if (role != null) {
						counts.put((Integer) role[0], (Integer) role[1]);
					}
				}
			}

			return counts;
		} catch (HibernateException e) {
			throw new DBServiceException("Error Finding UserRole(s); userIds=" + LangUtils.toString(this.roleIds));
		}
	}
}
