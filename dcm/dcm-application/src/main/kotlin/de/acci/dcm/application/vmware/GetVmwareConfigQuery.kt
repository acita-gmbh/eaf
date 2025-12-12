package de.acci.dcm.application.vmware

import de.acci.dcm.domain.vmware.VmwareConfigurationId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

/**
 * Query to retrieve VMware configuration for a tenant.
 *
 * ## Usage
 *
 * ```kotlin
 * val query = GetVmwareConfigQuery(tenantId = TenantId.fromString("..."))
 * val result = handler.handle(query)
 * result.fold(
 *     onSuccess = { config ->
 *         println("vCenter: ${config.vcenterUrl}")
 *         println("Version: ${config.version}") // Use for optimistic locking
 *     },
 *     onFailure = { error ->
 *         when (error) {
 *             is GetVmwareConfigError.NotFound ->
 *                 println("No VMware configuration - redirect to setup")
 *         }
 *     }
 * )
 * ```
 *
 * @property tenantId The tenant to get configuration for
 */
public data class GetVmwareConfigQuery(
    val tenantId: TenantId
)

/**
 * Errors that can occur when querying VMware configuration.
 */
public sealed class GetVmwareConfigError {

    /**
     * No configuration exists for this tenant.
     * This is expected for new tenants - should redirect to setup flow.
     */
    public data class NotFound(
        val tenantId: TenantId,
        val message: String = "VMware configuration not found. Please configure vCenter connection."
    ) : GetVmwareConfigError()

    /**
     * Access denied (different tenant attempted access).
     * Returns NotFound-style response to prevent tenant enumeration.
     */
    public data class Forbidden(
        val message: String = "Not authorized to view this configuration"
    ) : GetVmwareConfigError()

    /**
     * Unexpected query failure.
     */
    public data class QueryFailure(
        val message: String
    ) : GetVmwareConfigError()
}

/**
 * VMware configuration data returned from query.
 *
 * Note: Password is NOT included in the response - it's only used internally
 * for vSphere API calls and is never exposed via API.
 *
 * @property id Configuration ID
 * @property tenantId Tenant this belongs to
 * @property vcenterUrl vCenter SDK URL
 * @property username Service account username
 * @property datacenterName vSphere datacenter name
 * @property clusterName vSphere cluster name
 * @property datastoreName Default datastore name
 * @property networkName Default network name
 * @property templateName VM template name
 * @property folderPath Optional VM folder path
 * @property verifiedAt Last successful connection test (null if never verified)
 * @property createdAt When configuration was created
 * @property updatedAt When configuration was last modified
 * @property createdBy User who created configuration
 * @property updatedBy User who last modified configuration
 * @property version Current version (use for optimistic locking in updates)
 */
public data class VmwareConfigResponse(
    val id: VmwareConfigurationId,
    val tenantId: TenantId,
    val vcenterUrl: String,
    val username: String,
    // PASSWORD INTENTIONALLY OMITTED - never exposed via API
    val datacenterName: String,
    val clusterName: String,
    val datastoreName: String,
    val networkName: String,
    val templateName: String,
    val folderPath: String?,
    val verifiedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: UserId,
    val updatedBy: UserId,
    val version: Long
)

/**
 * Handler for GetVmwareConfigQuery.
 *
 * Retrieves the VMware configuration for a tenant. The password is never
 * included in the response for security reasons.
 *
 * ## Security
 *
 * - Password excluded from response
 * - Tenant isolation enforced via RLS
 * - NotFound returned for both missing and unauthorized access (prevents enumeration)
 */
public class GetVmwareConfigHandler(
    private val configurationPort: VmwareConfigurationPort
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handle the get VMware configuration query.
     *
     * @param query The query to process
     * @return Result containing configuration (without password) or an error
     */
    public suspend fun handle(
        query: GetVmwareConfigQuery
    ): Result<VmwareConfigResponse, GetVmwareConfigError> {
        return try {
            val configuration = configurationPort.findByTenantId(query.tenantId)
                ?: return GetVmwareConfigError.NotFound(
                    tenantId = query.tenantId
                ).failure()

            // Map to response (excluding password)
            VmwareConfigResponse(
                id = configuration.id,
                tenantId = configuration.tenantId,
                vcenterUrl = configuration.vcenterUrl,
                username = configuration.username,
                datacenterName = configuration.datacenterName,
                clusterName = configuration.clusterName,
                datastoreName = configuration.datastoreName,
                networkName = configuration.networkName,
                templateName = configuration.templateName,
                folderPath = configuration.folderPath,
                verifiedAt = configuration.verifiedAt,
                createdAt = configuration.createdAt,
                updatedAt = configuration.updatedAt,
                createdBy = configuration.createdBy,
                updatedBy = configuration.updatedBy,
                version = configuration.version
            ).success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to query VMware configuration for tenant ${query.tenantId.value}"
            }
            GetVmwareConfigError.QueryFailure(
                message = "Failed to retrieve configuration: ${e.message}"
            ).failure()
        }
    }
}

/**
 * Query to check if VMware configuration exists for a tenant.
 *
 * Lightweight query for AC-3.1.5 (Missing Configuration Warning).
 * Returns only a boolean, avoiding full configuration load.
 *
 * @property tenantId The tenant to check
 */
public data class CheckVmwareConfigExistsQuery(
    val tenantId: TenantId
)

/**
 * Handler for CheckVmwareConfigExistsQuery.
 *
 * Simple existence check used by the VM Request form to determine
 * whether to show the "VMware not configured" warning.
 */
public class CheckVmwareConfigExistsHandler(
    private val configurationPort: VmwareConfigurationPort
) {
    /**
     * Check if VMware configuration exists for the tenant.
     *
     * @param query The query to process
     * @return True if configuration exists, false otherwise
     */
    public suspend fun handle(
        query: CheckVmwareConfigExistsQuery
    ): Boolean = configurationPort.existsByTenantId(query.tenantId)
}
