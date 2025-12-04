package de.acci.dvmm.application.vmware

import de.acci.dvmm.domain.vmware.VmwareConfiguration
import de.acci.dvmm.domain.vmware.VmwareConfigurationId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.TenantId

/**
 * Port (interface) for VMware configuration persistence operations.
 *
 * This is a port in hexagonal architecture terms - it defines what the
 * application layer needs from persistence without specifying implementation.
 * The infrastructure layer provides the concrete adapter (e.g., jOOQ repository).
 *
 * ## Tenant Isolation
 *
 * All operations are tenant-scoped. The implementation MUST enforce
 * tenant isolation via PostgreSQL Row-Level Security (RLS) policies.
 */
public interface VmwareConfigurationPort {

    /**
     * Find VMware configuration by tenant ID.
     *
     * Since there's exactly one configuration per tenant, this is the
     * primary lookup method.
     *
     * @param tenantId The tenant to find configuration for
     * @return The configuration if exists, null otherwise
     */
    public suspend fun findByTenantId(tenantId: TenantId): VmwareConfiguration?

    /**
     * Find VMware configuration by ID.
     *
     * @param id The configuration ID
     * @return The configuration if exists, null otherwise
     */
    public suspend fun findById(id: VmwareConfigurationId): VmwareConfiguration?

    /**
     * Save (insert) a new VMware configuration.
     *
     * @param configuration The configuration to save
     * @return Success or failure with error details
     */
    public suspend fun save(configuration: VmwareConfiguration): Result<Unit, VmwareConfigurationError>

    /**
     * Update an existing VMware configuration.
     *
     * Uses optimistic locking - the operation fails if the configuration
     * has been modified since it was loaded (version mismatch).
     *
     * @param configuration The updated configuration
     * @return Success or failure with error details
     */
    public suspend fun update(configuration: VmwareConfiguration): Result<Unit, VmwareConfigurationError>

    /**
     * Check if a configuration exists for the given tenant.
     *
     * Lightweight check without loading the full configuration.
     *
     * @param tenantId The tenant to check
     * @return True if configuration exists, false otherwise
     */
    public suspend fun existsByTenantId(tenantId: TenantId): Boolean
}

/**
 * Errors that can occur during VMware configuration persistence operations.
 */
public sealed class VmwareConfigurationError {

    /**
     * Configuration not found.
     */
    public data class NotFound(
        val id: VmwareConfigurationId? = null,
        val tenantId: TenantId? = null,
        val message: String = "VMware configuration not found"
    ) : VmwareConfigurationError()

    /**
     * Configuration already exists for the tenant.
     * Each tenant can have exactly one VMware configuration.
     */
    public data class AlreadyExists(
        val tenantId: TenantId,
        val message: String = "VMware configuration already exists for tenant ${tenantId.value}"
    ) : VmwareConfigurationError()

    /**
     * Concurrent modification detected (optimistic locking failure).
     */
    public data class ConcurrencyConflict(
        val expectedVersion: Long,
        val actualVersion: Long,
        val message: String = "Configuration was modified by another user. Expected version $expectedVersion but found $actualVersion"
    ) : VmwareConfigurationError()

    /**
     * Database or persistence failure.
     */
    public data class PersistenceFailure(
        val message: String,
        val cause: Throwable? = null
    ) : VmwareConfigurationError()
}

/**
 * No-op implementation of VmwareConfigurationPort for testing.
 *
 * Always returns null/empty results. Use MockK for richer test scenarios.
 */
public object NoOpVmwareConfigurationPort : VmwareConfigurationPort {
    override suspend fun findByTenantId(tenantId: TenantId): VmwareConfiguration? = null
    override suspend fun findById(id: VmwareConfigurationId): VmwareConfiguration? = null
    override suspend fun save(configuration: VmwareConfiguration): Result<Unit, VmwareConfigurationError> =
        Result.Success(Unit)
    override suspend fun update(configuration: VmwareConfiguration): Result<Unit, VmwareConfigurationError> =
        Result.Success(Unit)
    override suspend fun existsByTenantId(tenantId: TenantId): Boolean = false
}
