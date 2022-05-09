package com.code42.exec;

import java.io.File;
import java.util.concurrent.TimeUnit;

import com.backup42.common.perm.C42PermissionPro;
import com.code42.core.CommandException;
import com.code42.core.IEnvironment;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.core.schedule.IScheduleService;
import com.code42.core.schedule.SchedulerException;
import com.code42.utils.Os;
import com.google.inject.Inject;

/**
 * Encapsulation of all logic necessary to execute the external shell script responsible for copying a generated
 * database (the one to be "imported") and restarting the server.
 * 
 * @author bmcguire
 */
public class DatabaseImportExecuteCmd extends AbstractCmd<Void> {

	/* ================= Dependencies ================= */
	private IScheduleService schedule;
	private IEnvironment env;

	/* ================= DI injection points ================= */
	@Inject
	public void setSchedule(IScheduleService schedule) {
		this.schedule = schedule;
	}

	@Inject
	public void setEnvironment(IEnvironment env) {
		this.env = env;
	}

	@Override
	public Void exec(CoreSession session) throws CommandException {

		this.auth.isAuthorized(session, C42PermissionPro.System.MANAGE_DATABASE);

		File libexec = new File("libexec");
		File script = new File(libexec, this.getScriptName());
		this.runtime.runAsync(new ExecuteShellCommandCmd(script, libexec), session);

		try {

			/*
			 * We used to execute this task with SystemUpgrade.executeCommand(). That method would kill the system if it
			 * didn't get a result back within one second. Simulate that behaviour here.
			 */
			this.schedule.scheduleWithDelay("exit", "core", 5, TimeUnit.SECONDS, new AbstractCmd<Void>() {

				@Override
				public Void exec(CoreSession session) throws CommandException {

					System.exit(0);
					return null;
				}
			});
		} catch (SchedulerException se) {
			throw new CommandException("Exception scheduling shutdown task", se);
		}

		return null;
	}

	/*
	 * This used to be defined in system props... now that we have the command pattern and the ability to determine
	 * platforms programmatically there's really no need to do so.
	 */
	private String getScriptName() {

		return (this.env.getOs() == Os.Windows) ? "db_import.bat" : "db_import.sh";
	}
}
