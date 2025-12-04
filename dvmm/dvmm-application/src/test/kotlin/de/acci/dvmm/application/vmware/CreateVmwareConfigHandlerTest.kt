package de.acci.dvmm.application.vmware

import de.acci.dvmm.domain.vmware.VmwareConfiguration
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

@DisplayName("CreateVmwareConfigHandler")
class CreateVmwareConfigHandlerTest {

    private val configurationPort = mockk<VmwareConfigurationPort>()
    private val credentialEncryptor = mockk<CredentialEncryptor>()
    private val fixedInstant = Instant.parse("2025-01-15T10:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private val handler = CreateVmwareConfigHandler(
        configurationPort = configurationPort,
        credentialEncryptor = credentialEncryptor,
        clock = clock
    )

    private fun createCommand(
        tenantId: TenantId = TenantId.generate(),
        userId: UserId = UserId.generate(),
        vcenterUrl: String = "https://vcenter.example.com/sdk",
        username: String = "admin@vsphere.local",
        password: String = "secret123",
        datacenterName: String = "DC1",
        clusterName: String = "Cluster1",
        datastoreName: String = "Datastore1",
        networkName: String = "VM-Network",
        templateName: String = VmwareConfiguration.DEFAULT_TEMPLATE_NAME,
        folderPath: String? = null
    ) = CreateVmwareConfigCommand(
        tenantId = tenantId,
        userId = userId,
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
        @DisplayName("should create configuration successfully")
        fun `should create configuration successfully`() = runTest {
            // Given
            val command = createCommand()
            val encryptedPassword = "encrypted".toByteArray()

            coEvery { configurationPort.existsByTenantId(command.tenantId) } returns false
            every { credentialEncryptor.encrypt(command.password) } returns encryptedPassword
            coEvery { configurationPort.save(any()) } returns Unit.success()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            val success = result as Result.Success
            assertTrue(success.value.configurationId.value.toString().isNotEmpty())

            coVerify(exactly = 1) { configurationPort.existsByTenantId(command.tenantId) }
            coVerify(exactly = 1) { credentialEncryptor.encrypt(command.password) }
            coVerify(exactly = 1) { configurationPort.save(any()) }
        }

        @Test
        @DisplayName("should return error when configuration already exists")
        fun `should return error when configuration already exists`() = runTest {
            // Given
            val command = createCommand()
            coEvery { configurationPort.existsByTenantId(command.tenantId) } returns true

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is CreateVmwareConfigError.ConfigurationAlreadyExists)
            val error = failure.error as CreateVmwareConfigError.ConfigurationAlreadyExists
            assertEquals(command.tenantId, error.tenantId)

            coVerify(exactly = 1) { configurationPort.existsByTenantId(command.tenantId) }
            coVerify(exactly = 0) { credentialEncryptor.encrypt(any()) }
            coVerify(exactly = 0) { configurationPort.save(any()) }
        }

        @Test
        @DisplayName("should return error when encryption fails")
        fun `should return error when encryption fails`() = runTest {
            // Given
            val command = createCommand()

            coEvery { configurationPort.existsByTenantId(command.tenantId) } returns false
            every { credentialEncryptor.encrypt(command.password) } throws EncryptionException("Encryption failed")

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is CreateVmwareConfigError.EncryptionFailed)
            val error = failure.error as CreateVmwareConfigError.EncryptionFailed
            assertTrue(error.message.contains("Encryption failed"))

            coVerify(exactly = 1) { configurationPort.existsByTenantId(command.tenantId) }
            coVerify(exactly = 0) { configurationPort.save(any()) }
        }

        @Test
        @DisplayName("should return error when save fails with AlreadyExists")
        fun `should return error when save fails with AlreadyExists`() = runTest {
            // Given
            val command = createCommand()
            val encryptedPassword = "encrypted".toByteArray()

            coEvery { configurationPort.existsByTenantId(command.tenantId) } returns false
            every { credentialEncryptor.encrypt(command.password) } returns encryptedPassword
            coEvery { configurationPort.save(any()) } returns VmwareConfigurationError.AlreadyExists(
                tenantId = command.tenantId
            ).failure()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is CreateVmwareConfigError.ConfigurationAlreadyExists)
        }

        @Test
        @DisplayName("should return error when save fails with PersistenceFailure")
        fun `should return error when save fails with PersistenceFailure`() = runTest {
            // Given
            val command = createCommand()
            val encryptedPassword = "encrypted".toByteArray()

            coEvery { configurationPort.existsByTenantId(command.tenantId) } returns false
            every { credentialEncryptor.encrypt(command.password) } returns encryptedPassword
            coEvery { configurationPort.save(any()) } returns VmwareConfigurationError.PersistenceFailure(
                message = "Database error"
            ).failure()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is CreateVmwareConfigError.PersistenceFailure)
            val error = failure.error as CreateVmwareConfigError.PersistenceFailure
            assertEquals("Database error", error.message)
        }

        @Test
        @DisplayName("should create configuration with custom folder path")
        fun `should create configuration with custom folder path`() = runTest {
            // Given
            val command = createCommand(folderPath = "/VMs/Production")
            val encryptedPassword = "encrypted".toByteArray()

            coEvery { configurationPort.existsByTenantId(command.tenantId) } returns false
            every { credentialEncryptor.encrypt(command.password) } returns encryptedPassword
            coEvery { configurationPort.save(any()) } returns Unit.success()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            coVerify(exactly = 1) { configurationPort.save(match { it.folderPath == "/VMs/Production" }) }
        }

        @Test
        @DisplayName("should create configuration with custom template name")
        fun `should create configuration with custom template name`() = runTest {
            // Given
            val command = createCommand(templateName = "windows-server-2022")
            val encryptedPassword = "encrypted".toByteArray()

            coEvery { configurationPort.existsByTenantId(command.tenantId) } returns false
            every { credentialEncryptor.encrypt(command.password) } returns encryptedPassword
            coEvery { configurationPort.save(any()) } returns Unit.success()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            coVerify(exactly = 1) { configurationPort.save(match { it.templateName == "windows-server-2022" }) }
        }
    }
}
