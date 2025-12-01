package de.acci.dvmm.api.vmrequest

import de.acci.dvmm.domain.vmrequest.events.VmRequestCancelled
import jakarta.validation.constraints.Size

/**
 * Request DTO for cancelling a VM request.
 *
 * @property reason Optional reason for cancellation (max [VmRequestCancelled.MAX_REASON_LENGTH] characters)
 */
public data class CancelVmRequestRequest(
    @field:Size(
        max = VmRequestCancelled.MAX_REASON_LENGTH,
        message = "Reason must not exceed ${VmRequestCancelled.MAX_REASON_LENGTH} characters"
    )
    val reason: String? = null
)
