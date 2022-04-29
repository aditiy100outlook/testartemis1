/*
 * <a href="http://www.code42.com">(c)2011 Code 42 Software, Inc.</a>
 */
package com.code42.cli;

import java.util.List;

import com.backup42.app.command.RetrieveHistoryCommand;
import com.backup42.common.CPErrors;
import com.backup42.common.command.CommandInfo;
import com.backup42.common.command.CommandResult;
import com.backup42.common.command.ICliExecutor;
import com.backup42.common.command.ServiceCommand;
import com.code42.auth.IPermission;
import com.code42.computer.ComputerDeauthorizeCmd;
import com.code42.computer.ComputerSso;
import com.code42.computer.ComputerSsoFindByComputerIdCmd;
import com.code42.computer.ComputerSsoFindByGuidCmd;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsComputerManageableCmd;
import com.code42.core.impl.AbstractCmd;
import com.google.inject.Inject;

/**
 * Runs commands from the command-line interface:
 * <ul>
 * <li>'clientCommand pause 99999999' (to be run on the client guid 999999999)</li>
 * <li>'clientCommand pause id123' (where the 'id' prefix tells you this is a computerId)</li>
 * <li>'daily.accounting' (runs on the server)</li>
 * </ul>
 */
public class CliRunCmd extends AbstractCmd<List<CommandResult>> {

	private final String command;

	/**
	 * This should be a service, but the executor needs to be able to run on both the client and the server, which means
	 * we cannot turn it a service without great care... or wrapping it. Baby steps.
	 */
	@Inject
	private ICliExecutor cliExecutor;

	/**
	 * @param command
	 */
	public CliRunCmd(String command) {
		super();
		this.command = command;
	}

	@Override
	public List<CommandResult> exec(CoreSession session) throws CommandException {

		List<CommandResult> list = this.cliExecutor.preprocessCommands(this.command);

		// Require SYSADMIN permission for everything except computer commands, which require a special computer
		// authorization check.
		for (CommandResult result : list) {
			if (result.getError() != null) {
				continue; // We do nothing with commands that had errors during preprocessing.
			}

			CommandInfo info = result.getCommand();
			IPermission perm = this.auth.getPermission(info.getPermission());
			if (info.getImpl().contains("ClientCommand") || info.getCls().equals(RetrieveHistoryCommand.class)) {

				// We need both of these
				Long computerId = null;
				Long guid = null;

				final int arg = info.getCls().equals(RetrieveHistoryCommand.class) ? 0 : 1;

				if (result.getArguments().length <= arg) {
					result.setError(CPErrors.Command.USAGE);
					continue;
				}

				// Parse the computerId or the guid
				if (result.getArgument(arg).toLowerCase().startsWith("id")) {
					try {
						computerId = Long.parseLong(result.getArgument(arg).substring(2));
					} catch (NumberFormatException e) {
						result.setError(CPErrors.Command.USAGE);
						continue;
					}
				} else {
					// Assume this is a guid
					try {
						guid = Long.parseLong(result.getArgument(arg));
					} catch (NumberFormatException e) {
						result.setError(CPErrors.Command.USAGE);
						continue;
					}
				}

				// Find the space storage object so we have both a guid and a computerId
				ComputerSso cSso = null;
				if (guid != null) {
					cSso = this.run(new ComputerSsoFindByGuidCmd(guid), session);
				} else if (computerId != null) {
					cSso = this.run(new ComputerSsoFindByComputerIdCmd(computerId), session);
				}

				if (cSso == null) {
					result.setError(CPErrors.Command.INVALID_COMPUTER);
					continue;
				}

				// Check for Computer UPDATE permission on this computer, if the permission is not specified appropriately.
				// Effectively, this means that putting a non-Computer permission requirement on a ClientCommand is pointless,
				// but that's ok.
				try {
					C42PermissionApp.Computer cPerm = C42PermissionApp.Computer.UPDATE;
					if (C42PermissionApp.Computer.isComputerPermission(perm)) {
						cPerm = (C42PermissionApp.Computer) perm;
					}
					this.run(new IsComputerManageableCmd(cSso.getComputerId(), cPerm), session);
				} catch (UnauthorizedException e) {
					result.setError(CPErrors.Command.UNAUTHORIZED);
				}

				if (guid == null) {
					// Convert the computerId argument to be a guid
					result.getArguments()[arg] = String.valueOf(cSso.getGuid());
				}

				// The client does not have to be connected to deauthorize so we'll shortcut the process here
				if (info.getImpl().toLowerCase().contains("." + ServiceCommand.DEAUTHORIZE)) {
					this.run(new ComputerDeauthorizeCmd(cSso.getComputerId()), session);
				}
			} else {
				// We have a "central" (server) command

				try {
					// CommandInfo default to C42PermissionPro.System.COMMAND unless otherwise specified
					this.auth.isAuthorized(session, perm);
				} catch (UnauthorizedException e) {
					result.setError(CPErrors.Command.UNAUTHORIZED);
				}
			}
		}

		// Commands that already have an error will be skipped in this call...
		return this.cliExecutor.runAll(list);
	}
}
