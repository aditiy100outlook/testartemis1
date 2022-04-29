package com.code42.exec;

import java.io.File;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.config.impl.HibernateConfigurationFactory;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;
import com.code42.utils.Os;

/**
 * Command to encapsulate the logic of triggering a server restart
 */
public class RestartCoreExecuteCmd extends AbstractCmd<Void> {

	private static final Logger log = Logger.getLogger(RestartCoreExecuteCmd.class);
	private final String reason;

	public RestartCoreExecuteCmd() {
		this(null); // no known reason
	}

	public RestartCoreExecuteCmd(String reason) {
		super();
		this.reason = reason;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		/* This is one of those things you should really only be doing if you're an admin */
		this.auth.isAuthorized(session, C42PermissionPro.System.SYSTEM_SETTINGS);

		// The real script will ignore the additional argument; in dev, the port info is used by the dev script to determine
		// which instance of the server to kill
		Integer port = this.config.get(HibernateConfigurationFactory.Keys.h2WebPort, Integer.class, 4284);

		log.warn("RESTARTING SYSTEM:: port: {}, reason: {}", port, this.reason);

		File libexec = new File("libexec");
		if (!libexec.exists()) {
			throw new CommandException("Could not find libexec directory");
		}
		if (!libexec.isDirectory()) {
			throw new CommandException("libexec exists but is not a directory");
		}
		String script = this.getScriptName() + " " + port;
		this.runtime.runAsync(new ExecuteShellCommandCmd(script, libexec), session);

		return null;
	}

	/*
	 * This used to be defined in system props... now that we have the command pattern and the ability to determine
	 * platforms programmatically there's really no need to do so.
	 */
	private String getScriptName() {

		return (this.env.getOs() == Os.Windows) ? " restart.bat" : "restart.sh";
	}
}
