package de.acci.dvmm.application.vm

import de.acci.dvmm.domain.vm.VmProvisioningStage
import de.acci.dvmm.domain.vmrequest.VmRequestId
import java.time.Instant

/**
 * Projection for tracking provisioning progress with per-stage timestamps.
 *
 * @property vmRequestId The ID of the VM request being provisioned
 * @property stage The current provisioning stage
 * @property details Human-readable status message
 * @property startedAt When provisioning started
 * @property updatedAt When the current stage was reached
 * @property stageTimestamps Map of stage -> completion timestamp for each completed stage
 * @property estimatedRemainingSeconds Estimated seconds until provisioning completes (null if unknown)
 */
public data class VmProvisioningProgressProjection(
    public val vmRequestId: VmRequestId,
    public val stage: VmProvisioningStage,
    public val details: String,
    public val startedAt: Instant,
    public val updatedAt: Instant,
    public val stageTimestamps: Map<VmProvisioningStage, Instant> = emptyMap(),
    public val estimatedRemainingSeconds: Long? = null
) {
    public companion object {
        /**
         * Estimated duration in seconds for each stage based on typical vSphere operations.
         * Used for ETA calculation.
         *
         * Note: These are initial estimates based on observed vSphere behavior with small VMs.
         * Actual durations vary significantly based on:
         * - VM size (disk clone time scales with size)
         * - Storage performance (SSD vs HDD, datastore load)
         * - Network conditions (for VMware Tools/IP detection)
         * - vCenter server load
         *
         * TODO: Consider calibrating from real metrics or making configurable via properties
         * if user feedback indicates estimates are consistently inaccurate.
         */
        public val ESTIMATED_STAGE_DURATIONS: Map<VmProvisioningStage, Long> = mapOf(
            VmProvisioningStage.CLONING to 45L,
            VmProvisioningStage.CONFIGURING to 15L,
            VmProvisioningStage.POWERING_ON to 20L,
            VmProvisioningStage.WAITING_FOR_NETWORK to 45L,
            VmProvisioningStage.READY to 0L
        )

        /**
         * Calculates estimated remaining seconds based on current stage.
         */
        public fun calculateEstimatedRemaining(currentStage: VmProvisioningStage): Long {
            val stages = VmProvisioningStage.entries
            val currentIndex = stages.indexOf(currentStage)
            return stages.drop(currentIndex + 1)
                .sumOf { ESTIMATED_STAGE_DURATIONS[it] ?: 0L }
        }
    }
}
