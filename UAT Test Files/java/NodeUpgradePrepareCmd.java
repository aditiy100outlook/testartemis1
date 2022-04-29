package com.code42.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import com.backup42.common.CPVersion;
import com.backup42.common.SystemUpgradeUtils.InvalidUpgradeFileException;
import com.backup42.common.perm.C42PermissionPro;
import com.backup42.common.upgrade.UpgradeProperty;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.core.server.IServerUpgradeService;
import com.code42.crypto.MD5Value;
import com.code42.io.FileUtility;
import com.code42.io.IOUtil;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.server.license.ServerLicenseFactory;
import com.code42.util.NodeUpgradePrepareCmd.UpgradeMetadataDto;
import com.code42.utils.PropertiesUtil;
import com.code42.utils.SystemProperties;
import com.code42.utils.SystemProperty;
import com.code42.utils.Time;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.inject.Inject;

/**
 * This command handles acquiring the upgrade file, unpacking it and doing some sanity checks. After this command has
 * been run successfully, the NodeUpgradeCmd can be called with the corresponding upgradePath to proceed with the
 * update.
 * 
 * @author ahelgeso
 */
public class NodeUpgradePrepareCmd extends AbstractCmd<UpgradeMetadataDto> {

	private static final Logger log = LoggerFactory.getLogger(NodeUpgradePrepareCmd.class);

	private static final String UPGRADE_FILE = "upgrade.properties";
	private static final String EULA_FILE = "upgrade_EULA.txt";

	public static enum Error {
		INVALID_VERSION, INVALID_FILE, NO_SUPPORT, EULA_ERROR
	}

	private static final long TIMEOUT_MS = Time.MINUTE * 30;

	@Inject
	private IServerUpgradeService sus;

	private final File fileData;
	private final String fileName;
	private final MD5Value fileHash;

	/**
	 * @param fileData - Input file
	 * @param fileName - Output file
	 */
	public NodeUpgradePrepareCmd(File fileData, String fileName) {
		super();
		this.fileData = fileData;
		this.fileName = fileName;
		this.fileHash = null;
	}

	public NodeUpgradePrepareCmd(String fileName, MD5Value fileHash) {
		this.fileData = null;
		this.fileName = fileName;
		this.fileHash = fileHash;
	}

	@Override
	public UpgradeMetadataDto exec(CoreSession session) throws CommandException {
		log.info("Upgrade:: Preparing upgrade package");

		this.auth.isAuthorized(session, C42PermissionPro.System.MANAGE_DATABASE);
		if (!ServerLicenseFactory.getInstance().isSupported()) {
			throw new CommandException(Error.NO_SUPPORT, "You cannot upgrade without a license that has support");
		}

		String baseUpgradePath = SystemProperties.getRequired(SystemProperty.UPGRADE_DIR);
		File upgradeFile = this.getUpgradeFile(baseUpgradePath, this.fileName);

		UpgradeMetadataDto result = this.unpackUpgrade(baseUpgradePath, upgradeFile);

		this.recordUpgrade(baseUpgradePath, result.getToVersion());

		return result;
	}

	private UpgradeMetadataDto unpackUpgrade(String baseUpgradePath, File upgradeArchive) throws CommandException {
		log.info("Upgrade:: UNPACKING UPGRADE- " + upgradeArchive.getName());

		// Extract the given file into the upgrade directory.
		final long timestamp = Time.getNowInMillis();
		final File extractedUpgradeDir = this.extractFile(baseUpgradePath, upgradeArchive, timestamp);

		// Read version properties
		final PropertiesUtil props = this.readProperties(extractedUpgradeDir);
		final long fromVersion = props.getRequiredLong(UpgradeProperty.FROM_VERSION);
		final long toVersion = props.getRequiredLong(UpgradeProperty.TO_VERSION);

		// Verify versions
		if (fromVersion != CPVersion.getVersion()) {
			log.info("fromVersion: " + fromVersion + "; CPVersion: " + CPVersion.getVersion());

			if (SystemProperties.isDevEnv()) {
				log.warn("Upgrade:: Permitting upgrade in dev env despite unexpected from-version found in upgrade archive:"
						+ " found {}, expected {}", fromVersion, CPVersion.getVersion());
			} else {
				log.error("Upgrade:: The given upgrade file is for an old version. Terminating this upgrade.");
				throw new CommandException(Error.INVALID_VERSION, "The given upgrade file (" + this.fileName
						+ ") is for an old version.");
			}
		}

		final String upgradePath = extractedUpgradeDir.getAbsolutePath();
		final String eula = this.getUpgradeEula(upgradePath);

		return new UpgradeMetadataDto(upgradePath, fromVersion, toVersion, eula);
	}

	/**
	 * Extract the given file into the upgrade directory.
	 * 
	 * @return the directory that everything was extracted into
	 * @throws InvalidUpgradeFileException
	 */
	private File extractFile(String baseUpgradePath, File file, long timestamp) throws CommandException {
		try {
			final File upgradeDirFile = new File(baseUpgradePath + "/" + timestamp);
			FileUtility.mkdirs(upgradeDirFile);
			log.info("  Upgrade:: extracting into " + upgradeDirFile.getAbsolutePath());
			FileUtility.unzip(file, upgradeDirFile);
			return upgradeDirFile;
		} catch (final IOException e) {
			CommandException ce = new CommandException(Error.INVALID_FILE, "Unable to extract upgrade file, file="
					+ file.getAbsolutePath(), e);
			log.warn("Upgrade:: " + ce.toString(), ce);
			throw ce;
		}
	}

	/**
	 * Get the EULA associated with an upgrade.
	 * 
	 * @param upgradePath
	 * @return
	 * @throws CommandException
	 */
	private String getUpgradeEula(String upgradePath) throws CommandException {
		try {
			final File upgradeEulaFile = new File(upgradePath + "/" + EULA_FILE);

			return Files.toString(upgradeEulaFile, Charsets.UTF_8);
		} catch (final IOException e) {
			CommandException ce = new CommandException(Error.EULA_ERROR, "Unable to read eula from upgrade", e);
			log.warn("Upgrade:: " + ce.toString(), ce);
			throw ce;
		}
	}

	/**
	 * @param baseUpgradePath
	 * @param toVersion
	 */
	private void recordUpgrade(String baseUpgradePath, long toVersion) {
		final Properties finishProps = new Properties();
		finishProps.setProperty(UpgradeProperty.FROM_VERSION, Long.valueOf(CPVersion.getVersion()).toString());
		finishProps.setProperty(UpgradeProperty.TO_VERSION, Long.valueOf(toVersion).toString());

		OutputStream out = null;
		try {
			final String filename = baseUpgradePath + FileUtility.SEP + UPGRADE_FILE;
			final File file = new File(filename);
			out = new FileOutputStream(file);
			finishProps.store(out, "Record of the last upgrade attempt.");
			out.close();
		} catch (final IOException e) {
			log.warn("Upgrade:: Unable to save properties " + e.getMessage(), e);//$NON-NLS-1$
		} finally {
			IOUtil.close(out);
		}
	}

	/**
	 * Read the upgrade properties file.
	 * 
	 * @param dir the directory where the properties file should exist
	 * @return the upgrade properties
	 * @throws InvalidUpgradeFileException unable to read properties file
	 */
	private PropertiesUtil readProperties(File dir) throws CommandException {
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
				CommandException ce = new CommandException(Error.INVALID_FILE, "Unable to read properties, file="
						+ upgradePropsFile.getAbsolutePath(), e);
				log.warn("Upgrade:: " + ce.toString(), ce);
				throw ce;
			} finally {
				IOUtil.close(in);
			}
		}
	}

	/**
	 * Get a File reference to the local upgrade file. If the file is already in place, use it; otherwise: - We are a
	 * Master, in which case, fileData must have been posted to us through the browser. - We are a StorageNode, in which
	 * case, we will use fileData if passed, otherwise, we will request the upgrade file from the Master.
	 * 
	 * @param upgradeDir
	 * @return
	 * @throws CommandException
	 */
	private File getUpgradeFile(String dirPath, String fName) throws CommandException {
		File upgradeDir = new File(dirPath);
		File upgradeFile = new File(upgradeDir, fName);

		// Check if the file is already in place and accessible.
		if (upgradeFile.exists() && upgradeFile.canRead() && upgradeFile.isFile()) {
			return upgradeFile;
		}

		// File is not present; determine method for its retrieval
		if (this.env.isMaster() || this.fileData != null) {
			upgradeFile = this.writeFileData(upgradeFile);

		} else {
			int attempts = 0;
			MD5Value md5 = this.sus.pullUpgradeFileFromMaster(upgradeDir.getAbsolutePath(), upgradeFile, TIMEOUT_MS);
			while (!this.fileHash.equals(md5) && attempts++ < 5) {
				FileUtility.deleteAll(upgradeFile);
				md5 = this.sus.pullUpgradeFileFromMaster(upgradeDir.getAbsolutePath(), upgradeFile, TIMEOUT_MS);
			}

			if (!this.fileHash.equals(md5)) {
				FileUtility.deleteAll(upgradeFile);
				throw new CommandException(
						"SERVER UPGRADE:: Unable to pull upgrade from Master.  MD5s Failed after 5 attempts. src={}, local={}",
						this.fileHash, md5);
			}

		}

		return upgradeFile;
	}

	private File writeFileData(File upgradeFile) throws CommandException {

		FileInputStream input = null;
		FileOutputStream output = null;
		try {
			input = new FileInputStream(this.fileData);

			log.info("Upgrade:: Upgrading using file: {}", upgradeFile.getAbsolutePath());
			output = new FileOutputStream(upgradeFile);
			int totalBytes = 0;
			int bytesread = -1;
			byte[] buffer = new byte[1024];
			while ((bytesread = input.read(buffer)) != -1) {
				output.write(buffer, 0, bytesread);
				totalBytes += bytesread;
			}
			log.info("Upgrade:: Saved upgrade file: {} with total bytes: {}", this.fileName, totalBytes);
		} catch (Throwable t) {
			log.error("Upgrade:: Unable to copy the upgrade file to the upgrade directory", t);
			throw new CommandException("Error uploading upgrade file", t);
		} finally {
			IOUtil.flush(output);
			IOUtil.close(output);
			IOUtil.close(input);
		}

		return upgradeFile;
	}

	public static class UpgradeMetadataDto {

		private long fromVersion;
		private long toVersion;
		private String upgradePath;
		private String eula;

		public UpgradeMetadataDto(String upgradePath, long fromVersion, long toVersion, String eula) {
			this.upgradePath = upgradePath;
			this.fromVersion = fromVersion;
			this.toVersion = toVersion;
			this.eula = eula;
		}

		public UpgradeMetadataDto() {
		}

		public long getFromVersion() {
			return this.fromVersion;
		}

		public void setFromVersion(long fromVersion) {
			this.fromVersion = fromVersion;
		}

		public long getToVersion() {
			return this.toVersion;
		}

		public void setToVersion(long toVersion) {
			this.toVersion = toVersion;
		}

		public String getUpgradePath() {
			return this.upgradePath;
		}

		public void setUpgradePath(String upgradePath) {
			this.upgradePath = upgradePath;
		}

		public String getEula() {
			return this.eula;
		}

		public void setEula(String eula) {
			this.eula = eula;
		}
	}
}
