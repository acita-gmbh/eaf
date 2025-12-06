package de.acci.dvmm.infrastructure.vmware

import de.acci.dvmm.application.vmware.VspherePort
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VcenterAdapterTest {

    private val vsphereClient = mockk<VsphereClient>()
    private val adapter: VspherePort = VcenterAdapter(vsphereClient)

    @Test
    fun `adapter implements VspherePort`() {
        assertTrue(adapter is VspherePort)
    }
}
