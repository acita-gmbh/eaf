package de.acci.dcm.application.vmware

/**
 * Information returned from a successful vCenter connection test.
 *
 * @property vcenterVersion vCenter Server version (e.g., "8.0.2")
 * @property clusterName Name of the validated cluster
 * @property clusterHosts Number of ESXi hosts in the cluster
 * @property datastoreFreeGb Free space in the datastore in GB
 */
public data class ConnectionInfo(
    val vcenterVersion: String,
    val clusterName: String,
    val clusterHosts: Int,
    val datastoreFreeGb: Long
)

/**
 * Errors that can occur during vCenter connection testing.
 *
 * Error messages are designed to help admins troubleshoot (AC-3.1.3):
 * - "Connection refused" → Network/firewall issue
 * - "Authentication failed" → Invalid credentials
 * - "Datacenter not found" → Wrong datacenter name
 * - "Cluster not found" → Wrong cluster name
 */
public sealed class ConnectionError {

    /**
     * Network-level connection failure.
     * Causes: firewall blocking, wrong URL, vCenter down, DNS failure.
     */
    public data class NetworkError(
        val message: String,
        val cause: Throwable? = null
    ) : ConnectionError()

    /**
     * SSL/TLS certificate validation failure.
     * Causes: self-signed cert, expired cert, hostname mismatch.
     */
    public data class SslError(
        val message: String,
        val cause: Throwable? = null
    ) : ConnectionError()

    /**
     * Authentication failure.
     * Causes: wrong username, wrong password, locked account, insufficient permissions.
     */
    public data class AuthenticationFailed(
        val message: String = "Authentication failed - check username and password"
    ) : ConnectionError()

    /**
     * Specified datacenter not found in vCenter.
     */
    public data class DatacenterNotFound(
        val datacenterName: String,
        val message: String = "Datacenter not found: $datacenterName"
    ) : ConnectionError()

    /**
     * Specified cluster not found in the datacenter.
     */
    public data class ClusterNotFound(
        val clusterName: String,
        val message: String = "Cluster not found: $clusterName"
    ) : ConnectionError()

    /**
     * Specified datastore not found or inaccessible.
     */
    public data class DatastoreNotFound(
        val datastoreName: String,
        val message: String = "Datastore not found: $datastoreName"
    ) : ConnectionError()

    /**
     * Specified network not found.
     */
    public data class NetworkNotFound(
        val networkName: String,
        val message: String = "Network not found: $networkName"
    ) : ConnectionError()

    /**
     * Specified template not found.
     */
    public data class TemplateNotFound(
        val templateName: String,
        val message: String = "VM template not found: $templateName"
    ) : ConnectionError()

    /**
     * Unexpected vSphere API error.
     */
    public data class ApiError(
        val message: String,
        val cause: Throwable? = null
    ) : ConnectionError()
}
