package de.acci.dvmm.infrastructure.vmware

import com.vmware.vim25.ServiceContent
import com.vmware.vim25.VimPortType
import de.acci.eaf.core.types.TenantId
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VsphereSessionManagerTest {

    private val manager = VsphereSessionManager()
    private val vimPort = mockk<VimPortType>()
    private val serviceContent = mockk<ServiceContent>()
    private val tenantId = TenantId.generate()

    @Test
    fun `getSession returns null when no session exists`() {
        assertNull(manager.getSession(tenantId))
    }

    @Test
    fun `registerSession stores session and getSession retrieves it`() {
        val session = VsphereSession(vimPort = vimPort, serviceContent = serviceContent)
        manager.registerSession(tenantId, session)

        val retrieved = manager.getSession(tenantId)
        assertSame(session, retrieved)
    }

    @Test
    fun `removeSession removes the session`() {
        val session = VsphereSession(vimPort = vimPort, serviceContent = serviceContent)
        manager.registerSession(tenantId, session)

        manager.removeSession(tenantId)

        assertNull(manager.getSession(tenantId))
    }

    @Test
    fun `touchSession updates lastActivity`() {
        val session = VsphereSession(vimPort = vimPort, serviceContent = serviceContent)
        val initialTime = session.lastActivity
        manager.registerSession(tenantId, session)

        // Wait a bit to ensure time difference
        Thread.sleep(10)
        manager.touchSession(tenantId)

        val updatedSession = manager.getSession(tenantId)
        assertNotNull(updatedSession)
        assertTrue(updatedSession!!.lastActivity.isAfter(initialTime))
    }
}