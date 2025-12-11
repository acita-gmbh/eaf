package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.application.vmware.HypervisorPort
import de.acci.dvmm.application.vmware.VmId
import de.acci.dvmm.application.vmware.VmInfo
import de.acci.dvmm.application.vmware.VmPowerState
import de.acci.dvmm.application.vmware.VsphereError
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@DisplayName("SyncVmStatusHandler")
class SyncVmStatusHandlerTest {

    private lateinit var hypervisorPort: HypervisorPort
    private lateinit var projectionPort: VmStatusProjectionPort
    private lateinit var handler: SyncVmStatusHandler

    private val testTenantId = TenantId(UUID.randomUUID())
    private val testUserId = UserId(UUID.randomUUID())
    private val testRequestId = VmRequestId(UUID.randomUUID())
    private val testVmwareVmId = "vm-12345"
    private val fixedInstant = Instant.parse("2024-01-15T10:30:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    @BeforeEach
    fun setup() {
        hypervisorPort = mockk()
        projectionPort = mockk()
        handler = SyncVmStatusHandler(hypervisorPort, projectionPort, fixedClock)
    }

    @Nested
    @DisplayName("when VM is provisioned")
    inner class WhenVmProvisioned {

        @Test
        fun `returns success with VM status details`() = runBlocking {
            // Given
            val vmInfo = VmInfo(
                id = testVmwareVmId,
                name = "web-server-01",
                powerState = VmPowerState.POWERED_ON,
                ipAddress = "192.168.1.100",
                hostname = "web-server-01.local",
                guestOs = "Ubuntu 22.04.3 LTS (64-bit)"
            )

            coEvery { projectionPort.getVmwareVmId(testRequestId) } returns testVmwareVmId
            coEvery { hypervisorPort.getVm(VmId(testVmwareVmId)) } returns vmInfo.success()
            coEvery {
                projectionPort.updateVmDetails(
                    requestId = testRequestId,
                    vmwareVmId = testVmwareVmId,
                    ipAddress = "192.168.1.100",
                    hostname = "web-server-01.local",
                    powerState = "POWERED_ON",
                    guestOs = "Ubuntu 22.04.3 LTS (64-bit)",
                    lastSyncedAt = fixedInstant
                )
            } returns 1

            // When
            val command = SyncVmStatusCommand(
                tenantId = testTenantId,
                requestId = testRequestId,
                userId = testUserId
            )
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            val syncResult = (result as Result.Success).value
            assertEquals(testRequestId, syncResult.requestId)
            assertEquals("POWERED_ON", syncResult.powerState)
            assertEquals("192.168.1.100", syncResult.ipAddress)

            coVerify(exactly = 1) {
                projectionPort.updateVmDetails(
                    requestId = testRequestId,
                    vmwareVmId = testVmwareVmId,
                    ipAddress = "192.168.1.100",
                    hostname = "web-server-01.local",
                    powerState = "POWERED_ON",
                    guestOs = "Ubuntu 22.04.3 LTS (64-bit)",
                    lastSyncedAt = fixedInstant
                )
            }
        }

        @Test
        fun `handles VM with no IP address detected`() = runBlocking {
            // Given: VM is powered on but VMware Tools hasn't detected IP yet
            val vmInfo = VmInfo(
                id = testVmwareVmId,
                name = "web-server-01",
                powerState = VmPowerState.POWERED_ON,
                ipAddress = null,
                hostname = null,
                guestOs = null
            )

            coEvery { projectionPort.getVmwareVmId(testRequestId) } returns testVmwareVmId
            coEvery { hypervisorPort.getVm(VmId(testVmwareVmId)) } returns vmInfo.success()
            coEvery {
                projectionPort.updateVmDetails(
                    requestId = testRequestId,
                    vmwareVmId = testVmwareVmId,
                    ipAddress = null,
                    hostname = null,
                    powerState = "POWERED_ON",
                    guestOs = null,
                    lastSyncedAt = fixedInstant
                )
            } returns 1

            // When
            val command = SyncVmStatusCommand(
                tenantId = testTenantId,
                requestId = testRequestId,
                userId = testUserId
            )
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            val syncResult = (result as Result.Success).value
            assertEquals("POWERED_ON", syncResult.powerState)
            assertEquals(null, syncResult.ipAddress)
        }

        @Test
        fun `handles powered off VM`() = runBlocking {
            // Given
            val vmInfo = VmInfo(
                id = testVmwareVmId,
                name = "web-server-01",
                powerState = VmPowerState.POWERED_OFF,
                ipAddress = null,
                hostname = null,
                guestOs = "Ubuntu 22.04.3 LTS (64-bit)"
            )

            coEvery { projectionPort.getVmwareVmId(testRequestId) } returns testVmwareVmId
            coEvery { hypervisorPort.getVm(VmId(testVmwareVmId)) } returns vmInfo.success()
            coEvery {
                projectionPort.updateVmDetails(
                    requestId = testRequestId,
                    vmwareVmId = testVmwareVmId,
                    ipAddress = null,
                    hostname = null,
                    powerState = "POWERED_OFF",
                    guestOs = "Ubuntu 22.04.3 LTS (64-bit)",
                    lastSyncedAt = fixedInstant
                )
            } returns 1

            // When
            val result = handler.handle(
                SyncVmStatusCommand(testTenantId, testRequestId, testUserId)
            )

            // Then
            assertTrue(result is Result.Success)
            assertEquals("POWERED_OFF", (result as Result.Success).value.powerState)
        }
    }

    @Nested
    @DisplayName("when VM is not provisioned")
    inner class WhenVmNotProvisioned {

        @Test
        fun `returns NotProvisioned error`() = runBlocking {
            // Given: No vmwareVmId in projection (VM not yet provisioned)
            coEvery { projectionPort.getVmwareVmId(testRequestId) } returns null

            // When
            val result = handler.handle(
                SyncVmStatusCommand(testTenantId, testRequestId, testUserId)
            )

            // Then
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is SyncVmStatusError.NotProvisioned)
            assertEquals(testRequestId, (error as SyncVmStatusError.NotProvisioned).requestId)
        }
    }

    @Nested
    @DisplayName("when vSphere query fails")
    inner class WhenVsphereQueryFails {

        @Test
        fun `returns HypervisorError when VM not found in vSphere`() = runBlocking {
            // Given
            coEvery { projectionPort.getVmwareVmId(testRequestId) } returns testVmwareVmId
            coEvery { hypervisorPort.getVm(VmId(testVmwareVmId)) } returns
                VsphereError.ResourceNotFound(
                    resourceType = "VirtualMachine",
                    resourceId = testVmwareVmId
                ).failure()

            // When
            val result = handler.handle(
                SyncVmStatusCommand(testTenantId, testRequestId, testUserId)
            )

            // Then
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is SyncVmStatusError.HypervisorError)
        }

        @Test
        fun `returns HypervisorError on connection failure`() = runBlocking {
            // Given
            coEvery { projectionPort.getVmwareVmId(testRequestId) } returns testVmwareVmId
            coEvery { hypervisorPort.getVm(VmId(testVmwareVmId)) } returns
                VsphereError.ConnectionError("Network unreachable").failure()

            // When
            val result = handler.handle(
                SyncVmStatusCommand(testTenantId, testRequestId, testUserId)
            )

            // Then
            assertTrue(result is Result.Failure)
            assertTrue((result as Result.Failure).error is SyncVmStatusError.HypervisorError)
        }
    }

    @Nested
    @DisplayName("when projection update fails")
    inner class WhenProjectionUpdateFails {

        @Test
        fun `returns NotFound when no rows updated`() = runBlocking {
            // Given: vSphere returns VM info but projection update affects 0 rows (request deleted?)
            val vmInfo = VmInfo(
                id = testVmwareVmId,
                name = "web-server-01",
                powerState = VmPowerState.POWERED_ON
            )

            coEvery { projectionPort.getVmwareVmId(testRequestId) } returns testVmwareVmId
            coEvery { hypervisorPort.getVm(VmId(testVmwareVmId)) } returns vmInfo.success()
            coEvery {
                projectionPort.updateVmDetails(any(), any(), any(), any(), any(), any(), any())
            } returns 0

            // When
            val result = handler.handle(
                SyncVmStatusCommand(testTenantId, testRequestId, testUserId)
            )

            // Then
            assertTrue(result is Result.Failure)
            assertTrue((result as Result.Failure).error is SyncVmStatusError.NotFound)
        }

        @Test
        fun `returns UpdateFailure on database exception`() = runBlocking {
            // Given
            val vmInfo = VmInfo(
                id = testVmwareVmId,
                name = "web-server-01",
                powerState = VmPowerState.POWERED_ON
            )

            coEvery { projectionPort.getVmwareVmId(testRequestId) } returns testVmwareVmId
            coEvery { hypervisorPort.getVm(VmId(testVmwareVmId)) } returns vmInfo.success()
            coEvery {
                projectionPort.updateVmDetails(any(), any(), any(), any(), any(), any(), any())
            } throws RuntimeException("Database connection lost")

            // When
            val result = handler.handle(
                SyncVmStatusCommand(testTenantId, testRequestId, testUserId)
            )

            // Then
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is SyncVmStatusError.UpdateFailure)
            assertTrue((error as SyncVmStatusError.UpdateFailure).message.contains("Database connection lost"))
        }
    }
}
