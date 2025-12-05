package de.acci.dvmm.application.vmware

import de.acci.dvmm.domain.vmware.VmwareConfiguration
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import io.mockk.coEvery
import io.mockk.coVerify
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

@DisplayName("TestVmwareConnectionHandler")
class TestVmwareConnectionHandlerTest {

    private val vspherePort = mockk<VspherePort>()
    private val configurationPort = mockk<VmwareConfigurationPort>()
    private val credentialEncryptor = mockk<CredentialEncryptor>()
    private val fixedInstant = Instant.parse("2025-01-15T10:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private val handler = TestVmwareConnectionHandler(
        vspherePort = vspherePort,
        configurationPort = configurationPort,
        credentialEncryptor = credentialEncryptor,
        clock = clock
    )

    private val testTenantId = TenantId.generate()
    private val testUserId = UserId.generate()

    private fun createCommand(
        tenantId: TenantId = testTenantId,
        userId: UserId = testUserId,
        vcenterUrl: String = "https://vcenter.example.com/sdk",
        username: String = "admin@vsphere.local",
        password: String? = "secret123", // Nullable for stored password tests
        datacenterName: String = "DC1",
        clusterName: String = "Cluster1",
        datastoreName: String = "Datastore1",
        networkName: String = "VM-Network",
        templateName: String = VmwareConfiguration.DEFAULT_TEMPLATE_NAME,
        updateVerifiedAt: Boolean = false
    ) = TestVmwareConnectionCommand(
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
        updateVerifiedAt = updateVerifiedAt
    )

    private fun createConnectionInfo(
        vcenterVersion: String = "8.0.2",
        clusterName: String = "Cluster1",
        clusterHosts: Int = 3,
        datastoreFreeGb: Long = 500L
    ) = ConnectionInfo(
        vcenterVersion = vcenterVersion,
        clusterName = clusterName,
        clusterHosts = clusterHosts,
        datastoreFreeGb = datastoreFreeGb
    )

    @Nested
    @DisplayName("handle()")
    inner class HandleTests {

        @Test
        @DisplayName("should test connection successfully with provided password")
        fun `should test connection successfully with provided password`() = runTest {
            // Given
            val command = createCommand(password = "secret123")
            val connectionInfo = createConnectionInfo()

            coEvery { vspherePort.testConnection(any(), any()) } returns connectionInfo.success()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            val success = result as Result.Success
            assertEquals("8.0.2", success.value.vcenterVersion)
            assertEquals("Cluster1", success.value.clusterName)
            assertEquals(3, success.value.clusterHosts)
            assertEquals(500L, success.value.datastoreFreeGb)
            assertTrue(success.value.message.contains("Connected to vCenter"))

            coVerify(exactly = 1) { vspherePort.testConnection(any(), eq("secret123")) }
            coVerify(exactly = 0) { configurationPort.findByTenantId(any()) } // Not needed when password provided
        }

        @Test
        @DisplayName("should use stored password when password is null")
        fun `should use stored password when password is null`() = runTest {
            // Given
            val encryptedPassword = "encrypted-data".toByteArray()
            val decryptedPassword = "stored-secret"
            val existingConfig = VmwareConfiguration.create(
                tenantId = testTenantId,
                vcenterUrl = "https://vcenter.example.com/sdk",
                username = "admin@vsphere.local",
                passwordEncrypted = encryptedPassword,
                datacenterName = "DC1",
                clusterName = "Cluster1",
                datastoreName = "Datastore1",
                networkName = "VM-Network",
                templateName = VmwareConfiguration.DEFAULT_TEMPLATE_NAME,
                folderPath = null,
                userId = testUserId,
                timestamp = Instant.parse("2025-01-01T00:00:00Z")
            )
            val command = createCommand(password = null) // No password provided
            val connectionInfo = createConnectionInfo()

            coEvery { configurationPort.findByTenantId(testTenantId) } returns existingConfig
            coEvery { credentialEncryptor.decrypt(encryptedPassword) } returns decryptedPassword
            coEvery { vspherePort.testConnection(any(), any()) } returns connectionInfo.success()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            coVerify(exactly = 1) { configurationPort.findByTenantId(testTenantId) }
            coVerify(exactly = 1) { credentialEncryptor.decrypt(encryptedPassword) }
            coVerify(exactly = 1) { vspherePort.testConnection(any(), eq(decryptedPassword)) }
        }

        @Test
        @DisplayName("should return PasswordRequired error when no password and no stored config")
        fun `should return PasswordRequired error when no password and no stored config`() = runTest {
            // Given
            val command = createCommand(password = null)

            coEvery { configurationPort.findByTenantId(testTenantId) } returns null

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is TestVmwareConnectionError.PasswordRequired)
            coVerify(exactly = 0) { vspherePort.testConnection(any(), any()) }
        }

        @Test
        @DisplayName("should return PasswordRequired error when decryption fails")
        fun `should return PasswordRequired error when decryption fails`() = runTest {
            // Given - DecryptionException results in null password
            val encryptedPassword = "corrupted-data".toByteArray()
            val existingConfig = VmwareConfiguration.create(
                tenantId = testTenantId,
                vcenterUrl = "https://vcenter.example.com/sdk",
                username = "admin@vsphere.local",
                passwordEncrypted = encryptedPassword,
                datacenterName = "DC1",
                clusterName = "Cluster1",
                datastoreName = "Datastore1",
                networkName = "VM-Network",
                templateName = VmwareConfiguration.DEFAULT_TEMPLATE_NAME,
                folderPath = null,
                userId = testUserId,
                timestamp = Instant.parse("2025-01-01T00:00:00Z")
            )
            val command = createCommand(password = null)

            coEvery { configurationPort.findByTenantId(testTenantId) } returns existingConfig
            coEvery { credentialEncryptor.decrypt(encryptedPassword) } throws DecryptionException("Key mismatch")

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is TestVmwareConnectionError.PasswordRequired)
            coVerify(exactly = 0) { vspherePort.testConnection(any(), any()) }
        }

        @Test
        @DisplayName("should update verifiedAt timestamp when flag is set and return true")
        fun `should update verifiedAt timestamp when flag is set and return true`() = runTest {
            // Given
            val command = createCommand(updateVerifiedAt = true)
            val connectionInfo = createConnectionInfo()
            val existingConfig = VmwareConfiguration.create(
                tenantId = testTenantId,
                vcenterUrl = command.vcenterUrl,
                username = command.username,
                passwordEncrypted = "encrypted".toByteArray(),
                datacenterName = command.datacenterName,
                clusterName = command.clusterName,
                datastoreName = command.datastoreName,
                networkName = command.networkName,
                templateName = command.templateName,
                folderPath = null,
                userId = testUserId,
                timestamp = Instant.parse("2025-01-01T00:00:00Z")
            )

            coEvery { vspherePort.testConnection(any(), any()) } returns connectionInfo.success()
            coEvery { configurationPort.findByTenantId(testTenantId) } returns existingConfig
            coEvery { configurationPort.update(any()) } returns Unit.success()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            val success = result as Result.Success
            assertEquals(true, success.value.verifiedAtUpdated)
            coVerify(exactly = 1) { configurationPort.findByTenantId(testTenantId) }
            coVerify(exactly = 1) { configurationPort.update(match { it.verifiedAt == fixedInstant }) }
        }

        @Test
        @DisplayName("should not update verifiedAt when flag is false and return null")
        fun `should not update verifiedAt when flag is false and return null`() = runTest {
            // Given
            val command = createCommand(updateVerifiedAt = false)
            val connectionInfo = createConnectionInfo()

            coEvery { vspherePort.testConnection(any(), any()) } returns connectionInfo.success()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            val success = result as Result.Success
            assertEquals(null, success.value.verifiedAtUpdated)
            coVerify(exactly = 0) { configurationPort.findByTenantId(any()) }
            coVerify(exactly = 0) { configurationPort.update(any()) }
        }

        @Test
        @DisplayName("should return error on network failure")
        fun `should return error on network failure`() = runTest {
            // Given
            val command = createCommand()
            coEvery { vspherePort.testConnection(any(), any()) } returns ConnectionError.NetworkError(
                message = "Connection refused"
            ).failure()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is TestVmwareConnectionError.ConnectionRefused)
            val error = failure.error as TestVmwareConnectionError.ConnectionRefused
            assertTrue(error.message.contains("Connection refused"))
        }

        @Test
        @DisplayName("should return error on SSL failure")
        fun `should return error on SSL failure`() = runTest {
            // Given
            val command = createCommand()
            coEvery { vspherePort.testConnection(any(), any()) } returns ConnectionError.SslError(
                message = "Certificate not trusted"
            ).failure()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is TestVmwareConnectionError.SslCertificateError)
        }

        @Test
        @DisplayName("should return error on authentication failure")
        fun `should return error on authentication failure`() = runTest {
            // Given
            val command = createCommand()
            coEvery { vspherePort.testConnection(any(), any()) } returns ConnectionError.AuthenticationFailed(
                message = "Invalid credentials"
            ).failure()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is TestVmwareConnectionError.AuthenticationFailed)
        }

        @Test
        @DisplayName("should return error when datacenter not found")
        fun `should return error when datacenter not found`() = runTest {
            // Given
            val command = createCommand()
            coEvery { vspherePort.testConnection(any(), any()) } returns ConnectionError.DatacenterNotFound(
                datacenterName = "DC1"
            ).failure()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is TestVmwareConnectionError.DatacenterNotFound)
            val error = failure.error as TestVmwareConnectionError.DatacenterNotFound
            assertEquals("DC1", error.datacenterName)
        }

        @Test
        @DisplayName("should return error when cluster not found")
        fun `should return error when cluster not found`() = runTest {
            // Given
            val command = createCommand()
            coEvery { vspherePort.testConnection(any(), any()) } returns ConnectionError.ClusterNotFound(
                clusterName = "Cluster1"
            ).failure()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is TestVmwareConnectionError.ClusterNotFound)
        }

        @Test
        @DisplayName("should return error when datastore not found")
        fun `should return error when datastore not found`() = runTest {
            // Given
            val command = createCommand()
            coEvery { vspherePort.testConnection(any(), any()) } returns ConnectionError.DatastoreNotFound(
                datastoreName = "Datastore1"
            ).failure()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is TestVmwareConnectionError.DatastoreNotFound)
        }

        @Test
        @DisplayName("should return error when network not found")
        fun `should return error when network not found`() = runTest {
            // Given
            val command = createCommand()
            coEvery { vspherePort.testConnection(any(), any()) } returns ConnectionError.NetworkNotFound(
                networkName = "VM-Network"
            ).failure()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is TestVmwareConnectionError.NetworkNotFound)
        }

        @Test
        @DisplayName("should return error when template not found")
        fun `should return error when template not found`() = runTest {
            // Given
            val command = createCommand()
            coEvery { vspherePort.testConnection(any(), any()) } returns ConnectionError.TemplateNotFound(
                templateName = "ubuntu-22.04-template"
            ).failure()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is TestVmwareConnectionError.TemplateNotFound)
        }

        @Test
        @DisplayName("should return error on API failure")
        fun `should return error on API failure`() = runTest {
            // Given
            val command = createCommand()
            coEvery { vspherePort.testConnection(any(), any()) } returns ConnectionError.ApiError(
                message = "vSphere API error"
            ).failure()

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is TestVmwareConnectionError.ApiError)
        }

        @Test
        @DisplayName("should return false for verifiedAtUpdated when update fails")
        fun `should return false for verifiedAtUpdated when update fails`() = runTest {
            // Given
            val command = createCommand(updateVerifiedAt = true)
            val connectionInfo = createConnectionInfo()
            val existingConfig = VmwareConfiguration.create(
                tenantId = testTenantId,
                vcenterUrl = command.vcenterUrl,
                username = command.username,
                passwordEncrypted = "encrypted".toByteArray(),
                datacenterName = command.datacenterName,
                clusterName = command.clusterName,
                datastoreName = command.datastoreName,
                networkName = command.networkName,
                templateName = command.templateName,
                folderPath = null,
                userId = testUserId,
                timestamp = Instant.parse("2025-01-01T00:00:00Z")
            )

            coEvery { vspherePort.testConnection(any(), any()) } returns connectionInfo.success()
            coEvery { configurationPort.findByTenantId(testTenantId) } returns existingConfig
            coEvery { configurationPort.update(any()) } returns VmwareConfigurationError.PersistenceFailure(
                message = "Update failed"
            ).failure()

            // When
            val result = handler.handle(command)

            // Then - connection test still succeeds but verifiedAtUpdated is false
            assertTrue(result is Result.Success)
            val success = result as Result.Success
            assertEquals(false, success.value.verifiedAtUpdated)
        }

        @Test
        @DisplayName("should return false for verifiedAtUpdated when findByTenantId throws exception")
        fun `should return false for verifiedAtUpdated when findByTenantId throws exception`() = runTest {
            // Given
            val command = createCommand(updateVerifiedAt = true)
            val connectionInfo = createConnectionInfo()

            coEvery { vspherePort.testConnection(any(), any()) } returns connectionInfo.success()
            coEvery { configurationPort.findByTenantId(testTenantId) } throws RuntimeException("DB error")

            // When
            val result = handler.handle(command)

            // Then - connection test still succeeds but verifiedAtUpdated is false
            assertTrue(result is Result.Success)
            val success = result as Result.Success
            assertEquals(false, success.value.verifiedAtUpdated)
        }
    }
}
