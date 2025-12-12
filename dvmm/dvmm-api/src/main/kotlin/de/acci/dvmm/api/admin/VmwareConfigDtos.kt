package de.acci.dvmm.api.admin

import de.acci.dvmm.application.vmware.VmwareConfigResponse
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * Request body for creating or updating VMware configuration.
 *
 * ## Create vs Update
 *
 * - If [version] is null, creates a new configuration
 * - If [version] is provided, updates existing configuration with optimistic locking
 *
 * ## Password Handling
 *
 * - For create: [password] is required
 * - For update: [password] can be null to retain existing password
 *
 * @property vcenterUrl vCenter SDK URL (must start with https://)
 * @property username Service account username
 * @property password Plaintext password (encrypted before storage)
 * @property datacenterName vSphere datacenter name
 * @property clusterName vSphere cluster name
 * @property datastoreName Default datastore name
 * @property networkName Default network name
 * @property templateName VM template name (optional, defaults to ubuntu-22.04-template)
 * @property folderPath Optional VM folder path for organization
 * @property version Expected version for optimistic locking (null for create)
 */
public data class SaveVmwareConfigRequest(
    @field:NotBlank(message = "vCenter URL is required")
    @field:Pattern(
        regexp = "^https://.*",
        message = "vCenter URL must start with https://"
    )
    @field:Size(max = 500, message = "vCenter URL must not exceed 500 characters")
    val vcenterUrl: String,

    @field:NotBlank(message = "Username is required")
    @field:Size(max = 255, message = "Username must not exceed 255 characters")
    val username: String,

    // Password nullable for update (keep existing) - required validation done in controller
    @field:Size(max = 500, message = "Password must not exceed 500 characters")
    val password: String?,

    @field:NotBlank(message = "Datacenter name is required")
    @field:Size(max = 255, message = "Datacenter name must not exceed 255 characters")
    val datacenterName: String,

    @field:NotBlank(message = "Cluster name is required")
    @field:Size(max = 255, message = "Cluster name must not exceed 255 characters")
    val clusterName: String,

    @field:NotBlank(message = "Datastore name is required")
    @field:Size(max = 255, message = "Datastore name must not exceed 255 characters")
    val datastoreName: String,

    @field:NotBlank(message = "Network name is required")
    @field:Size(max = 255, message = "Network name must not exceed 255 characters")
    val networkName: String,

    @field:Size(max = 255, message = "Template name must not exceed 255 characters")
    val templateName: String? = null,

    @field:Size(max = 500, message = "Folder path must not exceed 500 characters")
    val folderPath: String? = null,

    // Set to true to explicitly clear folderPath to null (for updates only)
    val clearFolderPath: Boolean = false,

    // Null = create, non-null = update with optimistic locking
    val version: Long? = null
)

/**
 * Request body for testing VMware connection.
 *
 * Can test with:
 * 1. Explicit credentials: Provide [password] for new/changed credentials
 * 2. Stored credentials: Set [password] to null to use saved encrypted password
 *
 * When using stored credentials, the backend will:
 * 1. Fetch the existing configuration for the tenant
 * 2. Decrypt the stored password
 * 3. Use it for the connection test
 *
 * @property vcenterUrl vCenter SDK URL
 * @property username Service account username
 * @property password Password for testing (null = use stored credentials)
 * @property datacenterName Datacenter to validate
 * @property clusterName Cluster to validate
 * @property datastoreName Datastore to validate
 * @property networkName Network to validate
 * @property templateName Optional template to validate
 * @property updateVerifiedAt If true and test succeeds, update verifiedAt timestamp
 */
public data class TestVmwareConnectionRequest(
    @field:NotBlank(message = "vCenter URL is required")
    @field:Pattern(
        regexp = "^https://.*",
        message = "vCenter URL must start with https://"
    )
    val vcenterUrl: String,

    @field:NotBlank(message = "Username is required")
    val username: String,

    // Null = use stored password from existing configuration
    @field:Size(max = 500, message = "Password must not exceed 500 characters")
    val password: String?,

    @field:NotBlank(message = "Datacenter name is required")
    val datacenterName: String,

    @field:NotBlank(message = "Cluster name is required")
    val clusterName: String,

    @field:NotBlank(message = "Datastore name is required")
    val datastoreName: String,

    @field:NotBlank(message = "Network name is required")
    val networkName: String,

    val templateName: String? = null,

    val updateVerifiedAt: Boolean = false
)

/**
 * Response for VMware configuration GET request.
 *
 * Password is intentionally excluded from the response for security.
 * A placeholder (masked) indication is provided instead.
 *
 * @property id Unique identifier of the configuration
 * @property vcenterUrl vCenter SDK URL
 * @property username Service account username
 * @property hasPassword Indicates if a password is currently set (true) or missing (false)
 * @property datacenterName Default datacenter name
 * @property clusterName Default cluster name
 * @property datastoreName Default datastore name
 * @property networkName Default network name
 * @property templateName Default VM template name
 * @property folderPath VM folder path (optional)
 * @property verifiedAt Timestamp of last successful connection test
 * @property createdAt Timestamp when configuration was created
 * @property updatedAt Timestamp when configuration was last updated
 * @property createdBy User who created the configuration
 * @property updatedBy User who last updated the configuration
 * @property version Optimistic locking version
 */
public data class VmwareConfigApiResponse(
    val id: String,
    val vcenterUrl: String,
    val username: String,
    val hasPassword: Boolean, // Indicates password is set, but value not exposed
    val datacenterName: String,
    val clusterName: String,
    val datastoreName: String,
    val networkName: String,
    val templateName: String,
    val folderPath: String?,
    val verifiedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: String,
    val updatedBy: String,
    val version: Long
) {
    public companion object {
        public fun fromDomain(response: VmwareConfigResponse): VmwareConfigApiResponse =
            VmwareConfigApiResponse(
                id = response.id.value.toString(),
                vcenterUrl = response.vcenterUrl,
                username = response.username,
                hasPassword = true, // Password is always set for valid config
                datacenterName = response.datacenterName,
                clusterName = response.clusterName,
                datastoreName = response.datastoreName,
                networkName = response.networkName,
                templateName = response.templateName,
                folderPath = response.folderPath,
                verifiedAt = response.verifiedAt,
                createdAt = response.createdAt,
                updatedAt = response.updatedAt,
                createdBy = response.createdBy.value.toString(),
                updatedBy = response.updatedBy.value.toString(),
                version = response.version
            )
    }
}

/**
 * Response for successful connection test.
 *
 * @property verifiedAtUpdated True if verifiedAt timestamp was updated (only when requested),
 *                             null if update was not requested, false if update failed
 */
public data class TestVmwareConnectionApiResponse(
    val success: Boolean,
    val vcenterVersion: String,
    val clusterName: String,
    val clusterHosts: Int,
    val datastoreFreeGb: Long,
    val message: String,
    val verifiedAtUpdated: Boolean? = null
)

/**
 * Response for configuration existence check.
 *
 * Used by frontend to determine whether to show "VMware not configured" warning.
 */
public data class VmwareConfigExistsResponse(
    val exists: Boolean,
    val verifiedAt: Instant? = null
)

/**
 * Response for successful save operation.
 */
public data class SaveVmwareConfigApiResponse(
    val id: String,
    val version: Long,
    val message: String
)
