package com.code42.server;

import java.net.URL;

import com.backup42.server.data.ext.WebsiteHostFormatter;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;

public class ServerFindWebsiteHostByServerIdCmd extends DBCmd<URL> {

	private int serverId;

	public ServerFindWebsiteHostByServerIdCmd(int serverId) {
		super();
		this.serverId = serverId;
	}

	@Override
	public URL exec(CoreSession session) throws CommandException {
		// NO AUTH: Anyone needs to be able to get the website host for any server for web restore even regular users.

		final BaseServer server = this.db.find(new BaseServerFindByIdQuery(this.serverId));

		// legacy servers may have an incomplete entry for their website host. just in case we'll pass our existing value
		// through the formatter (probably for the second time) just to make sure the value is up to par
		final String host = server.getWebsiteHost();
		final URL url = WebsiteHostFormatter.getWebsiteHostAsURL(host);

		return url;
	}
}
