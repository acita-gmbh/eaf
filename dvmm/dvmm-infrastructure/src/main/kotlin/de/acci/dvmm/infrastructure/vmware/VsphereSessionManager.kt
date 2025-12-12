package de.acci.dvmm.infrastructure.vmware

import de.acci.eaf.core.types.TenantId
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

/**
 * Manages active vSphere sessions for tenants (thread-safe).
 *
 * Keeps track of authenticated sessions to avoid re-login on every request.
 * Handles keep-alive and cleanup of sessions.
 */
@Component
public class VsphereSessionManager {
    private val sessions: ConcurrentHashMap<TenantId, VsphereSession> = ConcurrentHashMap()

    /** Gets the active session for a tenant if it exists. */
    public fun getSession(tenantId: TenantId): VsphereSession? {
        return sessions[tenantId]
    }

    /**
     * Registers a new session for a tenant.
     * Cancels any previous session's keepalive job to prevent resource leaks.
     */
    public fun registerSession(tenantId: TenantId, session: VsphereSession) {
        val existingSession = sessions.put(tenantId, session)
        // Always cancel the previous session's keepAliveJob to prevent resource leaks
        if (existingSession != null) {
            existingSession.keepAliveJob?.cancel()
        }
    }

    /** Removes a session and cancels its keepalive job. */
    public fun removeSession(tenantId: TenantId) {
        val session = sessions.remove(tenantId)
        session?.keepAliveJob?.cancel()
    }

    /** Updates the last activity timestamp for a session. */
    public fun touchSession(tenantId: TenantId) {
        sessions.computeIfPresent(tenantId) { _, session ->
            session.copy(lastActivity = Instant.now())
        }
    }
}
