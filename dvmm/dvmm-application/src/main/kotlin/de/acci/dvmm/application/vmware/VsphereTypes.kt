package de.acci.dvmm.application.vmware

/**
 * Provisioning error codes for user-friendly error reporting.
 *
 * Maps to VMware fault types and provides human-readable messages.
 * See AC-3.6.3 for the error mapping requirements.
 */
public enum class ProvisioningErrorCode(
    public val userMessage: String
) {
    /** InsufficientResourcesFault - cluster capacity reached */
    INSUFFICIENT_RESOURCES("Cluster capacity reached. Please try a smaller size or contact support."),

    /** InvalidDatastorePath - storage not available */
    DATASTORE_NOT_AVAILABLE("Storage unavailable. Please contact support."),

    /** VmConfigFault - invalid VM configuration */
    VM_CONFIG_INVALID("Invalid configuration. Please check your request parameters."),

    /** NotAuthenticated - authentication failure */
    CONNECTION_FAILED("System authentication failed. IT has been notified."),

    /** TemplateNotFound - VM template missing */
    TEMPLATE_NOT_FOUND("VM template missing. IT has been notified."),

    /** NetworkConfigFailed - network setup failure */
    NETWORK_CONFIG_FAILED("Network setup failed. IT has been notified."),

    /** NetworkNotFound - network not available in vCenter */
    NETWORK_NOT_FOUND("Network not available. IT has been notified."),

    /** ResourcePoolNotFound - resource pool missing */
    RESOURCE_POOL_NOT_FOUND("Resource pool not available. IT has been notified."),

    /** ClusterNotFound - compute cluster missing */
    CLUSTER_NOT_FOUND("Cluster not available. IT has been notified."),

    /** FolderNotFound - VM folder missing */
    FOLDER_NOT_FOUND("VM folder not available. IT has been notified."),

    /** GuestToolsTimeout - VMware Tools didn't respond */
    VMWARE_TOOLS_TIMEOUT("VM started but tools didn't respond. Please restart the VM."),

    /** SocketTimeoutException - temporary connection issue */
    CONNECTION_TIMEOUT("Temporary connection issue. We will retry automatically."),

    /** Unknown/unclassified error */
    UNKNOWN("Unexpected error. IT has been notified.")
}

/**
 * Sealed class hierarchy for vSphere API errors.
 *
 * Aligned with future HypervisorError abstraction (ADR-004a) to minimize
 * refactoring when multi-hypervisor support is added in Epic 6.
 *
 * ## Error Classification
 *
 * Errors are classified as **retriable** or **permanent**:
 * - **Retriable:** Transient failures that may succeed on retry (connection issues,
 *   temporary unavailability, resource exhaustion that may clear)
 * - **Permanent:** Configuration errors or missing resources that require human intervention
 *
 * ## Usage with Resilience4j
 *
 * The [retriable] property is used by Resilience4j retry configuration to determine
 * whether to retry an operation:
 *
 * ```kotlin
 * val retryConfig = RetryConfig.custom<Result<*, VsphereError>>()
 *     .retryOnResult { result ->
 *         result is Result.Failure && result.error.retriable
 *     }
 *     .build()
 * ```
 *
 * @see ProvisioningErrorCode for user-friendly error messages
 */
public sealed class VsphereError(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    /** Whether this error is transient and should be retried */
    public abstract val retriable: Boolean

    /** User-friendly error message suitable for display */
    public abstract val userMessage: String

    /** Error code for structured error reporting */
    public abstract val errorCode: ProvisioningErrorCode

    /**
     * Connection error - typically transient network issues.
     * Retriable because connection may be restored.
     */
    public class ConnectionError(
        message: String,
        cause: Throwable? = null
    ) : VsphereError(message, cause) {
        override val retriable: Boolean = true
        override val userMessage: String = ProvisioningErrorCode.CONNECTION_TIMEOUT.userMessage
        override val errorCode: ProvisioningErrorCode = ProvisioningErrorCode.CONNECTION_TIMEOUT
    }

    /**
     * Authentication error - credentials or session invalid.
     * Not retriable - requires configuration change.
     */
    public class AuthenticationError(
        message: String,
        cause: Throwable? = null
    ) : VsphereError(message, cause) {
        override val retriable: Boolean = false
        override val userMessage: String = ProvisioningErrorCode.CONNECTION_FAILED.userMessage
        override val errorCode: ProvisioningErrorCode = ProvisioningErrorCode.CONNECTION_FAILED
    }

    /**
     * Generic API error - typically transient vCenter issues.
     * Retriable by default.
     */
    public class ApiError(
        message: String,
        cause: Throwable? = null
    ) : VsphereError(message, cause) {
        override val retriable: Boolean = true
        override val userMessage: String = ProvisioningErrorCode.UNKNOWN.userMessage
        override val errorCode: ProvisioningErrorCode = ProvisioningErrorCode.UNKNOWN
    }

    /**
     * Resource not found - template, datastore, network, or other resource missing.
     * Not retriable - requires configuration change or resource creation.
     */
    public class ResourceNotFound(
        public val resourceType: String,
        public val resourceId: String,
        cause: Throwable? = null
    ) : VsphereError("$resourceType not found: $resourceId", cause) {
        override val retriable: Boolean = false
        override val userMessage: String
            get() = when (resourceType.lowercase()) {
                "template" -> ProvisioningErrorCode.TEMPLATE_NOT_FOUND.userMessage
                "datastore" -> ProvisioningErrorCode.DATASTORE_NOT_AVAILABLE.userMessage
                "network" -> ProvisioningErrorCode.NETWORK_NOT_FOUND.userMessage
                "resourcepool", "resource pool" -> ProvisioningErrorCode.RESOURCE_POOL_NOT_FOUND.userMessage
                "cluster" -> ProvisioningErrorCode.CLUSTER_NOT_FOUND.userMessage
                "folder" -> ProvisioningErrorCode.FOLDER_NOT_FOUND.userMessage
                // Fallback uses generic message to avoid exposing internal resource type names
                else -> "Resource not available. IT has been notified."
            }
        override val errorCode: ProvisioningErrorCode
            get() = when (resourceType.lowercase()) {
                "template" -> ProvisioningErrorCode.TEMPLATE_NOT_FOUND
                "datastore" -> ProvisioningErrorCode.DATASTORE_NOT_AVAILABLE
                "network" -> ProvisioningErrorCode.NETWORK_NOT_FOUND
                "resourcepool", "resource pool" -> ProvisioningErrorCode.RESOURCE_POOL_NOT_FOUND
                "cluster" -> ProvisioningErrorCode.CLUSTER_NOT_FOUND
                "folder" -> ProvisioningErrorCode.FOLDER_NOT_FOUND
                // UNKNOWN is still used for unrecognized resource types
                else -> ProvisioningErrorCode.UNKNOWN
            }
    }

    /**
     * Kept for backward compatibility - use ResourceNotFound instead.
     * @see ResourceNotFound
     */
    @Deprecated(
        message = "Use ResourceNotFound instead",
        replaceWith = ReplaceWith("ResourceNotFound(resourceType = \"unknown\", resourceId = message)")
    )
    public class NotFound(message: String) : VsphereError(message) {
        override val retriable: Boolean = false
        override val userMessage: String = ProvisioningErrorCode.UNKNOWN.userMessage
        override val errorCode: ProvisioningErrorCode = ProvisioningErrorCode.UNKNOWN
    }

    /**
     * Operation timeout - VMware Tools or task timeout.
     * Retriable because operation may succeed on retry.
     */
    public class Timeout(message: String) : VsphereError(message) {
        override val retriable: Boolean = true
        override val userMessage: String = ProvisioningErrorCode.VMWARE_TOOLS_TIMEOUT.userMessage
        override val errorCode: ProvisioningErrorCode = ProvisioningErrorCode.VMWARE_TOOLS_TIMEOUT
    }

    /**
     * Provisioning error - clone or VM creation failure.
     * @deprecated Use [OperationFailed] for specific operation failures or other typed error classes.
     */
    @Deprecated(
        message = "Use OperationFailed for specific operation failures or other typed error classes",
        replaceWith = ReplaceWith("OperationFailed(operation = \"clone\", details = message)")
    )
    public class ProvisioningError(
        message: String,
        cause: Throwable? = null
    ) : VsphereError(message, cause) {
        override val retriable: Boolean = true
        override val userMessage: String = ProvisioningErrorCode.UNKNOWN.userMessage
        override val errorCode: ProvisioningErrorCode = ProvisioningErrorCode.UNKNOWN
    }

    /**
     * VM deletion error.
     * Retriable because deletion may succeed on retry.
     */
    public class DeletionError(
        message: String,
        cause: Throwable? = null
    ) : VsphereError(message, cause) {
        override val retriable: Boolean = true
        override val userMessage: String = ProvisioningErrorCode.UNKNOWN.userMessage
        override val errorCode: ProvisioningErrorCode = ProvisioningErrorCode.UNKNOWN
    }

    // ===== ADR-004a Aligned Error Types =====
    // These error types mirror the future HypervisorError hierarchy

    /**
     * Resource exhausted - cluster capacity reached.
     * Retriable because resources may free up.
     *
     * Corresponds to HypervisorError.ResourceExhausted in ADR-004a.
     */
    public class ResourceExhausted(
        override val message: String,
        public val resourceType: String,
        public val requested: Int,
        public val available: Int,
        cause: Throwable? = null
    ) : VsphereError(message, cause) {
        override val retriable: Boolean = true
        override val userMessage: String = ProvisioningErrorCode.INSUFFICIENT_RESOURCES.userMessage
        override val errorCode: ProvisioningErrorCode = ProvisioningErrorCode.INSUFFICIENT_RESOURCES
    }

    /**
     * Invalid configuration - VM config doesn't meet requirements.
     * Not retriable - requires request modification.
     *
     * Corresponds to HypervisorError.InvalidConfiguration in ADR-004a.
     */
    public class InvalidConfiguration(
        override val message: String,
        public val field: String,
        cause: Throwable? = null
    ) : VsphereError(message, cause) {
        override val retriable: Boolean = false
        override val userMessage: String = ProvisioningErrorCode.VM_CONFIG_INVALID.userMessage
        override val errorCode: ProvisioningErrorCode = ProvisioningErrorCode.VM_CONFIG_INVALID
    }

    /**
     * Operation failed - generic operation failure (typically retriable).
     * Retriable for transient failures like network config issues.
     *
     * Corresponds to HypervisorError.OperationFailed in ADR-004a.
     *
     * @param operation The operation that failed (e.g., "network config", "disk resize")
     * @param details Error details describing what went wrong
     * @param cause Optional underlying exception
     */
    public class OperationFailed(
        public val operation: String,
        public val details: String,
        cause: Throwable? = null
    ) : VsphereError("$operation failed: $details", cause) {
        override val retriable: Boolean = true
        override val userMessage: String
            get() = when {
                operation.contains("network", ignoreCase = true) ->
                    ProvisioningErrorCode.NETWORK_CONFIG_FAILED.userMessage
                else -> ProvisioningErrorCode.UNKNOWN.userMessage
            }
        override val errorCode: ProvisioningErrorCode
            get() = when {
                operation.contains("network", ignoreCase = true) ->
                    ProvisioningErrorCode.NETWORK_CONFIG_FAILED
                else -> ProvisioningErrorCode.UNKNOWN
            }
    }
}

public data class Datacenter(public val id: String, public val name: String)
public data class Cluster(public val id: String, public val name: String)
public data class Datastore(public val id: String, public val name: String)
public data class Network(public val id: String, public val name: String)
public data class ResourcePool(public val id: String, public val name: String)
public data class VmInfo(public val id: String, public val name: String)
public data class VmId(public val value: String)

/**
 * Specification for creating a new VM.
 *
 * @property name VM name (will be used as hostname)
 * @property template Template name to clone from
 * @property cpu Number of CPU cores
 * @property memoryGb Memory in GB
 * @property diskGb Disk size in GB. Use 0 (default) to keep the template's disk size.
 *                  If greater than 0 and larger than the template's disk, the disk will be extended.
 *                  If a value smaller than the template's disk size is provided, the template's
 *                  disk size will be used instead (disks cannot shrink).
 */
public data class VmSpec(
    public val name: String,
    public val template: String,
    public val cpu: Int,
    public val memoryGb: Int,
    public val diskGb: Int = 0
)
