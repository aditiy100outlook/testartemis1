package com.code42.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import com.backup42.common.SystemUpgradeUtils.InvalidUpgradeFileException;
import com.backup42.common.perm.C42PermissionPro;
import com.backup42.common.upgrade.UpgradeProperty;
import com.backup42.common.util.SystemUtil;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.exception.DebugRuntimeException;
import com.code42.io.IOUtil;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.os.posix.PosixFileCommands;
import com.code42.server.ServerUpgradedSetStatusCmd;
import com.code42.server.license.ServerLicenseFactory;
import com.code42.utils.Os;
import com.code42.utils.PropertiesUtil;
import com.code42.utils.SystemProperties;

/**
 * Run an already prepared upgrade. The UpgradePrepareCmd must have been run previously to setup the upgrade directory.
 */
public class NodeUpgradeCmd extends AbstractCmd<Void> {

	private static final Logger log = LoggerFactory.getLogger(NodeUpgradeCmd.class);

	public static enum Error {
		ALREADY_IN_PROGRESS, INVALID_FILE, NO_SUPPORT
	}

	private static AtomicBoolean started = new AtomicBoolean(false);

	private String upgradePath;

	/**
	 * @param upgradePath- file system path to the extracted upgrade.
	 */
	public NodeUpgradeCmd(String upgradePath) {
		super();
		this.upgradePath = upgradePath;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {
		if (!started.compareAndSet(false, true)) { // If we can't set the started flag, another thread already has
			throw new CommandException(Error.ALREADY_IN_PROGRESS, "Upgrade:: An upgrade is already in progress");
		}

		try {
			log.info("Upgrade:: Starting upgrade of {}", this.upgradePath);

			this.auth.isAuthorized(session, C42PermissionPro.System.MANAGE_DATABASE);
			if (!ServerLicenseFactory.getInstance().isSupported()) {
				throw new CommandException(Error.NO_SUPPORT, "You cannot upgrade without a license that has support");
			}

			// If dev, pretend it upgraded successfully
			if (SystemProperties.isDevEnv()) {
				this.run(new ServerUpgradedSetStatusCmd(true), session);
				log.warn("Upgrade:: Development environment... upgrade process pretending to be successfull but didn't actually happen.");
			}

			// Precondition: upgrade was extracted to upgradePath by NodeUpgradePrepareCmd
			File upgradeDir = this.upgradePath2FileObj(this.upgradePath);

			final String shellCommand = readProperties(upgradeDir).getRequired(UpgradeProperty.COMMAND);
			invokeAndExit(shellCommand, upgradeDir);
			// On success, 'started' remains true to prevent later attempts at upgrading before the server shuts down
		} catch (Exception e) {
			log.error("Upgrade:: Failed to execute upgrade", e);
			started.set(false);
			throw this.wrapOrCast(e);
		}
		return null;
	}

	private CommandException wrapOrCast(Exception e) {
		return e instanceof CommandException ? (CommandException) e : new CommandException(e);
	}

	private File upgradePath2FileObj(String upgradePath) throws CommandException {
		File result = new File(upgradePath);
		if (!result.exists() || !result.canRead() || !result.isDirectory()) {
			throw new CommandException(Error.INVALID_FILE, "Upgrade:: Invalid update directory {}", upgradePath);
		}
		return result;
	}

	/**
	 * Read the upgrade properties file.
	 * 
	 * @param dir the directory where the properties file should exist
	 * @return the upgrade properties
	 * @throws InvalidUpgradeFileException unable to read properties file
	 */
	private static PropertiesUtil readProperties(File dir) throws InvalidUpgradeFileException {
		final Properties upgradeProps = new Properties();
		{
			final File upgradePropsFile = new File(dir, "upgrade.properties");
			log.info("  Upgrade:: reading " + upgradePropsFile);
			InputStream in = null;
			try {
				in = new FileInputStream(upgradePropsFile);
				upgradeProps.load(in);
				return new PropertiesUtil(upgradeProps);
			} catch (Throwable e) {
				final InvalidUpgradeFileException fe = new InvalidUpgradeFileException("Unable to read properties, file="
						+ upgradePropsFile.getAbsolutePath(), e);
				log.warn("Upgrade:: " + fe.toString(), fe);
				throw fe;
			} finally {
				IOUtil.close(in);
			}
		}
	}

	// TODO: Rich Apr 8, 2013: Should we bite of this refactoring?
	/**
	 * TODO: Some or all of this should be replaced by ExecuteShellCommandCmd. That command takes a slightly different
	 * approach to command execution: it doesn't immediately set commands to be executable (it fails with an exception if
	 * the command isn't executable) and it doesn't automatically create an exit thread. Instead it returns a Future so
	 * that the caller can take whatever action it wants if the wrong result is returned (or if a result isn't returned in
	 * time).
	 * 
	 * The philosophy behind ExecuteShellCommandCmd is more flexible; it provides a simpler common set of functionality
	 * and returns enough information to allow the caller to take actions if various results/errors occur. Most of the
	 * invocations of this method should probably do the same thing. If, for example, it's required that we mark something
	 * as executable before actually executing it the caller should do that _before_ asking the system to execute the
	 * script in question.
	 * 
	 * @param props
	 */
	private static void invokeAndExit(String command, File directory) {
		log.info("  Upgrade:: running command, cmd=" + command);
		if (!SystemProperties.isOs(Os.Windows)) { // Make command executable
			File cmdFile = new File(directory + "/" + command);
			log.debug("  Upgrade:: making script executable, " + cmdFile);
			PosixFileCommands.chmod(cmdFile, 0777);// octal
		}

		// execute the command asynchronously
		final long startTime = System.currentTimeMillis();
		boolean success = false;
		// Don't actually execute the command in dev. Pretend it worked.
		if (!SystemProperties.isDevEnv()) {
			success = SystemUtil.executeCommand(command, directory, true);
		} else {
			success = true; // Pretend it happened in dev.
		}
		if (success) {
			log.info("  Upgrade:: process has been started successfully, cmd='" + command + "' milliseconds: "
					+ (System.currentTimeMillis() - startTime));

			// Exit the system in 1s, we need to give the server time to respond to the upgrade request.
			// Important: the upgrade script needs to include a delay to give the system a chance to shutdown.
			new ExitThread(1000).start();
		} else {
			final DebugRuntimeException e = new DebugRuntimeException("Unable to run upgrade command, cmd=" + command,
					new Object[] { "command=" + command, directory });
			log.warn("Upgrade:: " + e.toString(), e);
			throw e;
		}
	}

	/**
	 * Async shutdown of the server.
	 */
	private static class ExitThread extends Thread {

		private final long delay;

		private ExitThread(long delay) {
			super("ExitThread");
			this.delay = delay;
		}

		@Override
		public void run() {
			try {
				log.info("Upgrade:: SHUTDOWN in " + this.delay + "ms.");
				Thread.sleep(this.delay);
				System.exit(0);
			} catch (InterruptedException e) {
			}
		}
	}
}
