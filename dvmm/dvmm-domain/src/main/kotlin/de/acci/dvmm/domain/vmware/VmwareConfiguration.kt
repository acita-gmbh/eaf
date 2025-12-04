package de.acci.dvmm.domain.vmware

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import java.time.Instant

/**
 * Domain model representing VMware vCenter connection configuration.
 *
 * Each tenant has exactly one VMware configuration that defines how DVMM
 * connects to their vCenter instance for VM provisioning. The password is
 * stored encrypted (AES-256) and only decrypted when needed for vSphere API calls.
 *
 * This is a configuration entity (not an event-sourced aggregate) because:
 * - It's CRUD configuration data, not a complex domain with state transitions
 * - Audit requirements are met via created/updated timestamps and user tracking
 * - The configuration is tenant-isolated via RLS
 *
 * ## Security Considerations
 *
 * - Passwords MUST be encrypted at rest using AES-256 (Spring Security Crypto)
 * - Passwords MUST NOT appear in logs, error messages, or API responses
 * - Only admins within the same tenant can view/modify configuration
 *
 * @property id Unique identifier for the configuration
 * @property tenantId Tenant this configuration belongs to (one config per tenant)
 * @property vcenterUrl vCenter SDK URL (e.g., https://vcenter.example.com/sdk)
 * @property username Service account username for vCenter authentication
 * @property passwordEncrypted AES-256 encrypted password (never store plaintext)
 * @property datacenterName vSphere datacenter name
 * @property clusterName vSphere cluster name within the datacenter
 * @property datastoreName Default datastore for VM storage
 * @property networkName Default network for VM connectivity
 * @property templateName VM template name for cloning (default: ubuntu-22.04-template)
 * @property folderPath Optional VM folder path for organization
 * @property verifiedAt Timestamp of last successful connection test (null if never verified)
 * @property createdAt When the configuration was created
 * @property updatedAt When the configuration was last modified
 * @property createdBy User who created the configuration
 * @property updatedBy User who last modified the configuration
 * @property version Optimistic locking version for concurrent update detection
 */
public data class VmwareConfiguration(
    val id: VmwareConfigurationId,
    val tenantId: TenantId,
    val vcenterUrl: String,
    val username: String,
    val passwordEncrypted: ByteArray,
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
) {
    public companion object {
        /**
         * Default template name for new configurations.
         */
        public const val DEFAULT_TEMPLATE_NAME: String = "ubuntu-22.04-template"

        /**
         * Create a new VMware configuration.
         *
         * @param tenantId Tenant this configuration belongs to
         * @param vcenterUrl vCenter SDK URL
         * @param username Service account username
         * @param passwordEncrypted AES-256 encrypted password
         * @param datacenterName vSphere datacenter name
         * @param clusterName vSphere cluster name
         * @param datastoreName Default datastore name
         * @param networkName Default network name
         * @param templateName VM template name (defaults to ubuntu-22.04-template)
         * @param folderPath Optional VM folder path
         * @param userId User creating the configuration
         * @param timestamp Current timestamp (typically Instant.now())
         */
        public fun create(
            tenantId: TenantId,
            vcenterUrl: String,
            username: String,
            passwordEncrypted: ByteArray,
            datacenterName: String,
            clusterName: String,
            datastoreName: String,
            networkName: String,
            templateName: String = DEFAULT_TEMPLATE_NAME,
            folderPath: String? = null,
            userId: UserId,
            timestamp: Instant
        ): VmwareConfiguration = VmwareConfiguration(
            id = VmwareConfigurationId.generate(),
            tenantId = tenantId,
            vcenterUrl = vcenterUrl,
            username = username,
            passwordEncrypted = passwordEncrypted,
            datacenterName = datacenterName,
            clusterName = clusterName,
            datastoreName = datastoreName,
            networkName = networkName,
            templateName = templateName,
            folderPath = folderPath,
            verifiedAt = null,
            createdAt = timestamp,
            updatedAt = timestamp,
            createdBy = userId,
            updatedBy = userId,
            version = 0
        )
    }

    /**
     * Create an updated configuration with new values.
     *
     * @param vcenterUrl New vCenter URL (or existing if null)
     * @param username New username (or existing if null)
     * @param passwordEncrypted New encrypted password (or existing if null)
     * @param datacenterName New datacenter name (or existing if null)
     * @param clusterName New cluster name (or existing if null)
     * @param datastoreName New datastore name (or existing if null)
     * @param networkName New network name (or existing if null)
     * @param templateName New template name (or existing if null)
     * @param folderPath New folder path (or existing if null)
     * @param userId User performing the update
     * @param timestamp Current timestamp
     */
    public fun update(
        vcenterUrl: String? = null,
        username: String? = null,
        passwordEncrypted: ByteArray? = null,
        datacenterName: String? = null,
        clusterName: String? = null,
        datastoreName: String? = null,
        networkName: String? = null,
        templateName: String? = null,
        folderPath: String? = null,
        userId: UserId,
        timestamp: Instant
    ): VmwareConfiguration = copy(
        vcenterUrl = vcenterUrl ?: this.vcenterUrl,
        username = username ?: this.username,
        passwordEncrypted = passwordEncrypted ?: this.passwordEncrypted,
        datacenterName = datacenterName ?: this.datacenterName,
        clusterName = clusterName ?: this.clusterName,
        datastoreName = datastoreName ?: this.datastoreName,
        networkName = networkName ?: this.networkName,
        templateName = templateName ?: this.templateName,
        folderPath = folderPath ?: this.folderPath,
        updatedAt = timestamp,
        updatedBy = userId,
        verifiedAt = null // Reset verification when config changes
    )

    /**
     * Mark the configuration as verified after a successful connection test.
     *
     * Increments the version and updates timestamp for proper optimistic locking.
     *
     * @param timestamp When the verification occurred
     * @return Updated configuration with incremented version
     */
    public fun markVerified(timestamp: Instant): VmwareConfiguration = copy(
        verifiedAt = timestamp,
        updatedAt = timestamp,
        version = version + 1
    )

    // Override equals/hashCode to handle ByteArray properly
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as VmwareConfiguration

        if (id != other.id) return false
        if (tenantId != other.tenantId) return false
        if (vcenterUrl != other.vcenterUrl) return false
        if (username != other.username) return false
        if (!passwordEncrypted.contentEquals(other.passwordEncrypted)) return false
        if (datacenterName != other.datacenterName) return false
        if (clusterName != other.clusterName) return false
        if (datastoreName != other.datastoreName) return false
        if (networkName != other.networkName) return false
        if (templateName != other.templateName) return false
        if (folderPath != other.folderPath) return false
        if (verifiedAt != other.verifiedAt) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false
        if (createdBy != other.createdBy) return false
        if (updatedBy != other.updatedBy) return false
        if (version != other.version) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + tenantId.hashCode()
        result = 31 * result + vcenterUrl.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + passwordEncrypted.contentHashCode()
        result = 31 * result + datacenterName.hashCode()
        result = 31 * result + clusterName.hashCode()
        result = 31 * result + datastoreName.hashCode()
        result = 31 * result + networkName.hashCode()
        result = 31 * result + templateName.hashCode()
        result = 31 * result + (folderPath?.hashCode() ?: 0)
        result = 31 * result + (verifiedAt?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + createdBy.hashCode()
        result = 31 * result + updatedBy.hashCode()
        result = 31 * result + version.hashCode()
        return result
    }

    // Prevent password from appearing in toString
    override fun toString(): String {
        return "VmwareConfiguration(" +
            "id=$id, " +
            "tenantId=$tenantId, " +
            "vcenterUrl=$vcenterUrl, " +
            "username=$username, " +
            "passwordEncrypted=[REDACTED], " +
            "datacenterName=$datacenterName, " +
            "clusterName=$clusterName, " +
            "datastoreName=$datastoreName, " +
            "networkName=$networkName, " +
            "templateName=$templateName, " +
            "folderPath=$folderPath, " +
            "verifiedAt=$verifiedAt, " +
            "createdAt=$createdAt, " +
            "updatedAt=$updatedAt, " +
            "version=$version" +
            ")"
    }
}
