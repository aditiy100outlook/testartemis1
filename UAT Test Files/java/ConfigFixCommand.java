package com.code42.config;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.backup42.app.command.ACentralCommand;
import com.backup42.common.CPErrors;
import com.backup42.common.IBackupApp;
import com.backup42.common.command.CommandResult;
import com.backup42.common.command.CommandUsageException;
import com.code42.core.impl.CoreBridge;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;

/**
 * Runs a command (or more someday) that will run through configs and fix them.
 */
public class ConfigFixCommand extends ACentralCommand {

	private static Logger log = LoggerFactory.getLogger(ConfigFixCommand.class);

	@Override
	public void init(IBackupApp app) {
	}

	public void execute(CommandResult result, String... args) throws Exception {
		if (args.length != 2) {
			throw new CommandUsageException();
		}

		boolean simulate = true;
		boolean destinations = false;

		// The first argument must be "fix" or "simulate"
		if (args[0].equalsIgnoreCase("fix")) {
			simulate = false;
		} else if (!args[0].equalsIgnoreCase("simulate")) {
			throw new CommandUsageException();
		}

		// The second argument must be "destinations" (or similar)
		// It is probably that more config fix options will be added
		if (args[1].equalsIgnoreCase("all")) {
			destinations = true;
		} else if (args[1].toLowerCase().startsWith("dest")) {
			destinations = true;
		} else {
			throw new CommandUsageException();
		}

		if (destinations) {

			Future<Boolean> future = CoreBridge.runAsync(new ConfigFixDestinationsCmd(simulate));
			try {
				Boolean startedSuccessfully = future.get(1, TimeUnit.SECONDS);
				if (!startedSuccessfully) {
					result.setError(CPErrors.Command.ALREADY_RUNNING);
					return;
				}
			} catch (TimeoutException te) {
				// If we timed out, it must be running OK
			}
			result.setError(CPErrors.Command.SUBMITTED);
		} else {
			assert false; // This should never happen
		}

		log.info("Manually started config fix job");
	}
}
