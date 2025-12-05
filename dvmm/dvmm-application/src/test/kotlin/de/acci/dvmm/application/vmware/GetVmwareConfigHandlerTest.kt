package de.acci.dvmm.application.vmware

import de.acci.dvmm.domain.vmware.VmwareConfiguration
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("GetVmwareConfigHandler")
class GetVmwareConfigHandlerTest {

    private val configurationPort = mockk<VmwareConfigurationPort>()
    private val handler = GetVmwareConfigHandler(configurationPort)

    private val testTenantId = TenantId.generate()
    private val testUserId = UserId.generate()

    private fun createConfig(
        tenantId: TenantId = testTenantId,
        verifiedAt: Instant? = null
    ): VmwareConfiguration = VmwareConfiguration.create(
        tenantId = tenantId,
        vcenterUrl = "https://vcenter.example.com/sdk",
        username = "admin@vsphere.local",
        passwordEncrypted = "encrypted".toByteArray(),
        datacenterName = "DC1",
        clusterName = "Cluster1",
        datastoreName = "Datastore1",
        networkName = "VM-Network",
        templateName = VmwareConfiguration.DEFAULT_TEMPLATE_NAME,
        folderPath = "/VMs/Production",
        userId = testUserId,
        timestamp = Instant.parse("2025-01-01T00:00:00Z")
    ).let { config ->
        if (verifiedAt != null) config.markVerified(verifiedAt, testUserId) else config
    }

    @Nested
    @DisplayName("handle()")
    inner class HandleTests {

        @Test
        @DisplayName("should return configuration successfully")
        fun `should return configuration successfully`() = runTest {
            // Given
            val config = createConfig()
            val query = GetVmwareConfigQuery(tenantId = testTenantId)

            coEvery { configurationPort.findByTenantId(testTenantId) } returns config

            // When
            val result = handler.handle(query)

            // Then
            assertTrue(result is Result.Success)
            val response = (result as Result.Success).value

            assertEquals(config.id, response.id)
            assertEquals(config.tenantId, response.tenantId)
            assertEquals(config.vcenterUrl, response.vcenterUrl)
            assertEquals(config.username, response.username)
            assertEquals(config.datacenterName, response.datacenterName)
            assertEquals(config.clusterName, response.clusterName)
            assertEquals(config.datastoreName, response.datastoreName)
            assertEquals(config.networkName, response.networkName)
            assertEquals(config.templateName, response.templateName)
            assertEquals(config.folderPath, response.folderPath)
            assertEquals(config.version, response.version)
        }

        @Test
        @DisplayName("should return configuration with verified timestamp")
        fun `should return configuration with verified timestamp`() = runTest {
            // Given
            val verifiedTime = Instant.parse("2025-01-10T15:30:00Z")
            val config = createConfig(verifiedAt = verifiedTime)
            val query = GetVmwareConfigQuery(tenantId = testTenantId)

            coEvery { configurationPort.findByTenantId(testTenantId) } returns config

            // When
            val result = handler.handle(query)

            // Then
            assertTrue(result is Result.Success)
            val response = (result as Result.Success).value
            assertNotNull(response.verifiedAt)
            assertEquals(verifiedTime, response.verifiedAt)
        }

        @Test
        @DisplayName("should return configuration without verified timestamp when never verified")
        fun `should return configuration without verified timestamp when never verified`() = runTest {
            // Given
            val config = createConfig(verifiedAt = null)
            val query = GetVmwareConfigQuery(tenantId = testTenantId)

            coEvery { configurationPort.findByTenantId(testTenantId) } returns config

            // When
            val result = handler.handle(query)

            // Then
            assertTrue(result is Result.Success)
            val response = (result as Result.Success).value
            assertNull(response.verifiedAt)
        }

        @Test
        @DisplayName("should return error when configuration not found")
        fun `should return error when configuration not found`() = runTest {
            // Given
            val query = GetVmwareConfigQuery(tenantId = testTenantId)
            coEvery { configurationPort.findByTenantId(testTenantId) } returns null

            // When
            val result = handler.handle(query)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is GetVmwareConfigError.NotFound)
            val error = failure.error as GetVmwareConfigError.NotFound
            assertEquals(testTenantId, error.tenantId)
        }

        @Test
        @DisplayName("should return error when exception occurs")
        fun `should return error when exception occurs`() = runTest {
            // Given
            val query = GetVmwareConfigQuery(tenantId = testTenantId)
            coEvery { configurationPort.findByTenantId(testTenantId) } throws RuntimeException("Database connection error")

            // When
            val result = handler.handle(query)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is GetVmwareConfigError.QueryFailure)
            val error = failure.error as GetVmwareConfigError.QueryFailure
            assertTrue(error.message.contains("Database connection error"))
        }
    }
}

@DisplayName("CheckVmwareConfigExistsHandler")
class CheckVmwareConfigExistsHandlerTest {

    private val configurationPort = mockk<VmwareConfigurationPort>()
    private val handler = CheckVmwareConfigExistsHandler(configurationPort)

    @Nested
    @DisplayName("handle()")
    inner class HandleTests {

        @Test
        @DisplayName("should return true when configuration exists")
        fun `should return true when configuration exists`() = runTest {
            // Given
            val tenantId = TenantId.generate()
            val query = CheckVmwareConfigExistsQuery(tenantId = tenantId)

            coEvery { configurationPort.existsByTenantId(tenantId) } returns true

            // When
            val result = handler.handle(query)

            // Then
            assertTrue(result)
        }

        @Test
        @DisplayName("should return false when configuration does not exist")
        fun `should return false when configuration does not exist`() = runTest {
            // Given
            val tenantId = TenantId.generate()
            val query = CheckVmwareConfigExistsQuery(tenantId = tenantId)

            coEvery { configurationPort.existsByTenantId(tenantId) } returns false

            // When
            val result = handler.handle(query)

            // Then
            assertTrue(!result)
        }

        @Test
        @DisplayName("should propagate exception from port")
        fun `should propagate exception from port`() = runTest {
            // Given
            val tenantId = TenantId.generate()
            val query = CheckVmwareConfigExistsQuery(tenantId = tenantId)

            coEvery { configurationPort.existsByTenantId(tenantId) } throws RuntimeException("Database error")

            // When/Then - exception should propagate (caller handles error scenarios)
            org.junit.jupiter.api.assertThrows<RuntimeException> {
                handler.handle(query)
            }
        }
    }
}
