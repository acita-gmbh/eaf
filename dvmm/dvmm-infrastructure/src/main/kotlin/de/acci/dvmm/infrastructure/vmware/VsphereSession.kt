package de.acci.dvmm.infrastructure.vmware

import com.vmware.vim25.ServiceContent
import com.vmware.vim25.VimPortType
import kotlinx.coroutines.Job
import java.time.Instant

/**
 * Represents an active vSphere session for a tenant.
 *
 * Sessions are managed by [VsphereSessionManager] and kept alive via a background
 * coroutine that periodically calls the vSphere API to prevent session timeout.
 *
 * @property vimPort The JAX-WS port for vSphere SOAP API calls
 * @property serviceContent The vSphere ServiceContent with references to managers
 * @property lastActivity Timestamp of the last API activity (used for session health monitoring)
 * @property keepAliveJob Background coroutine that maintains session via periodic API calls
 */
public data class VsphereSession(
    public val vimPort: VimPortType,
    public val serviceContent: ServiceContent,
    public val lastActivity: Instant = Instant.now(),
    public val keepAliveJob: Job? = null
)
