package de.acci.dvmm.application.vmware

import de.acci.dvmm.domain.vmware.VmwareConfiguration
import de.acci.dvmm.domain.vmware.VmwareConfigurationId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Clock
import java.time.Instant

/**
 * Command to create a new VMware vCenter configuration for a tenant.
 *
 * Each tenant can have exactly one VMware configuration. If a configuration
 * already exists, use [UpdateVmwareConfigCommand] instead.
 *
 * ## Usage
 *
 * ```kotlin
 * val command = CreateVmwareConfigCommand(
 *     tenantId = TenantId.fromString("..."),
 *     userId = UserId.fromString("..."),
 *     vcenterUrl = "https://vcenter.example.com/sdk",
 *     username = "svc-dvmm@vsphere.local",
 *     password = "secret", // Plaintext - will be encrypted by handler
 *     datacenterName = "DC1",
 *     clusterName = "Cluster1",
 *     datastoreName = "SSD-Tier1",
 *     networkName = "VM-Network"
 * )
 * val result = handler.handle(command)
 * ```
 *
 * @property tenantId Tenant this configuration belongs to
 * @property userId Admin user creating the configuration
 * @property vcenterUrl vCenter SDK URL (must be HTTPS)
 * @property username Service account username for vCenter
 * @property password Plaintext password (encrypted before storage)
 * @property datacenterName vSphere datacenter name
 * @property clusterName vSphere cluster name
 * @property datastoreName Default datastore name
 * @property networkName Default network name
 * @property templateName VM template name (defaults to ubuntu-22.04-template)
 * @property folderPath Optional VM folder path for organization
 */
public data class CreateVmwareConfigCommand(
    val tenantId: TenantId,
    val userId: UserId,
    val vcenterUrl: String,
    val username: String,
    val password: String, // Plaintext - encrypted by handler
    val datacenterName: String,
    val clusterName: String,
    val datastoreName: String,
    val networkName: String,
    val templateName: String = VmwareConfiguration.DEFAULT_TEMPLATE_NAME,
    val folderPath: String? = null
)

/**
 * Errors that can occur when creating VMware configuration.
 */
public sealed class CreateVmwareConfigError {

    /**
     * Configuration already exists for this tenant.
     * Use UpdateVmwareConfigCommand to modify existing configuration.
     */
    public data class ConfigurationAlreadyExists(
        val tenantId: TenantId,
        val message: String = "VMware configuration already exists for tenant. Use update instead."
    ) : CreateVmwareConfigError()

    /**
     * Password encryption failed.
     */
    public data class EncryptionFailed(
        val message: String
    ) : CreateVmwareConfigError()

    /**
     * Database persistence failed.
     */
    public data class PersistenceFailure(
        val message: String
    ) : CreateVmwareConfigError()
}

/**
 * Result of successfully creating VMware configuration.
 */
public data class CreateVmwareConfigResult(
    val configurationId: VmwareConfigurationId
)

/**
 * Handler for CreateVmwareConfigCommand.
 *
 * Creates a new VMware configuration for a tenant after:
 * 1. Verifying no configuration exists for the tenant
 * 2. Encrypting the password using AES-256
 * 3. Persisting to the database with tenant isolation
 *
 * ## Security
 *
 * - Password is encrypted before storage (AES-256)
 * - Tenant isolation enforced via RLS
 * - Only admin users should invoke this (enforced at API layer)
 */
public class CreateVmwareConfigHandler(
    private val configurationPort: VmwareConfigurationPort,
    private val credentialEncryptor: CredentialEncryptor,
    private val clock: Clock = Clock.systemUTC()
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handle the create VMware configuration command.
     *
     * @param command The command to process
     * @return Result containing the created configuration ID or an error
     */
    public suspend fun handle(
        command: CreateVmwareConfigCommand
    ): Result<CreateVmwareConfigResult, CreateVmwareConfigError> {
        logger.debug {
            "Creating VMware configuration: " +
                "tenantId=${command.tenantId.value}, " +
                "vcenterUrl=${command.vcenterUrl}, " +
                "userId=${command.userId.value}"
        }

        // Check if configuration already exists
        if (configurationPort.existsByTenantId(command.tenantId)) {
            logger.info {
                "VMware configuration already exists for tenant ${command.tenantId.value}"
            }
            return CreateVmwareConfigError.ConfigurationAlreadyExists(
                tenantId = command.tenantId
            ).failure()
        }

        // Encrypt the password
        val encryptedPassword = try {
            credentialEncryptor.encrypt(command.password)
        } catch (e: EncryptionException) {
            logger.error(e) {
                "Failed to encrypt password for tenant ${command.tenantId.value}"
            }
            return CreateVmwareConfigError.EncryptionFailed(
                message = "Failed to encrypt credentials: ${e.message}"
            ).failure()
        }

        // Create the configuration
        val now = Instant.now(clock)
        val configuration = VmwareConfiguration.create(
            tenantId = command.tenantId,
            vcenterUrl = command.vcenterUrl,
            username = command.username,
            passwordEncrypted = encryptedPassword,
            datacenterName = command.datacenterName,
            clusterName = command.clusterName,
            datastoreName = command.datastoreName,
            networkName = command.networkName,
            templateName = command.templateName,
            folderPath = command.folderPath,
            userId = command.userId,
            timestamp = now
        )

        // Persist to database
        val saveResult = configurationPort.save(configuration)

        return when (saveResult) {
            is Result.Success -> {
                logger.info {
                    "Created VMware configuration: " +
                        "id=${configuration.id.value}, " +
                        "tenantId=${command.tenantId.value}"
                }
                CreateVmwareConfigResult(configurationId = configuration.id).success()
            }
            is Result.Failure -> {
                logger.error {
                    "Failed to save VMware configuration for tenant ${command.tenantId.value}: " +
                        saveResult.error
                }
                when (val error = saveResult.error) {
                    is VmwareConfigurationError.AlreadyExists -> {
                        CreateVmwareConfigError.ConfigurationAlreadyExists(
                            tenantId = command.tenantId
                        ).failure()
                    }
                    is VmwareConfigurationError.PersistenceFailure -> {
                        CreateVmwareConfigError.PersistenceFailure(
                            message = error.message
                        ).failure()
                    }
                    is VmwareConfigurationError.NotFound,
                    is VmwareConfigurationError.ConcurrencyConflict -> {
                        // Unexpected errors during create
                        CreateVmwareConfigError.PersistenceFailure(
                            message = "Unexpected error: $error"
                        ).failure()
                    }
                }
            }
        }
    }
}
