package com.code42.server.mount;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.RequestTooLargeException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.server.destination.Destination;
import com.code42.server.destination.DestinationFindByGuidQuery;
import com.code42.utils.SystemProperties;
import com.code42.utils.SystemProperty;

/**
 * Finds
 */
public class MountPointDtoFindByCriteriaCmd extends DBCmd<List<MountPointDto>> {

	private final MountPointDtoFindByCriteriaBuilder data;

	public MountPointDtoFindByCriteriaCmd(MountPointDtoFindByCriteriaBuilder builder) {
		this.data = builder;
	}

	@Override
	public List<MountPointDto> exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		if (!session.isSystem()) {
			this.data.excludeProvider(true);
		}

		/*
		 * This is a very rough guess on whether or not the user is attempting to sort too much data. If so, throw
		 * RequestTooLargeException.
		 */
		if (this.data.mountId == null && this.data.nodeId == null && this.data.search == null) {
			boolean checkQueryLimit = this.data.obeyQueryLimit
					&& SystemProperties.getOptionalBoolean(SystemProperty.QUERY_LIMIT, false);
			if (checkQueryLimit) {
				throw new RequestTooLargeException();
			}
		}

		// If we have the destination Guid, translate it to a destinationId for use by the Query
		if (this.data.destinationGuid != null && this.data.destinationId == null) {
			Destination destination = this.db.find(new DestinationFindByGuidQuery(this.data.destinationGuid));
			this.data.destination(destination.getDestinationId());
		}

		// Find the mounts
		List<MountPointDto> list = this.db.find(new MountPointDtoFindByCriteriaQuery(this.data));
		this.runtime.run(new MountPointDtoLoadCmd(list), session);

		// Find and add any additional information requested for each mount
		ListIterator<MountPointDto> listIter = list.listIterator();
		while (listIter.hasNext()) {
			MountPointDto dto = listIter.next();

			// Filter in/out alerted mounts
			if (this.data.alerted != null) {
				boolean alerted = (dto.getFreeBytesAlert() != null || !dto.isOnline());
				if (this.data.alerted != alerted) {
					listIter.remove();
				}
			}
		}

		// Optional in-memory sorting
		if (this.data.isRamSort()) {
			Comparator<MountPointDto> comparator = new MountPointDtoComparator(this.data.sortKey, this.data.sortDir);
			Collections.sort(list, comparator);
		}

		return list;
	}

	/**
	 * Builds the data used by the command to run the query. The superclass is shared with the query.
	 */
	public static class Builder extends MountPointDtoFindByCriteriaBuilder<Builder, MountPointDtoFindByCriteriaCmd> {

		@Override
		public MountPointDtoFindByCriteriaCmd build() {
			this.validate();
			return new MountPointDtoFindByCriteriaCmd(this);
		}
	}

}
