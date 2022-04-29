package com.code42.server.destination;

import com.backup42.CpcConstants;
import com.backup42.social.SocialComputerNetworkServices;
import com.backup42.social.SocialComputerNetworkServices.SourceUsage;
import com.code42.computer.Computer;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableByGuidCmd;
import com.code42.core.impl.DBCmd;
import com.code42.user.User;
import com.code42.user.UserFindByIdQuery;

/**
 * Wrapping a call to the Social Network Services to offer use of the Destination's server to the source computer
 */
public class ComputerUseDestinationCmd extends DBCmd<Void> {

	private final long sourceComputerGuid;

	private final long targetDestinationGuid;
	private Destination targetDestination;

	public ComputerUseDestinationCmd(long sourceComputerGuid, long targetDestinationGuid) {
		super();
		this.sourceComputerGuid = sourceComputerGuid;
		this.targetDestinationGuid = targetDestinationGuid;
	}

	public ComputerUseDestinationCmd(Computer source, Destination dest) {
		this(source.getGuid(), dest.getDestinationGuid());
		this.targetDestination = dest;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.run(new IsComputerManageableByGuidCmd(this.sourceComputerGuid, C42PermissionApp.Computer.UPDATE), session);

		try {
			this.db.beginTransaction();

			if (this.targetDestination == null) {
				this.targetDestination = this.db.find(new DestinationFindByGuidQuery(this.targetDestinationGuid));
			}

			final User admin = this.db.find(new UserFindByIdQuery(CpcConstants.Users.ADMIN_ID));

			final SourceUsage sourceComputerUsage = new SourceUsage(this.sourceComputerGuid);

			// Offer the destination computer, make the Source computer Use the Destination computer, adjust the Destination
			// computer's usage accordingly and finally notify any peers of the change
			SocialComputerNetworkServices.getInstance().manageOfferedComputer(admin, this.targetDestination.getComputer(),
					true, true, true, true, sourceComputerUsage);

			this.db.commit();
		} finally {
			this.db.endTransaction();
		}

		return null;
	}

}
