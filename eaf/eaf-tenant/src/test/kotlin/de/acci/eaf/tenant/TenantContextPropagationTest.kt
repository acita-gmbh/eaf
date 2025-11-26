package de.acci.eaf.tenant

import de.acci.eaf.core.types.TenantId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TenantContextPropagationTest {

    @Test
    fun `tenant survives dispatcher switch`() = runTest {
        val tenant = TenantId.generate()

        withContext(TenantContextElement(tenant)) {
            withContext(Dispatchers.IO) {
                assertEquals(tenant, TenantContext.current())
            }
        }
    }

    @Test
    fun `tenant survives async child`() = runTest {
        val tenant = TenantId.generate()

        val result = withContext(TenantContextElement(tenant)) {
            async { TenantContext.current() }.await()
        }

        assertEquals(tenant, result)
    }

    @Test
    fun `tenant isolated across 100 concurrent coroutines`() = runTest {
        val tenantA = TenantId.generate()
        val tenantB = TenantId.generate()

        val expected = (1..100).map { if (it % 2 == 0) tenantA else tenantB }

        val results = (1..100).map { index ->
            async {
                val tenant = if (index % 2 == 0) tenantA else tenantB
                withContext(TenantContextElement(tenant)) {
                    withContext(Dispatchers.IO) {
                        delay(Random.nextLong(from = 1, until = 10))
                        TenantContext.current()
                    }
                }
            }
        }.awaitAll()

        results.zip(expected).forEach { (actual, exp) ->
            assertEquals(exp, actual)
        }
    }

    @Test
    fun `missing tenant throws`() = runTest {
        assertFailsWith<TenantContextMissingException> {
            TenantContext.current()
        }
    }
}
