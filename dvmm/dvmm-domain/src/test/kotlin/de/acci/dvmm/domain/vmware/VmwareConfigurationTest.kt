package de.acci.dvmm.domain.vmware

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

@DisplayName("VmwareConfiguration")
class VmwareConfigurationTest {

    private val testTenantId = TenantId.generate()
    private val testUserId = UserId.generate()
    private val testTimestamp = Instant.parse("2025-01-15T10:00:00Z")
    private val testPassword = "encrypted".toByteArray()

    private fun createConfig(
        vcenterUrl: String = "https://vcenter.example.com/sdk",
        username: String = "admin@vsphere.local",
        datacenterName: String = "DC1",
        clusterName: String = "Cluster1",
        datastoreName: String = "Datastore1",
        networkName: String = "VM-Network",
        templateName: String = VmwareConfiguration.DEFAULT_TEMPLATE_NAME,
        folderPath: String? = "/VMs/Production"
    ): VmwareConfiguration = VmwareConfiguration.create(
        tenantId = testTenantId,
        vcenterUrl = vcenterUrl,
        username = username,
        passwordEncrypted = testPassword,
        datacenterName = datacenterName,
        clusterName = clusterName,
        datastoreName = datastoreName,
        networkName = networkName,
        templateName = templateName,
        folderPath = folderPath,
        userId = testUserId,
        timestamp = testTimestamp
    )

    @Nested
    @DisplayName("create()")
    inner class CreateTests {

        @Test
        @DisplayName("should create configuration with valid values")
        fun `should create configuration with valid values`() {
            // When
            val config = createConfig()

            // Then
            assertEquals(testTenantId, config.tenantId)
            assertEquals("https://vcenter.example.com/sdk", config.vcenterUrl)
            assertEquals("admin@vsphere.local", config.username)
            assertTrue(config.passwordEncrypted.contentEquals(testPassword))
            assertEquals("DC1", config.datacenterName)
            assertEquals("Cluster1", config.clusterName)
            assertEquals("Datastore1", config.datastoreName)
            assertEquals("VM-Network", config.networkName)
            assertEquals(VmwareConfiguration.DEFAULT_TEMPLATE_NAME, config.templateName)
            assertEquals("/VMs/Production", config.folderPath)
            assertNull(config.verifiedAt)
            assertEquals(testTimestamp, config.createdAt)
            assertEquals(testTimestamp, config.updatedAt)
            assertEquals(testUserId, config.createdBy)
            assertEquals(testUserId, config.updatedBy)
            assertEquals(0L, config.version)
        }

        @Test
        @DisplayName("should create configuration with null folderPath")
        fun `should create configuration with null folderPath`() {
            // When
            val config = createConfig(folderPath = null)

            // Then
            assertNull(config.folderPath)
        }

        @Test
        @DisplayName("should use default template name")
        fun `should use default template name`() {
            // When
            val config = VmwareConfiguration.create(
                tenantId = testTenantId,
                vcenterUrl = "https://vcenter.example.com/sdk",
                username = "admin@vsphere.local",
                passwordEncrypted = testPassword,
                datacenterName = "DC1",
                clusterName = "Cluster1",
                datastoreName = "Datastore1",
                networkName = "VM-Network",
                userId = testUserId,
                timestamp = testTimestamp
            )

            // Then
            assertEquals("ubuntu-22.04-template", config.templateName)
        }
    }

    @Nested
    @DisplayName("validation")
    inner class ValidationTests {

        @Test
        @DisplayName("should reject blank vCenter URL")
        fun `should reject blank vCenter URL`() {
            val exception = assertThrows<IllegalArgumentException> {
                createConfig(vcenterUrl = "")
            }
            assertEquals("vCenter URL cannot be blank", exception.message)
        }

        @Test
        @DisplayName("should reject whitespace-only vCenter URL")
        fun `should reject whitespace-only vCenter URL`() {
            val exception = assertThrows<IllegalArgumentException> {
                createConfig(vcenterUrl = "   ")
            }
            assertEquals("vCenter URL cannot be blank", exception.message)
        }

        @Test
        @DisplayName("should reject vCenter URL without https")
        fun `should reject vCenter URL without https`() {
            val exception = assertThrows<IllegalArgumentException> {
                createConfig(vcenterUrl = "http://vcenter.example.com/sdk")
            }
            assertEquals("vCenter URL must start with https://", exception.message)
        }

        @Test
        @DisplayName("should reject blank username")
        fun `should reject blank username`() {
            val exception = assertThrows<IllegalArgumentException> {
                createConfig(username = "")
            }
            assertEquals("Username cannot be blank", exception.message)
        }

        @Test
        @DisplayName("should reject blank datacenter name")
        fun `should reject blank datacenter name`() {
            val exception = assertThrows<IllegalArgumentException> {
                createConfig(datacenterName = "")
            }
            assertEquals("Datacenter name cannot be blank", exception.message)
        }

        @Test
        @DisplayName("should reject blank cluster name")
        fun `should reject blank cluster name`() {
            val exception = assertThrows<IllegalArgumentException> {
                createConfig(clusterName = "")
            }
            assertEquals("Cluster name cannot be blank", exception.message)
        }

        @Test
        @DisplayName("should reject blank datastore name")
        fun `should reject blank datastore name`() {
            val exception = assertThrows<IllegalArgumentException> {
                createConfig(datastoreName = "")
            }
            assertEquals("Datastore name cannot be blank", exception.message)
        }

        @Test
        @DisplayName("should reject blank network name")
        fun `should reject blank network name`() {
            val exception = assertThrows<IllegalArgumentException> {
                createConfig(networkName = "")
            }
            assertEquals("Network name cannot be blank", exception.message)
        }

        @Test
        @DisplayName("should reject blank template name")
        fun `should reject blank template name`() {
            val exception = assertThrows<IllegalArgumentException> {
                createConfig(templateName = "")
            }
            assertEquals("Template name cannot be blank", exception.message)
        }
    }

    @Nested
    @DisplayName("update()")
    inner class UpdateTests {

        @Test
        @DisplayName("should update specific fields while keeping others")
        fun `should update specific fields while keeping others`() {
            // Given
            val original = createConfig()
            val newTimestamp = Instant.parse("2025-01-16T10:00:00Z")
            val newUserId = UserId.generate()

            // When
            val updated = original.update(
                vcenterUrl = "https://new-vcenter.example.com/sdk",
                datacenterName = "DC2",
                userId = newUserId,
                timestamp = newTimestamp
            )

            // Then
            assertEquals("https://new-vcenter.example.com/sdk", updated.vcenterUrl)
            assertEquals("DC2", updated.datacenterName)
            assertEquals(original.username, updated.username) // Unchanged
            assertEquals(original.clusterName, updated.clusterName) // Unchanged
            assertEquals(newTimestamp, updated.updatedAt)
            assertEquals(newUserId, updated.updatedBy)
            assertEquals(original.version + 1, updated.version)
            assertNull(updated.verifiedAt) // Reset on update
        }

        @Test
        @DisplayName("should reset verifiedAt on any update")
        fun `should reset verifiedAt on any update`() {
            // Given
            val original = createConfig().markVerified(Instant.parse("2025-01-14T10:00:00Z"), testUserId)
            assertEquals(Instant.parse("2025-01-14T10:00:00Z"), original.verifiedAt)

            // When
            val updated = original.update(
                vcenterUrl = "https://new-vcenter.example.com/sdk",
                userId = testUserId,
                timestamp = testTimestamp
            )

            // Then
            assertNull(updated.verifiedAt)
        }
    }

    @Nested
    @DisplayName("markVerified()")
    inner class MarkVerifiedTests {

        private val verifyingUserId = UserId.generate()

        @Test
        @DisplayName("should set verifiedAt timestamp and updatedBy")
        fun `should set verifiedAt timestamp and updatedBy`() {
            // Given
            val config = createConfig()
            val verificationTime = Instant.parse("2025-01-16T15:30:00Z")

            // When
            val verified = config.markVerified(verificationTime, verifyingUserId)

            // Then
            assertEquals(verificationTime, verified.verifiedAt)
            assertEquals(verificationTime, verified.updatedAt)
            assertEquals(verifyingUserId, verified.updatedBy)
            assertEquals(config.version + 1, verified.version)
        }

        @Test
        @DisplayName("should preserve other fields when marking verified")
        fun `should preserve other fields when marking verified`() {
            // Given
            val config = createConfig()
            val verificationTime = Instant.parse("2025-01-16T15:30:00Z")

            // When
            val verified = config.markVerified(verificationTime, verifyingUserId)

            // Then
            assertEquals(config.id, verified.id)
            assertEquals(config.tenantId, verified.tenantId)
            assertEquals(config.vcenterUrl, verified.vcenterUrl)
            assertEquals(config.username, verified.username)
            assertEquals(config.datacenterName, verified.datacenterName)
            assertEquals(config.clusterName, verified.clusterName)
            assertEquals(config.datastoreName, verified.datastoreName)
            assertEquals(config.networkName, verified.networkName)
            assertEquals(config.templateName, verified.templateName)
            assertEquals(config.folderPath, verified.folderPath)
            assertEquals(config.createdAt, verified.createdAt)
            assertEquals(config.createdBy, verified.createdBy)
            // Note: updatedBy is expected to change to verifyingUserId
            assertEquals(verifyingUserId, verified.updatedBy)
        }
    }

    @Nested
    @DisplayName("equals() and hashCode()")
    inner class EqualsHashCodeTests {

        @Test
        @DisplayName("should be equal for same configuration")
        fun `should be equal for same configuration`() {
            // Given
            val config = createConfig()

            // Then
            assertEquals(config, config)
            assertEquals(config.hashCode(), config.hashCode())
        }

        @Test
        @DisplayName("should handle ByteArray comparison correctly")
        fun `should handle ByteArray comparison correctly`() {
            // Given
            val id = VmwareConfigurationId.generate()
            val password = "test".toByteArray()

            // When - create two configs with same byte content but different arrays
            val config1 = VmwareConfiguration(
                id = id,
                tenantId = testTenantId,
                vcenterUrl = "https://vcenter.example.com/sdk",
                username = "admin",
                passwordEncrypted = password.copyOf(),
                datacenterName = "DC1",
                clusterName = "Cluster1",
                datastoreName = "Datastore1",
                networkName = "Network1",
                templateName = "template",
                folderPath = null,
                verifiedAt = null,
                createdAt = testTimestamp,
                updatedAt = testTimestamp,
                createdBy = testUserId,
                updatedBy = testUserId,
                version = 0
            )

            val config2 = VmwareConfiguration(
                id = id,
                tenantId = testTenantId,
                vcenterUrl = "https://vcenter.example.com/sdk",
                username = "admin",
                passwordEncrypted = password.copyOf(),
                datacenterName = "DC1",
                clusterName = "Cluster1",
                datastoreName = "Datastore1",
                networkName = "Network1",
                templateName = "template",
                folderPath = null,
                verifiedAt = null,
                createdAt = testTimestamp,
                updatedAt = testTimestamp,
                createdBy = testUserId,
                updatedBy = testUserId,
                version = 0
            )

            // Then
            assertEquals(config1, config2)
            assertEquals(config1.hashCode(), config2.hashCode())
        }

        @Test
        @DisplayName("should not be equal for different passwords")
        fun `should not be equal for different passwords`() {
            // Given
            val id = VmwareConfigurationId.generate()

            val config1 = VmwareConfiguration(
                id = id,
                tenantId = testTenantId,
                vcenterUrl = "https://vcenter.example.com/sdk",
                username = "admin",
                passwordEncrypted = "password1".toByteArray(),
                datacenterName = "DC1",
                clusterName = "Cluster1",
                datastoreName = "Datastore1",
                networkName = "Network1",
                templateName = "template",
                folderPath = null,
                verifiedAt = null,
                createdAt = testTimestamp,
                updatedAt = testTimestamp,
                createdBy = testUserId,
                updatedBy = testUserId,
                version = 0
            )

            val config2 = VmwareConfiguration(
                id = id,
                tenantId = testTenantId,
                vcenterUrl = "https://vcenter.example.com/sdk",
                username = "admin",
                passwordEncrypted = "password2".toByteArray(),
                datacenterName = "DC1",
                clusterName = "Cluster1",
                datastoreName = "Datastore1",
                networkName = "Network1",
                templateName = "template",
                folderPath = null,
                verifiedAt = null,
                createdAt = testTimestamp,
                updatedAt = testTimestamp,
                createdBy = testUserId,
                updatedBy = testUserId,
                version = 0
            )

            // Then
            assertNotEquals(config1, config2)
        }

        @Test
        @DisplayName("equals should handle null comparison")
        fun `equals should handle null comparison`() {
            // Given
            val config = createConfig()

            // Then
            assertFalse(config.equals(null))
        }

        @Test
        @DisplayName("equals should handle different type comparison")
        fun `equals should handle different type comparison`() {
            // Given
            val config = createConfig()

            // Then
            assertFalse(config.equals("not a config"))
        }
    }

    @Nested
    @DisplayName("toString()")
    inner class ToStringTests {

        @Test
        @DisplayName("should redact password in toString")
        fun `should redact password in toString`() {
            // Given
            val config = createConfig()

            // When
            val string = config.toString()

            // Then
            assertTrue(string.contains("[REDACTED]"))
            assertFalse(string.contains("encrypted")) // Actual password content
            assertTrue(string.contains("vcenterUrl=https://vcenter.example.com/sdk"))
            assertTrue(string.contains("username=admin@vsphere.local"))
        }
    }

    @Nested
    @DisplayName("DEFAULT_TEMPLATE_NAME")
    inner class DefaultTemplateNameTests {

        @Test
        @DisplayName("should have expected default template name")
        fun `should have expected default template name`() {
            assertEquals("ubuntu-22.04-template", VmwareConfiguration.DEFAULT_TEMPLATE_NAME)
        }
    }
}
