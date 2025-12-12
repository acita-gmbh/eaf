package de.acci.dvmm.application.vm

import de.acci.dvmm.domain.vm.VmProvisioningStage
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.tenant.TenantContextElement
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class VmProvisioningProgressQueryServiceTest {

    private val repository = mockk<VmProvisioningProgressProjectionRepository>()
    private val service = VmProvisioningProgressQueryService(repository)

    @Test
    fun `should return progress for valid tenant`() = runTest {
        val tenantId = TenantId.generate()
        val vmRequestId = VmRequestId.generate()
        val now = Instant.now()
        val expectedProgress = VmProvisioningProgressProjection(
            vmRequestId = vmRequestId,
            stage = VmProvisioningStage.CLONING,
            details = "Cloning VM...",
            startedAt = now,
            updatedAt = now,
            stageTimestamps = emptyMap(),
            estimatedRemainingSeconds = 60
        )

        coEvery { repository.findByVmRequestId(vmRequestId, tenantId) } returns expectedProgress

        withContext(TenantContextElement(tenantId)) {
            val result = service.getProgress(vmRequestId)
            assertTrue(result is Result.Success)
            assertEquals(expectedProgress, (result as Result.Success).value)
        }
    }

    @Test
    fun `should fail when tenant context is missing`() = runTest {
        val vmRequestId = VmRequestId.generate()

        // No TenantContextElement in context
        val result = service.getProgress(vmRequestId)

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is VmProvisioningProgressQueryService.Error.TenantContextUnavailable)
        val tenantError = error as VmProvisioningProgressQueryService.Error.TenantContextUnavailable
        assertEquals(vmRequestId, tenantError.vmRequestId)
        assertTrue(tenantError.message.contains("Tenant context missing or invalid"))
    }

    @Test
    fun `should handle repository returning null`() = runTest {
        val tenantId = TenantId.generate()
        val vmRequestId = VmRequestId.generate()

        coEvery { repository.findByVmRequestId(vmRequestId, tenantId) } returns null

        withContext(TenantContextElement(tenantId)) {
            val result = service.getProgress(vmRequestId)
            assertTrue(result is Result.Success)
            assertEquals(null, (result as Result.Success).value)
        }
    }
}
