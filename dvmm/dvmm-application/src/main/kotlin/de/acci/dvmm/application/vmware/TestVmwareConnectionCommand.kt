package de.acci.dvmm.application.vmware

import de.acci.dvmm.domain.vmware.VcenterConnectionParams
import de.acci.dvmm.domain.vmware.VmwareConfiguration
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Clock
import java.time.Instant

/**
 * Command to test VMware vCenter connection with provided configuration.
 *
 * This command validates that the VMware configuration is correct by:
 * 1. Connecting to vCenter
 * 2. Authenticating with credentials
 * 3. Verifying datacenter, cluster, datastore, and network exist
 *
 * ## Usage
 *
 * ```kotlin
 * val command = TestVmwareConnectionCommand(
 *     tenantId = TenantId.fromString("..."),
 *     userId = UserId.fromString("..."),
 *     vcenterUrl = "https://vcenter.example.com/sdk",
 *     username = "svc-dvmm@vsphere.local",
 *     password = "secret",
 *     datacenterName = "DC1",
 *     clusterName = "Cluster1",
 *     datastoreName = "SSD-Tier1",
 *     networkName = "VM-Network"
 * )
 * val result = handler.handle(command)
 * ```
 *
 * @property tenantId Tenant making the request
 * @property userId Admin user testing connection
 * @property vcenterUrl vCenter SDK URL
 * @property username vCenter username
 * @property password vCenter password (plaintext)
 * @property datacenterName Datacenter to validate
 * @property clusterName Cluster to validate
 * @property datastoreName Datastore to validate
 * @property networkName Network to validate
 * @property templateName Optional template to validate
 * @property updateVerifiedAt If true and using existing config, updates verifiedAt timestamp
 */
public data class TestVmwareConnectionCommand(
    val tenantId: TenantId,
    val userId: UserId,
    val vcenterUrl: String,
    val username: String,
    val password: String?, // Null = use stored password (requires existing config)
    val datacenterName: String,
    val clusterName: String,
    val datastoreName: String,
    val networkName: String,
    val templateName: String = VmwareConfiguration.DEFAULT_TEMPLATE_NAME,
    val updateVerifiedAt: Boolean = false
) {
    /**
     * Custom toString() that redacts password to prevent accidental logging.
     */
    override fun toString(): String = "TestVmwareConnectionCommand(" +
        "tenantId=$tenantId, " +
        "userId=$userId, " +
        "vcenterUrl=$vcenterUrl, " +
        "username=$username, " +
        "password=[REDACTED], " +
        "datacenterName=$datacenterName, " +
        "clusterName=$clusterName, " +
        "datastoreName=$datastoreName, " +
        "networkName=$networkName, " +
        "templateName=$templateName, " +
        "updateVerifiedAt=$updateVerifiedAt)"
}

/**
 * Errors that can occur when testing VMware connection.
 */
public sealed class TestVmwareConnectionError {

    /**
     * Connection failed due to network issues.
     */
    public data class ConnectionRefused(
        val message: String
    ) : TestVmwareConnectionError()

    /**
     * SSL/TLS certificate validation failed.
     */
    public data class SslCertificateError(
        val message: String
    ) : TestVmwareConnectionError()

    /**
     * Authentication failed.
     */
    public data class AuthenticationFailed(
        val message: String = "Authentication failed - check username and password"
    ) : TestVmwareConnectionError()

    /**
     * Datacenter not found in vCenter.
     */
    public data class DatacenterNotFound(
        val datacenterName: String
    ) : TestVmwareConnectionError()

    /**
     * Cluster not found in datacenter.
     */
    public data class ClusterNotFound(
        val clusterName: String
    ) : TestVmwareConnectionError()

    /**
     * Datastore not found or inaccessible.
     */
    public data class DatastoreNotFound(
        val datastoreName: String
    ) : TestVmwareConnectionError()

    /**
     * Network not found.
     */
    public data class NetworkNotFound(
        val networkName: String
    ) : TestVmwareConnectionError()

    /**
     * Template not found.
     */
    public data class TemplateNotFound(
        val templateName: String
    ) : TestVmwareConnectionError()

    /**
     * Unexpected API error.
     */
    public data class ApiError(
        val message: String
    ) : TestVmwareConnectionError()

    /**
     * No password provided and no stored configuration exists.
     */
    public data class PasswordRequired(
        val message: String = "Password is required - no stored configuration exists"
    ) : TestVmwareConnectionError()

    /**
     * Failed to decrypt stored password.
     */
    public data class DecryptionFailed(
        val message: String
    ) : TestVmwareConnectionError()
}

/**
 * Result of a successful connection test.
 *
 * @property vcenterVersion vCenter Server version
 * @property clusterName Validated cluster name
 * @property clusterHosts Number of ESXi hosts
 * @property datastoreFreeGb Free space in GB
 * @property message Human-readable success message
 * @property verifiedAtUpdated True if verifiedAt timestamp was updated (only when requested),
 *                             null if update was not requested, false if update failed
 */
public data class TestVmwareConnectionResult(
    val vcenterVersion: String,
    val clusterName: String,
    val clusterHosts: Int,
    val datastoreFreeGb: Long,
    val message: String,
    val verifiedAtUpdated: Boolean? = null
)

/**
 * Handler for TestVmwareConnectionCommand.
 *
 * Tests vCenter connectivity and validates infrastructure components.
 * On success, can optionally update the verifiedAt timestamp if config exists.
 *
 * ## Password Resolution
 *
 * If [TestVmwareConnectionCommand.password] is null or blank:
 * 1. Fetches the stored configuration for the tenant
 * 2. Decrypts the stored password
 * 3. Uses it for the connection test
 *
 * This allows testing with stored credentials without re-entering the password.
 */
public class TestVmwareConnectionHandler(
    private val vspherePort: VspherePort,
    private val configurationPort: VmwareConfigurationPort,
    private val credentialEncryptor: CredentialEncryptor,
    private val clock: Clock = Clock.systemUTC()
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handle the test connection command.
     *
     * @param command The command to process
     * @return Result containing connection info or an error
     */
    public suspend fun handle(
        command: TestVmwareConnectionCommand
    ): Result<TestVmwareConnectionResult, TestVmwareConnectionError> {
        logger.debug {
            "Testing VMware connection: " +
                "vcenterUrl=${command.vcenterUrl}, " +
                "datacenter=${command.datacenterName}, " +
                "cluster=${command.clusterName}, " +
                "useStoredPassword=${command.password.isNullOrBlank()}"
        }

        // Resolve password: use provided password or fetch from stored config
        val resolvedPassword = resolvePassword(command)
            ?: return TestVmwareConnectionError.PasswordRequired().failure()

        // Create connection parameters value object (no entity concerns - just connection params)
        val connectionParams = VcenterConnectionParams(
            vcenterUrl = command.vcenterUrl,
            username = command.username,
            datacenterName = command.datacenterName,
            clusterName = command.clusterName,
            datastoreName = command.datastoreName,
            networkName = command.networkName,
            templateName = command.templateName,
            folderPath = null
        )

        // Test the connection
        val connectionResult = vspherePort.testConnection(
            params = connectionParams,
            password = resolvedPassword
        )

        return when (connectionResult) {
            is Result.Success -> {
                val info = connectionResult.value
                logger.info {
                    "VMware connection test successful: " +
                        "vcenter=${info.vcenterVersion}, " +
                        "cluster=${info.clusterName}, " +
                        "hosts=${info.clusterHosts}"
                }

                // Optionally update verifiedAt if config exists and flag is set
                val verifiedAtUpdated = if (command.updateVerifiedAt) {
                    updateVerifiedTimestamp(command.tenantId, command.userId)
                } else {
                    null
                }

                TestVmwareConnectionResult(
                    vcenterVersion = info.vcenterVersion,
                    clusterName = info.clusterName,
                    clusterHosts = info.clusterHosts,
                    datastoreFreeGb = info.datastoreFreeGb,
                    message = "Connected to vCenter ${info.vcenterVersion}, Cluster: ${info.clusterName}",
                    verifiedAtUpdated = verifiedAtUpdated
                ).success()
            }
            is Result.Failure -> {
                val error = connectionResult.error
                logger.warn { "VMware connection test failed: $error" }
                mapConnectionError(error).failure()
            }
        }
    }

    /**
     * Maps VspherePort errors to command-level errors.
     */
    private fun mapConnectionError(error: ConnectionError): TestVmwareConnectionError = when (error) {
        is ConnectionError.NetworkError -> TestVmwareConnectionError.ConnectionRefused(
            message = "Connection refused: ${error.message}"
        )
        is ConnectionError.SslError -> TestVmwareConnectionError.SslCertificateError(
            message = "SSL certificate error: ${error.message}"
        )
        is ConnectionError.AuthenticationFailed -> TestVmwareConnectionError.AuthenticationFailed(
            message = error.message
        )
        is ConnectionError.DatacenterNotFound -> TestVmwareConnectionError.DatacenterNotFound(
            datacenterName = error.datacenterName
        )
        is ConnectionError.ClusterNotFound -> TestVmwareConnectionError.ClusterNotFound(
            clusterName = error.clusterName
        )
        is ConnectionError.DatastoreNotFound -> TestVmwareConnectionError.DatastoreNotFound(
            datastoreName = error.datastoreName
        )
        is ConnectionError.NetworkNotFound -> TestVmwareConnectionError.NetworkNotFound(
            networkName = error.networkName
        )
        is ConnectionError.TemplateNotFound -> TestVmwareConnectionError.TemplateNotFound(
            templateName = error.templateName
        )
        is ConnectionError.ApiError -> TestVmwareConnectionError.ApiError(
            message = error.message
        )
    }

    /**
     * Updates the verifiedAt timestamp for existing configuration.
     *
     * @param tenantId Tenant whose config to update
     * @param userId User performing the verification (for audit trail)
     * @return true if update succeeded, false if update failed or config not found
     */
    private suspend fun updateVerifiedTimestamp(tenantId: TenantId, userId: UserId): Boolean {
        return try {
            val existing = configurationPort.findByTenantId(tenantId)
            if (existing != null) {
                val updated = existing.markVerified(Instant.now(clock), userId)
                when (val result = configurationPort.update(updated)) {
                    is Result.Success -> {
                        logger.debug { "Updated verifiedAt timestamp for tenant ${tenantId.value}" }
                        true
                    }
                    is Result.Failure -> {
                        logger.warn { "Failed to update verifiedAt for tenant ${tenantId.value}: ${result.error}" }
                        false
                    }
                }
            } else {
                logger.debug { "No existing config to update verifiedAt for tenant ${tenantId.value}" }
                false
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to update verifiedAt timestamp for tenant ${tenantId.value}" }
            false
        }
    }

    /**
     * Resolves the password to use for connection testing.
     *
     * If a password is provided in the command, use it.
     * Otherwise, fetch the stored configuration and decrypt the password.
     *
     * @return The resolved password, or null if password is required but not available
     */
    private suspend fun resolvePassword(command: TestVmwareConnectionCommand): String? {
        // If password provided, use it directly
        if (!command.password.isNullOrBlank()) {
            return command.password
        }

        // No password provided - try to fetch from stored config
        logger.debug { "No password provided, attempting to use stored credentials for tenant ${command.tenantId.value}" }

        val existingConfig = configurationPort.findByTenantId(command.tenantId)
        if (existingConfig == null) {
            logger.warn { "No stored configuration found for tenant ${command.tenantId.value}" }
            return null
        }

        // Decrypt the stored password
        return try {
            credentialEncryptor.decrypt(existingConfig.passwordEncrypted)
        } catch (e: DecryptionException) {
            logger.error(e) { "Failed to decrypt stored password for tenant ${command.tenantId.value}" }
            null
        }
    }
}
