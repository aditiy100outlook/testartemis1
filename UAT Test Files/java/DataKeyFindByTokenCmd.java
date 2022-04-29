package com.code42.auth;

import com.code42.backup.DataKey;
import com.code42.computer.DataEncryptionKey;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.core.security.ICryptoService;
import com.code42.user.DataEncryptionKeyFindByUserQuery;
import com.google.inject.Inject;
import com.google.inject.name.Named;

class DataKeyFindByTokenCmd extends DBCmd<DataKey> {

	@Inject
	private ICryptoService crypto;

	private DataKeyTokenHandler tokenHandler;

	@Inject
	public void setTokenHandler(@Named("dataKey") AutoTokenHandler tokenHandler) {
		this.tokenHandler = (DataKeyTokenHandler) tokenHandler;
	}

	private String dataKeyToken;

	DataKeyFindByTokenCmd(String dataKeyToken) {
		this.dataKeyToken = dataKeyToken;
	}

	@Override
	public DataKey exec(CoreSession session) throws CommandException {
		// No need for an auth check as providing a valid DataKeyToken is enough to give access to the DataKey

		DataKeyToken token = this.tokenHandler.handleInboundToken(this.dataKeyToken);

		DataEncryptionKey key = this.db.find(new DataEncryptionKeyFindByUserQuery(token.getUserId()));
		return this.crypto.decryptDataKey(key.getDataKey());
	}
}
