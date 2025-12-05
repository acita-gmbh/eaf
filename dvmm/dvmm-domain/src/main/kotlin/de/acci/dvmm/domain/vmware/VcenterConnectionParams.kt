package de.acci.dvmm.domain.vmware

/**
 * Value object containing parameters needed to establish a vCenter connection.
 *
 * This is a pure value object used for testing connections. Unlike [VmwareConfiguration],
 * it doesn't include entity concerns (ID, version, timestamps) or encrypted credentials.
 * The password is provided separately to the connection test method.
 *
 * ## Usage
 *
 * Used by [VspherePort.testConnection] to validate vCenter connectivity without
 * requiring a full [VmwareConfiguration] entity:
 *
 * ```kotlin
 * val params = VcenterConnectionParams(
 *     vcenterUrl = "https://vcenter.example.com/sdk",
 *     username = "svc-dvmm@vsphere.local",
 *     datacenterName = "DC1",
 *     clusterName = "Cluster1",
 *     datastoreName = "SSD-Tier1",
 *     networkName = "VM-Network",
 *     templateName = "ubuntu-22.04-template"
 * )
 * val result = vspherePort.testConnection(params, password)
 * ```
 *
 * ## Design Rationale
 *
 * Separating connection parameters from the entity model ensures:
 * 1. **Domain integrity**: No need to create entities with invalid state (empty passwords)
 * 2. **Single responsibility**: This object is purely for connection testing
 * 3. **Type safety**: API consumers get exactly what they need, nothing more
 *
 * @property vcenterUrl vCenter SDK URL (must start with https://)
 * @property username Service account username for vCenter authentication
 * @property datacenterName vSphere datacenter name to validate
 * @property clusterName vSphere cluster name to validate
 * @property datastoreName Datastore name to validate
 * @property networkName Network name to validate
 * @property templateName VM template name to validate
 * @property folderPath Optional VM folder path
 */
public data class VcenterConnectionParams(
    val vcenterUrl: String,
    val username: String,
    val datacenterName: String,
    val clusterName: String,
    val datastoreName: String,
    val networkName: String,
    val templateName: String,
    val folderPath: String? = null
) {
    init {
        require(vcenterUrl.isNotBlank()) { "vCenter URL cannot be blank" }
        require(vcenterUrl.startsWith("https://")) { "vCenter URL must start with https://" }
        require(username.isNotBlank()) { "Username cannot be blank" }
        require(datacenterName.isNotBlank()) { "Datacenter name cannot be blank" }
        require(clusterName.isNotBlank()) { "Cluster name cannot be blank" }
        require(datastoreName.isNotBlank()) { "Datastore name cannot be blank" }
        require(networkName.isNotBlank()) { "Network name cannot be blank" }
        require(templateName.isNotBlank()) { "Template name cannot be blank" }
    }

    public companion object {
        /**
         * Create connection params from an existing [VmwareConfiguration].
         *
         * Useful when you have a saved configuration and want to test it.
         */
        public fun fromConfiguration(config: VmwareConfiguration): VcenterConnectionParams =
            VcenterConnectionParams(
                vcenterUrl = config.vcenterUrl,
                username = config.username,
                datacenterName = config.datacenterName,
                clusterName = config.clusterName,
                datastoreName = config.datastoreName,
                networkName = config.networkName,
                templateName = config.templateName,
                folderPath = config.folderPath
            )
    }
}
