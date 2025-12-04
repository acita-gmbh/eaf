package de.acci.dvmm.application.vmware

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
 * Command to update an existing VMware vCenter configuration.
 *
 * Updates are performed using optimistic locking. The [expectedVersion] must
 * match the current version in the database, otherwise the update fails with
 * a [UpdateVmwareConfigError.ConcurrencyConflict].
 *
 * ## Password Handling
 *
 * If [password] is null, the existing encrypted password is retained.
 * If [password] is provided, it will be encrypted and replace the existing one.
 *
 * ## Usage
 *
 * ```kotlin
 * val command = UpdateVmwareConfigCommand(
 *     tenantId = TenantId.fromString("..."),
 *     userId = UserId.fromString("..."),
 *     expectedVersion = 2, // From GetVmwareConfigQuery result
 *     vcenterUrl = "https://new-vcenter.example.com/sdk",
 *     // ... other fields to update
 *     password = null // Keep existing password
 * )
 * val result = handler.handle(command)
 * ```
 *
 * @property tenantId Tenant whose configuration to update
 * @property userId Admin user performing the update
 * @property expectedVersion Version number for optimistic locking (from read)
 * @property vcenterUrl New vCenter URL (or null to keep existing)
 * @property username New username (or null to keep existing)
 * @property password New plaintext password (or null to keep existing)
 * @property datacenterName New datacenter name (or null to keep existing)
 * @property clusterName New cluster name (or null to keep existing)
 * @property datastoreName New datastore name (or null to keep existing)
 * @property networkName New network name (or null to keep existing)
 * @property templateName New template name (or null to keep existing)
 * @property folderPath New folder path (or null to keep existing)
 */
public data class UpdateVmwareConfigCommand(
    val tenantId: TenantId,
    val userId: UserId,
    val expectedVersion: Long,
    val vcenterUrl: String? = null,
    val username: String? = null,
    val password: String? = null, // Plaintext - encrypted by handler if provided
    val datacenterName: String? = null,
    val clusterName: String? = null,
    val datastoreName: String? = null,
    val networkName: String? = null,
    val templateName: String? = null,
    val folderPath: String? = null
)

/**
 * Errors that can occur when updating VMware configuration.
 */
public sealed class UpdateVmwareConfigError {

    /**
     * No configuration exists for this tenant.
     * Use CreateVmwareConfigCommand to create one first.
     */
    public data class NotFound(
        val tenantId: TenantId,
        val message: String = "VMware configuration not found for tenant. Create one first."
    ) : UpdateVmwareConfigError()

    /**
     * Optimistic locking conflict - configuration was modified by another user.
     */
    public data class ConcurrencyConflict(
        val expectedVersion: Long,
        val actualVersion: Long,
        val message: String = "Configuration was modified by another user. " +
            "Expected version $expectedVersion but found $actualVersion. " +
            "Please refresh and retry."
    ) : UpdateVmwareConfigError()

    /**
     * Password encryption failed.
     */
    public data class EncryptionFailed(
        val message: String
    ) : UpdateVmwareConfigError()

    /**
     * Database persistence failed.
     */
    public data class PersistenceFailure(
        val message: String
    ) : UpdateVmwareConfigError()
}

/**
 * Result of successfully updating VMware configuration.
 */
public data class UpdateVmwareConfigResult(
    val newVersion: Long
)

/**
 * Handler for UpdateVmwareConfigCommand.
 *
 * Updates an existing VMware configuration with:
 * 1. Optimistic locking verification
 * 2. Optional password re-encryption
 * 3. Resetting verification status (connection test required after changes)
 *
 * ## Security
 *
 * - Password encrypted using AES-256 if changed
 * - Tenant isolation enforced via RLS
 * - Only admin users should invoke this (enforced at API layer)
 */
public class UpdateVmwareConfigHandler(
    private val configurationPort: VmwareConfigurationPort,
    private val credentialEncryptor: CredentialEncryptor,
    private val clock: Clock = Clock.systemUTC()
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handle the update VMware configuration command.
     *
     * @param command The command to process
     * @return Result containing the new version or an error
     */
    public suspend fun handle(
        command: UpdateVmwareConfigCommand
    ): Result<UpdateVmwareConfigResult, UpdateVmwareConfigError> {
        logger.debug {
            "Updating VMware configuration: " +
                "tenantId=${command.tenantId.value}, " +
                "expectedVersion=${command.expectedVersion}, " +
                "userId=${command.userId.value}"
        }

        // Load existing configuration
        val existing = configurationPort.findByTenantId(command.tenantId)
            ?: return UpdateVmwareConfigError.NotFound(
                tenantId = command.tenantId
            ).failure()

        // Verify optimistic lock
        if (existing.version != command.expectedVersion) {
            logger.warn {
                "Concurrency conflict for tenant ${command.tenantId.value}: " +
                    "expected version ${command.expectedVersion}, " +
                    "actual version ${existing.version}"
            }
            return UpdateVmwareConfigError.ConcurrencyConflict(
                expectedVersion = command.expectedVersion,
                actualVersion = existing.version
            ).failure()
        }

        // Encrypt new password if provided
        val encryptedPassword = command.password?.let { plaintext ->
            try {
                credentialEncryptor.encrypt(plaintext)
            } catch (e: EncryptionException) {
                logger.error(e) {
                    "Failed to encrypt password for tenant ${command.tenantId.value}"
                }
                return UpdateVmwareConfigError.EncryptionFailed(
                    message = "Failed to encrypt credentials: ${e.message}"
                ).failure()
            }
        }

        // Create updated configuration
        val now = Instant.now(clock)
        val updated = existing.update(
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
        ).copy(version = existing.version + 1) // Increment version

        // Persist to database
        val updateResult = configurationPort.update(updated)

        return when (updateResult) {
            is Result.Success -> {
                logger.info {
                    "Updated VMware configuration: " +
                        "id=${updated.id.value}, " +
                        "tenantId=${command.tenantId.value}, " +
                        "newVersion=${updated.version}"
                }
                UpdateVmwareConfigResult(newVersion = updated.version).success()
            }
            is Result.Failure -> {
                logger.error {
                    "Failed to update VMware configuration for tenant ${command.tenantId.value}: " +
                        updateResult.error
                }
                when (val error = updateResult.error) {
                    is VmwareConfigurationError.ConcurrencyConflict -> {
                        UpdateVmwareConfigError.ConcurrencyConflict(
                            expectedVersion = error.expectedVersion,
                            actualVersion = error.actualVersion
                        ).failure()
                    }
                    is VmwareConfigurationError.NotFound -> {
                        UpdateVmwareConfigError.NotFound(
                            tenantId = command.tenantId
                        ).failure()
                    }
                    is VmwareConfigurationError.PersistenceFailure -> {
                        UpdateVmwareConfigError.PersistenceFailure(
                            message = error.message
                        ).failure()
                    }
                    is VmwareConfigurationError.AlreadyExists -> {
                        // Unexpected during update
                        UpdateVmwareConfigError.PersistenceFailure(
                            message = "Unexpected error: $error"
                        ).failure()
                    }
                }
            }
        }
    }
}
