package de.acci.dvmm.application.vmware

public sealed class VsphereError(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    public class ConnectionError(message: String, cause: Throwable? = null) : VsphereError(message, cause)
    public class AuthenticationError(message: String, cause: Throwable? = null) : VsphereError(message, cause)
    public class ApiError(message: String, cause: Throwable? = null) : VsphereError(message, cause)
    public class NotFound(message: String) : VsphereError(message)
    public class Timeout(message: String) : VsphereError(message)
    public class ProvisioningError(message: String, cause: Throwable? = null) : VsphereError(message, cause)
    public class DeletionError(message: String, cause: Throwable? = null) : VsphereError(message, cause)
}

public data class Datacenter(public val id: String, public val name: String)
public data class Cluster(public val id: String, public val name: String)
public data class Datastore(public val id: String, public val name: String)
public data class Network(public val id: String, public val name: String)
public data class ResourcePool(public val id: String, public val name: String)
public data class VmInfo(public val id: String, public val name: String)
public data class VmId(public val value: String)
public data class VmSpec(
    public val name: String,
    public val template: String,
    public val cpu: Int,
    public val memoryGb: Int
)
