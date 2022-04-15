package com.code42.balance;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hibernate.Session;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.balance.BalanceCommandDto.Status;
import com.code42.balance.BalanceCommandDto.Type;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.db.DBServiceException;
import com.code42.core.db.impl.FindQuery;
import com.code42.core.impl.DBCmd;
import com.code42.server.mount.MountPoint;
import com.code42.sql.SQLQuery;

public class BalanceCommandDtoFindByMountCmd extends DBCmd<List<BalanceCommandDto>> {

	private final Builder b;

	public BalanceCommandDtoFindByMountCmd(Builder b) {
		super();
		this.b = b;
	}

	@Override
	public List<BalanceCommandDto> exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		final List<BalanceCommandDto> dtos = this.db.find(new BalanceCommandDtoFindByCriteriaQuery(this.b));
		return dtos;
	}

	/**
	 * The usual- joins are needed to get the src/tgt mount details.
	 */
	private static class BalanceCommandDtoFindByCriteriaQuery extends FindQuery<List<BalanceCommandDto>> {

		private static final String SQL = ""
				+ "                  select dbc.data_balance_command_id, dbc.guid, dbc.active, dbc.cancelled,                                                            \n"
				+ "                    dbc.completed_date, dbc.cancelled_date, dbc.creation_date, dbc.discriminator,                                                     \n"
				+ "                    srcMount.mount_point_id as sMountId, srcMount.name as sName, srcMount.server_id as sServerId, srcServerComp.name as sServerName,  \n"
				+ "                    tgtMount.mount_point_id as tMountId, tgtMount.name as tName, tgtMount.server_id as tServerId, tgtServerComp.name as tServerName   \n"
				+ "                  from t_data_balance_command dbc                                                                                                     \n"
				// source server info
				+ "                  join t_mount_point srcMount on (srcMount.mount_point_id = dbc.mount_point_id)                                                       \n"
				+ "                  join t_server srcServer on (srcServer.server_id = srcMount.server_id)                                                               \n"
				+ "                  join t_computer srcServerComp on (srcServerComp.computer_id = srcServer.computer_id)                                                \n"
				// target server info
				+ "                  left outer join t_mount_point tgtMount on (tgtMount.mount_point_id = dbc.destination_mount_point_id)                                \n"
				+ "                  left outer join t_server tgtServer on (tgtServer.server_id = tgtMount.server_id)                                                    \n"
				+ "                  left outer join t_computer tgtServerComp on (tgtServerComp.computer_id = tgtServer.computer_id)                                     \n"
				+ "--findBySrcMount  where dbc.mount_point_id = :srcMountId                                                                                              \n"
				+ "                  order by dbc.data_balance_command_id                                                                                                \n"
				+ "--limit           limit :limit                                                                                                                        \n"
				+ "--offset          offset :offset                                                                                                                      \n";

		private final Builder b;

		public BalanceCommandDtoFindByCriteriaQuery(Builder b) {
			super();
			this.b = b;
		}

		@Override
		public List<BalanceCommandDto> query(Session session) throws DBServiceException {

			final SQLQuery q = new SQLQuery(session, SQL);

			if (this.b.mountId != null) {
				q.activate("--findBySrcMount");
				q.setInteger("srcMountId", this.b.mountId);
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

			final List<BalanceCommandDto> dtos = new ArrayList(rows.size());
			for (Object[] row : rows) {

				final int dataBalanceCommandId = (Integer) row[0];
				final Long guid = row[1] != null ? ((BigInteger) row[1]).longValue() : null;
				final boolean active = (Boolean) row[2];
				final boolean cancelled = (Boolean) row[3];
				final Date completedDate = (Date) row[4];
				final Date cancelledDate = (Date) row[5];
				final Date creationDate = (Date) row[6];
				final String discriminator = (String) row[7];

				final int srcMountId = (Integer) row[8];
				final String srcMountName = (String) row[9];
				final int srcNodeId = (Integer) row[10];
				final String srcNodeName = (String) row[11];

				final Integer tgtMountId = (Integer) row[12];
				final String tgtMountName = (String) row[13];
				final Integer tgtNodeId = (Integer) row[14];
				final String tgtNodeName = (String) row[15];

				final BalanceCommandDto dto = new BalanceCommandDto();

				dto.setDataBalanceCommandId(dataBalanceCommandId);
				dto.setCreationDate(creationDate);
				dto.setEndDate(completedDate);

				// massage the state
				if (cancelled) {
					dto.setStatus(Status.CANCELLED);
					dto.setEndDate(cancelledDate); // override
				} else if (!active) {
					dto.setStatus(Status.COMPLETED);
				}

				// type-specific fields
				if ("EMPTY_TO_CLUSTER".equals(discriminator)) {
					dto.setType(Type.EMPTY_MOUNT_TO_DESTINATION);

				} else if ("EMPTY_TO_MOUNT".equals(discriminator)) {
					dto.setType(Type.EMPTY_MOUNT_TO_MOUNT);

				} else { // MoveGuidToMount
					dto.setType(Type.ARCHIVE_MOVE);
					dto.setGuid(guid);
				}

				// source info
				dto.setSrcMountId(srcMountId);
				dto.setSrcMountName(srcMountName);
				dto.setSrcNodeId(srcNodeId);
				dto.setSrcNodeName(srcNodeName);

				// target info
				dto.setTgtMountId(tgtMountId);
				dto.setTgtMountName(tgtMountName);
				dto.setTgtNodeId(tgtNodeId);
				dto.setTgtNodeName(tgtNodeName);

				dtos.add(dto);
			}

			return dtos;
		}
	}

	public static class Builder {

		Integer mountId;
		Integer limit;
		Integer offset;

		public Builder mount(int mountId) {
			this.mountId = mountId;
			return this;
		}

		public Builder mount(MountPoint mount) {
			this.mount(mount.getMountPointId());
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

	/**
	 * Not supported at this time.
	 */
	static class BalanceCommandDtoFindByDestinationCmd extends DBCmd<List<BalanceCommandDto>> {

		@Override
		public List<BalanceCommandDto> exec(CoreSession session) throws CommandException {
			throw new RuntimeException("Commands by Destination is not supported");
		}
	}

	/**
	 * Not supported at this time.
	 */
	static class BalanceCommandDtoFindByServerCmd extends DBCmd<List<BalanceCommandDto>> {

		@Override
		public List<BalanceCommandDto> exec(CoreSession session) throws CommandException {
			throw new RuntimeException("Commands by Server is not supported");
		}
	}
}
