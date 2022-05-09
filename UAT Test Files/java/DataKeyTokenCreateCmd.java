package com.code42.auth;

import com.code42.computer.ComputerSso;
import com.code42.computer.ComputerSsoFindByGuidCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.google.inject.Inject;
import com.google.inject.name.Named;

public class DataKeyTokenCreateCmd extends AbstractCmd<String> {

	private static final Logger log = LoggerFactory.getLogger(DataKeyTokenCreateCmd.class);

	private DataKeyTokenHandler tokenHandler;

	@Inject
	public void setTokenHandler(@Named("dataKey") AutoTokenHandler tokenHandler) {
		this.tokenHandler = (DataKeyTokenHandler) tokenHandler;
	}

	private final long computerGuid;

	public DataKeyTokenCreateCmd(long computerGuid) {
		this.computerGuid = computerGuid;
	}

	@Override
	public String exec(CoreSession session) throws CommandException {
		if (!this.env.isMaster()) {
			throw new CommandException("Only a master can create DataKeyTokens");
		}

		ComputerSso c = this.run(new ComputerSsoFindByGuidCmd(this.computerGuid), session);

		this.runtime.run(new IsComputerManageableCmd(c.getComputerId(), C42PermissionApp.Computer.READ), session);

		DataKeyToken token = new DataKeyToken(c.getUserId());
		String encryptedToken = this.tokenHandler.handleOutboundToken(token);
		log.info("Issuing DataKeyToken; userId={}, tt={}", c.getUserId(), encryptedToken);
		return encryptedToken;
	}
}