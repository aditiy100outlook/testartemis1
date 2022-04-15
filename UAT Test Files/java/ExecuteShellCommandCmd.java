package com.code42.exec;

import java.io.File;

import com.backup42.common.util.SystemUtil;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;

/**
 * Command for executing a shell command, returning a boolean indicating whether the command successfully executed or
 * not <br>
 * <br>
 * Note that this command is NOT asynchronous. If you want asynchronous behaviour it's not this commands job to provide
 * that for you; you should do it yourself by calling ICoreRuntime.runAsync().
 * 
 * @author bmcguire
 */
public class ExecuteShellCommandCmd extends AbstractCmd<Boolean> {

	private static final Logger log = LoggerFactory.getLogger(ExecuteShellCommandCmd.class);

	private final String cmd;
	private final File dir;

	/*
	 * Member vlaue set when we're asked to execute a command with no flags or parameters. We can do a little bit more
	 * validation in this case.
	 */
	private final File cmdFile;

	public ExecuteShellCommandCmd(String cmd, String dir) {

		this.cmd = cmd;
		this.cmdFile = null;
		this.dir = new File(dir);
	}

	public ExecuteShellCommandCmd(String cmd, File dir) {

		this.cmd = cmd;
		this.cmdFile = null;
		this.dir = dir;
	}

	/* Execute the specified command with no command-line arguments */
	public ExecuteShellCommandCmd(File cmd, File dir) {

		this.cmd = cmd.getName();
		this.cmdFile = cmd;
		this.dir = dir;
	}

	@Override
	public Boolean exec(CoreSession session) throws CommandException {

		log.info("EXEC:: Executing command " + this.cmd + " in working directory " + this.dir);

		if (!this.dir.exists()) {
			CommandException e = new CommandException("Input directory " + this.dir.getAbsolutePath() + " does not exist!");
			log.warn("EXEC:: ", e);
			throw e;
		}

		if (!this.dir.isDirectory()) {
			CommandException e = new CommandException("Input directory " + this.dir.getAbsolutePath()
					+ " is not a directory!");
			log.warn("EXEC:: ", e);
			throw e;
		}

		if (this.cmdFile != null) {

			if (!this.cmdFile.exists()) {
				CommandException e = new CommandException("Input command " + this.cmdFile.getAbsolutePath()
						+ " does not exist!");
				log.warn("EXEC:: ", e);
				throw e;
			}

			if (!this.cmdFile.isFile()) {
				CommandException e = new CommandException("Input command " + this.cmdFile.getAbsolutePath() + " is not a file!");
				log.warn("EXEC:: ", e);
				throw e;
			}

			// if (!this.cmdFile.canExecute()) {
			// throw new CommandException("Input command " + this.cmdFile.getAbsolutePath() + " cannot be executed!");
			// }
		}

		/* If you want asynchronous behaviour you should call this with ICoreRuntime.runAsync() */
		log.info("EXEC:: Calling SystemUtil.executeCommand() cmd={}, dir={}", this.cmd, this.dir);
		return SystemUtil.executeCommand(this.cmd, this.dir, false, false);
	}
}
