package de.acci.eaf.testing

import de.acci.eaf.core.types.TenantId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TenantTestContextTest {

    @Test
    fun `set and current returns the same tenant`() {
        // Given
        val tenantId = TenantId.generate()

        try {
            // When
            TenantTestContext.set(tenantId)

            // Then
            assertEquals(tenantId, TenantTestContext.current())
        } finally {
            TenantTestContext.clear()
        }
    }

    @Test
    fun `current returns null when no tenant is set`() {
        // Given
        TenantTestContext.clear()

        // When / Then
        assertNull(TenantTestContext.current())
    }

    @Test
    fun `clear removes the tenant context`() {
        // Given
        val tenantId = TenantId.generate()
        TenantTestContext.set(tenantId)

        // When
        TenantTestContext.clear()

        // Then
        assertNull(TenantTestContext.current())
    }

    @Test
    fun `set overwrites previous tenant`() {
        // Given
        val firstTenant = TenantId.generate()
        val secondTenant = TenantId.generate()

        try {
            TenantTestContext.set(firstTenant)

            // When
            TenantTestContext.set(secondTenant)

            // Then
            assertEquals(secondTenant, TenantTestContext.current())
        } finally {
            TenantTestContext.clear()
        }
    }
}
