package de.acci.dvmm.api.controller

import de.acci.dvmm.application.vm.VmProvisioningProgressProjection
import de.acci.dvmm.application.vm.VmProvisioningProgressQueryService
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.result.Result
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/requests")
public class VmProvisioningProgressController(
    private val queryService: VmProvisioningProgressQueryService
) {

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
                // Opaque error for security
                ResponseEntity.notFound().build()
            }
        }
    }
}