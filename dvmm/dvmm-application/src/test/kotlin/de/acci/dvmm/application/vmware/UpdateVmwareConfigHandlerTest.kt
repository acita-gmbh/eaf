package de.acci.dvmm.application.vmware

import de.acci.dvmm.domain.vmware.VmwareConfiguration
import de.acci.dvmm.domain.vmware.VmwareConfigurationId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@DisplayName("UpdateVmwareConfigHandler")
class UpdateVmwareConfigHandlerTest {

    private val configurationPort = mockk<VmwareConfigurationPort>()
    private val credentialEncryptor = mockk<CredentialEncryptor>()
    private val fixedInstant = Instant.parse("2025-01-15T10:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private val handler = UpdateVmwareConfigHandler(
        configurationPort = configurationPort,
        credentialEncryptor = credentialEncryptor,
        clock = clock
    )

    private val testTenantId = TenantId.generate()
    private val testUserId = UserId.generate()

    private fun createExistingConfig(
        tenantId: TenantId = testTenantId,
        version: Long = 1L
    ): VmwareConfiguration = VmwareConfiguration.create(
        tenantId = tenantId,
        vcenterUrl = "https://old-vcenter.example.com/sdk",
        username = "old-admin@vsphere.local",
        passwordEncrypted = "old-encrypted".toByteArray(),
        datacenterName = "OldDC",
        clusterName = "OldCluster",
        datastoreName = "OldDatastore",
        networkName = "OldNetwork",
        templateName = VmwareConfiguration.DEFAULT_TEMPLATE_NAME,
        folderPath = null,
        userId = testUserId,
        timestamp = Instant.parse("2025-01-01T00:00:00Z")
    ).let {
        // Simulate existing config with specific version
        it.copy(version = version)
    }

    private fun createCommand(
        tenantId: TenantId = testTenantId,
        userId: UserId = testUserId,
        expectedVersion: Long = 1L,
        vcenterUrl: String? = "https://new-vcenter.example.com/sdk",
        username: String? = "new-admin@vsphere.local",
        password: String? = null,
        datacenterName: String? = null,
        clusterName: String? = null,
        datastoreName: String? = null,
        networkName: String? = null,
        templateName: String? = null,
        folderPath: String? = null
    ) = UpdateVmwareConfigCommand(
        tenantId = tenantId,
        userId = userId,
        expectedVersion = expectedVersion,
        vcenterUrl = vcenterUrl,
        username = username,
        password = password,
        datacenterName = datacenterName,
        clusterName = clusterName,
        datastoreName = datastoreName,
        networkName = networkName,
        templateName = templateName,
        folderPath = folderPath
    )

    @Nested
    @DisplayName("handle()")
    inner class HandleTests {

        @Test
        @DisplayName("should update configuration successfully without password change")
        fun `should update configuration successfully without password change`() = runTest {
            // Given
            val existingConfig = createExistingConfig(version = 1L)
            val command = createCommand(expectedVersion = 1L, password = null)

            coEvery { configurationPort.findByTenantId(command.tenantId) } returns existingConfig
            coEvery { configurationPort.update(any()) } returns Unit.success()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            val success = result as Result.Success
            assertEquals(2L, success.value.newVersion) // Version incremented

            coVerify(exactly = 0) { credentialEncryptor.encrypt(any()) }
            coVerify(exactly = 1) { configurationPort.update(any()) }
        }

        @Test
        @DisplayName("should update configuration with new password")
        fun `should update configuration with new password`() = runTest {
            // Given
            val existingConfig = createExistingConfig(version = 1L)
            val command = createCommand(expectedVersion = 1L, password = "new-password")
            val newEncryptedPassword = "new-encrypted".toByteArray()

            coEvery { configurationPort.findByTenantId(command.tenantId) } returns existingConfig
            every { credentialEncryptor.encrypt("new-password") } returns newEncryptedPassword
            coEvery { configurationPort.update(any()) } returns Unit.success()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            coVerify(exactly = 1) { credentialEncryptor.encrypt("new-password") }
            coVerify(exactly = 1) {
                configurationPort.update(match { it.passwordEncrypted.contentEquals(newEncryptedPassword) })
            }
        }

        @Test
        @DisplayName("should return error when configuration not found")
        fun `should return error when configuration not found`() = runTest {
            // Given
            val command = createCommand()
            coEvery { configurationPort.findByTenantId(command.tenantId) } returns null

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is UpdateVmwareConfigError.NotFound)
            val error = failure.error as UpdateVmwareConfigError.NotFound
            assertEquals(command.tenantId, error.tenantId)

            coVerify(exactly = 0) { configurationPort.update(any()) }
        }

        @Test
        @DisplayName("should return error when version mismatch")
        fun `should return error when version mismatch`() = runTest {
            // Given
            val existingConfig = createExistingConfig(version = 2L)
            val command = createCommand(expectedVersion = 1L) // Expecting old version

            coEvery { configurationPort.findByTenantId(command.tenantId) } returns existingConfig

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is UpdateVmwareConfigError.ConcurrencyConflict)
            val error = failure.error as UpdateVmwareConfigError.ConcurrencyConflict
            assertEquals(1L, error.expectedVersion)
            assertEquals(2L, error.actualVersion)

            coVerify(exactly = 0) { configurationPort.update(any()) }
        }

        @Test
        @DisplayName("should return error when encryption fails")
        fun `should return error when encryption fails`() = runTest {
            // Given
            val existingConfig = createExistingConfig(version = 1L)
            val command = createCommand(expectedVersion = 1L, password = "new-password")

            coEvery { configurationPort.findByTenantId(command.tenantId) } returns existingConfig
            every { credentialEncryptor.encrypt("new-password") } throws EncryptionException("Key error")

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is UpdateVmwareConfigError.EncryptionFailed)

            coVerify(exactly = 0) { configurationPort.update(any()) }
        }

        @Test
        @DisplayName("should return error when update fails with concurrency conflict")
        fun `should return error when update fails with concurrency conflict`() = runTest {
            // Given
            val existingConfig = createExistingConfig(version = 1L)
            val command = createCommand(expectedVersion = 1L, password = null)

            coEvery { configurationPort.findByTenantId(command.tenantId) } returns existingConfig
            coEvery { configurationPort.update(any()) } returns VmwareConfigurationError.ConcurrencyConflict(
                expectedVersion = 2L,
                actualVersion = 3L
            ).failure()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is UpdateVmwareConfigError.ConcurrencyConflict)
        }

        @Test
        @DisplayName("should return error when update fails with persistence failure")
        fun `should return error when update fails with persistence failure`() = runTest {
            // Given
            val existingConfig = createExistingConfig(version = 1L)
            val command = createCommand(expectedVersion = 1L, password = null)

            coEvery { configurationPort.findByTenantId(command.tenantId) } returns existingConfig
            coEvery { configurationPort.update(any()) } returns VmwareConfigurationError.PersistenceFailure(
                message = "DB error"
            ).failure()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is UpdateVmwareConfigError.PersistenceFailure)
        }

        @Test
        @DisplayName("should preserve existing values when null is provided")
        fun `should preserve existing values when null is provided`() = runTest {
            // Given
            val existingConfig = createExistingConfig(version = 1L)
            val command = UpdateVmwareConfigCommand(
                tenantId = testTenantId,
                userId = testUserId,
                expectedVersion = 1L,
                vcenterUrl = null, // Keep existing
                username = null, // Keep existing
                password = null, // Keep existing
                datacenterName = "NewDC", // Update this
                clusterName = null, // Keep existing
                datastoreName = null, // Keep existing
                networkName = null, // Keep existing
                templateName = null, // Keep existing
                folderPath = null // Keep existing
            )

            coEvery { configurationPort.findByTenantId(command.tenantId) } returns existingConfig
            coEvery { configurationPort.update(any()) } returns Unit.success()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            coVerify(exactly = 1) {
                configurationPort.update(match {
                    it.vcenterUrl == existingConfig.vcenterUrl &&
                        it.username == existingConfig.username &&
                        it.datacenterName == "NewDC"
                })
            }
        }
    }
}
