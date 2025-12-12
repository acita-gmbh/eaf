package de.acci.dcm.api.controller

import de.acci.dcm.application.vm.VmProvisioningProgressProjection
import de.acci.dcm.application.vm.VmProvisioningProgressQueryService
import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.result.Result
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Controller for retrieving VM provisioning progress.
 */
@RestController
@RequestMapping("/api/requests")
public class VmProvisioningProgressController(
    private val queryService: VmProvisioningProgressQueryService
) {
    private val logger = KotlinLogging.logger {}

    /** Gets the progress for a request. */
    @GetMapping("/{id}/provisioning-progress")
    public suspend fun getProgress(@PathVariable id: UUID): ResponseEntity<VmProvisioningProgressProjection> {
        // VmRequestId.fromString handles UUID string
        val result = queryService.getProgress(VmRequestId.fromString(id.toString()))

        return when (result) {
            is Result.Success -> {
                val projection = result.value
                if (projection != null) {
                    ResponseEntity.ok(projection)
                } else {
                    // If not found, return 404
                    ResponseEntity.notFound().build()
                }
            }
            is Result.Failure -> {
                // Log actual error for audit trail, return opaque 404 for security
                logger.warn { "Failed to get provisioning progress for request $id: ${result.error}" }
                ResponseEntity.notFound().build()
            }
        }
    }
}