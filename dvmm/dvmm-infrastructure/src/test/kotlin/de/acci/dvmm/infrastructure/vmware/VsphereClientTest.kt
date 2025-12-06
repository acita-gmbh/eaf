package de.acci.dvmm.infrastructure.vmware

import de.acci.dvmm.application.vmware.CredentialEncryptor
import de.acci.dvmm.application.vmware.VmwareConfigurationPort
import de.acci.dvmm.application.vmware.VsphereError
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.tenant.TenantContextElement
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VsphereClientTest {

    private val sessionManager = mockk<VsphereSessionManager>()
    private val configPort = mockk<VmwareConfigurationPort>()
    private val credentialEncryptor = mockk<CredentialEncryptor>()
    private val client = VsphereClient(sessionManager, configPort, credentialEncryptor)
    private val tenantId = TenantId.generate()

    @Test
    fun `listDatacenters returns failure when no config exists`() = runTest(TenantContextElement(tenantId)) {
        every { sessionManager.getSession(tenantId) } returns null
        coEvery { configPort.findByTenantId(tenantId) } returns null
        
        val result = client.listDatacenters()
        
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is VsphereError.ConnectionError)
        // Message checking
        val error = (result as Result.Failure).error as VsphereError.ConnectionError
        assertTrue(error.message?.contains("No VMware configuration") == true)
    }
}