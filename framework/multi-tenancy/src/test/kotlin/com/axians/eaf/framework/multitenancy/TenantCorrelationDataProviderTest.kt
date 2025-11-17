package com.axians.eaf.framework.multitenancy

import com.axians.eaf.framework.multitenancy.config.TenantEventProcessingConfiguration
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import org.axonframework.eventhandling.GenericEventMessage

/**
 * Unit test for tenant CorrelationDataProvider.
 *
 * **AC5 Coverage:**
 * - AC5: Event metadata enriched with tenant_id during command processing
 *
 * Epic 4, Story 4.5: AC5
 */
class TenantCorrelationDataProviderTest :
    FunSpec({
        val config = TenantEventProcessingConfiguration()
        val provider = config.tenantCorrelationDataProvider()

        afterEach {
            // Ensure context is always cleared after each test
            @Suppress("SwallowedException")
            try {
                TenantContext.clearCurrentTenant()
            } catch (e: Exception) {
                // Ignore if already cleared - safe to swallow in test cleanup
            }
        }

        context("AC5: Automatic metadata enrichment") {
            test("Add tenant_id to metadata when TenantContext is set") {
                // Given - TenantContext set
                val tenantId = "tenant-test-123"
                TenantContext.setCurrentTenantId(tenantId)

                // When - Provider generates correlation data
                val message = GenericEventMessage.asEventMessage<String>("test-event")
                val correlationData = provider.correlationDataFor(message)

                // Then - AC5: tenant_id added to metadata
                correlationData shouldContain ("tenant_id" to tenantId)
            }

            test("Do NOT add tenant_id when TenantContext is not set") {
                // Given - NO TenantContext set (system event)

                // When - Provider generates correlation data
                val message = GenericEventMessage.asEventMessage<String>("system-event")
                val correlationData = provider.correlationDataFor(message)

                // Then - No tenant_id in metadata (empty map)
                @Suppress("UNCHECKED_CAST")
                val dataMap = correlationData as Map<String, Any>
                dataMap.containsKey("tenant_id") shouldBe false
                correlationData.size shouldBe 0
            }
        }
    })
