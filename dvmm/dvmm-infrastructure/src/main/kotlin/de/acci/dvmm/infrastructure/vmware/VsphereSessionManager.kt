package de.acci.dvmm.infrastructure.vmware

import de.acci.eaf.core.types.TenantId
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

@Component
public class VsphereSessionManager {
    private val sessions: ConcurrentHashMap<TenantId, VsphereSession> = ConcurrentHashMap()

    public fun getSession(tenantId: TenantId): VsphereSession? {
        return sessions[tenantId]
    }

    public fun registerSession(tenantId: TenantId, session: VsphereSession) {
        sessions[tenantId] = session
    }

    public fun removeSession(tenantId: TenantId) {
        val session = sessions.remove(tenantId)
        // Cancel keepalive job to prevent resource leak
        session?.keepAliveJob?.cancel()
    }

    public fun touchSession(tenantId: TenantId) {
        sessions.computeIfPresent(tenantId) { _, session ->
            session.copy(lastActivity = java.time.Instant.now())
        }
    }
}
