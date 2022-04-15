package com.code42.balance;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hibernate.Session;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.balance.BalanceActivityDto.Status;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;
import com.code42.sql.SQLQuery;

/**
 * Find ONLY historical balancer activity.
 */
public class BalanceActivityDtoFindHistoryByDestinationCmd extends DBCmd<List<BalanceActivityDto>> {

	private final Integer destinationId;
	private final Integer limit;
	private final Integer offset;

	public BalanceActivityDtoFindHistoryByDestinationCmd(Integer destinationId, Integer limit, Integer offset) {
		super();
		this.destinationId = destinationId;
		this.limit = limit;
		this.offset = offset;
	}

	public BalanceActivityDtoFindHistoryByDestinationCmd(Integer destinationId) {
		this(destinationId, null, null);
	}

	@Override
	public List<BalanceActivityDto> exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		BalanceActivityDtoFindHistoryByCriteriaQuery.Builder b = new BalanceActivityDtoFindHistoryByCriteriaQuery.Builder();
		b.destination(this.destinationId);
		b.limit(this.limit);
		b.offset(this.offset);

		final List<BalanceActivityDto> dtos = this.db.find(new BalanceActivityDtoFindHistoryByCriteriaQuery(b));
		return dtos;
	}

	/**
	 * Manual sql for pulling DBH history simultaneously with entity names.
	 */
	private static class BalanceActivityDtoFindHistoryByCriteriaQuery extends FindQuery<List<BalanceActivityDto>> {

		private static final String SQL = ""
				+ "                     select bh.src_computer_guid, bh.archive_bytes, bh.success, bh.start_date, bh.end_date,  \n"
				+ "                            srcMount.mount_point_id as sMountId, srcMount.name as sName,                     \n"
				+ "                            srcMount.server_id as sServerId, srcServerComp.name as sServerName,              \n"
				+ "                            tgtMount.mount_point_id as tMountId, tgtMount.name as tName,                     \n"
				+ "                            tgtMount.server_id as tServerId, tgtServerComp.name as tServerName               \n"
				+ "                     from t_balance_history bh                                                               \n"
				// source server info
				+ "                     join t_mount_point srcMount on (srcMount.mount_point_id = bh.src_mount_id)              \n"
				+ "                     join t_server srcServer on (srcServer.server_id = srcMount.server_id)                   \n"
				+ "                     join t_computer srcServerComp on (srcServerComp.computer_id = srcServer.computer_id)    \n"
				// target server info
				+ "                     join t_mount_point tgtMount on (tgtMount.mount_point_id = bh.tgt_mount_id)              \n"
				+ "                     join t_server tgtServer on (tgtServer.server_id = tgtMount.server_id)                   \n"
				+ "                     join t_computer tgtServerComp on (tgtServerComp.computer_id = tgtServer.computer_id)    \n"
				+ "--findByDestination  where srcServer.destination_server_id = :destinationId                                  \n"
				+ "                     order by bh.balance_history_id desc                                                     \n"
				+ "--limit              limit :limit                                                                            \n"
				+ "--offset             offset :offset                                                                          \n";

		private final Builder b;

		public BalanceActivityDtoFindHistoryByCriteriaQuery(Builder b) {
			super();
			this.b = b;
		}

		@Override
		public List<BalanceActivityDto> query(Session session) throws DBServiceException {

			final SQLQuery q = new SQLQuery(session, SQL);

			if (this.b.destinationId != null) {
				q.activate("--findByDestination");
				q.setInteger("destinationId", this.b.destinationId);
			}

			if (this.b.limit != null) {
				q.activate("--limit");
				q.setInteger("limit", this.b.limit);
			}

			if (this.b.offset != null) {
				if (this.b.limit == null) {
					throw new RuntimeException("Offset must be combined with a limit");
				}
				q.activate("--offset");
				q.setInteger("offset", this.b.offset);
			}

			final List<Object[]> rows = q.list();

			final List<BalanceActivityDto> dtos = new ArrayList(rows.size());
			for (Object[] row : rows) {

				final BalanceActivityDto dto = new BalanceActivityDto();

				dto.setGuid(((BigInteger) row[0]).longValue());
				dto.setArchiveBytes(((BigInteger) row[1]).longValue());
				final boolean success = (Boolean) row[2];
				dto.setStatus(success ? Status.COMPLETED : Status.FAILED);
				dto.setStartDate((Date) row[3]);
				dto.setEndDate((Date) row[4]);

				dto.setSrcMountId((Integer) row[5]);
				dto.setSrcMountName((String) row[6]);
				dto.setSrcNodeId((Integer) row[7]);
				dto.setSrcNodeName((String) row[8]);

				dto.setTgtMountId((Integer) row[9]);
				dto.setTgtMountName((String) row[10]);
				dto.setTgtNodeId((Integer) row[11]);
				dto.setTgtNodeName((String) row[12]);

				dtos.add(dto);
			}

			return dtos;
		}

		public static class Builder {

			Integer destinationId;
			Integer limit;
			Integer offset;

			public Builder destination(int destinationId) {
				this.destinationId = destinationId;
				return this;
			}

			public Builder limit(Integer limit) {
				this.limit = limit;
				return this;
			}

			public Builder offset(Integer offset) {
				this.offset = offset;
				return this;
			}
		}
	}
}
