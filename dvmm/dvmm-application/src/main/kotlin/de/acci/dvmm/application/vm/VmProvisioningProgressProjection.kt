package de.acci.dvmm.application.vm

import de.acci.dvmm.domain.vm.VmProvisioningStage
import de.acci.dvmm.domain.vmrequest.VmRequestId
import java.time.Instant

public data class VmProvisioningProgressProjection(
    public val vmRequestId: VmRequestId,
    public val stage: VmProvisioningStage,
    public val details: String,
    public val startedAt: Instant,
    public val updatedAt: Instant
)
