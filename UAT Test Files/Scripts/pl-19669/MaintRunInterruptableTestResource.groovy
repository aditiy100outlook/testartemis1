package com.code42.backup.api.v1

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import com.code42.api.lib.RESTResource
import com.code42.backup.ArchiveServer
import com.code42.backup.manifest.ArchivePropsWork.PropsSetAndStoreReduceStateWork
import com.code42.backup.manifest.BackupArchiveProperties.ReduceState
import com.code42.core.archive.IArchiveService
import com.code42.core.auth.impl.CoreSession
import com.code42.core.impl.CoreBridge
import com.code42.keys.ArchiveGuid


/**
 * Note: This only exists for QA testing of maintenance interruptibility and should never be merged into the
 * code base.  This will be replaced by a more permanent solution when there is time.
 * @author nick.shelago-hegna
 *
 */
public class MaintRunInterruptableTestResource extends RESTResource {

	def post(HttpServletRequest req, HttpServletResponse resp, Map mappingData, CoreSession session) {
		standardExceptionCheck({

			def sBody = getBody(req)
			def body = parseBody(sBody, req.contentType)

			def archiveGuid = ArchiveGuid.of(getRequiredLong(body, "archiveGuid"))

			IArchiveService service = CoreBridge.getArchiveService()

			ArchiveServer archiveServer = service.createArchiveServer(archiveGuid);
			archiveServer.getMaintenanceManager().setLastShouldRunMaintenanceTimestamp(0L)

			service.getArchiveManager().doWork(archiveGuid, new PropsSetAndStoreReduceStateWork(ReduceState.QUEUED))

			service.getMaintenanceManager().addMaintJob(archiveGuid, false, true, false, null)
		}, req, resp, mappingData, session)
	}
}
