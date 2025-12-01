package de.acci.dvmm.api.vmrequest

import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmRequestStatus
import de.acci.dvmm.domain.vmrequest.VmSize
import java.time.Instant

/**
 * Response DTO for VM request.
 *
 * @property id Unique identifier for this request
 * @property vmName Requested VM name
 * @property projectId Project this VM belongs to
 * @property projectName Display name of the project (optional, set after lookup)
 * @property size VM size with resource specifications
 * @property status Current request status
 * @property createdAt When the request was created
 */
public data class VmRequestResponse(
    val id: String,
    val vmName: String,
    val projectId: String,
    val projectName: String?,
    val size: VmSizeResponse,
    val status: String,
    val createdAt: Instant
) {
    public companion object {
        public fun created(
            requestId: VmRequestId,
            vmName: String,
            projectId: String,
            size: VmSize,
            createdAt: Instant
        ): VmRequestResponse = VmRequestResponse(
            id = requestId.value.toString(),
            vmName = vmName,
            projectId = projectId,
            projectName = null, // Will be populated by projection queries
            size = VmSizeResponse.fromDomain(size),
            status = VmRequestStatus.PENDING.name,
            createdAt = createdAt
        )
    }
}

/**
 * Response DTO for VM size with resource specifications.
 */
public data class VmSizeResponse(
    val code: String,
    val cpuCores: Int,
    val memoryGb: Int,
    val diskGb: Int
) {
    public companion object {
        public fun fromDomain(size: VmSize): VmSizeResponse = VmSizeResponse(
            code = size.name,
            cpuCores = size.cpuCores,
            memoryGb = size.memoryGb,
            diskGb = size.diskGb
        )
    }
}
