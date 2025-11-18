package com.axians.eaf.framework.multitenancy

import com.axians.eaf.framework.multitenancy.config.TenantEventProcessingConfiguration
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

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

                // When - Create provider and generate correlation data
                val config = TenantEventProcessingConfiguration()
                val provider = config.tenantCorrelationDataProvider()

                val message =
                    object : org.axonframework.messaging.Message<String> {
                        override fun getIdentifier() = "test-event-id"

                        override fun getMetaData() =
                            org.axonframework.messaging.MetaData
                                .emptyInstance()

                        override fun getPayload() = "test-event-payload"

                        override fun getPayloadType(): Class<String> = String::class.java

                        override fun withMetaData(metaData: MutableMap<String, *>) = this

                        override fun andMetaData(metaData: MutableMap<String, *>) = this
                    }

                val correlationData = provider.correlationDataFor(message)

                // Then - AC5: tenant_id added to metadata
                correlationData.containsKey("tenant_id") shouldBe true
                correlationData["tenant_id"] shouldBe tenantId
            }

            test("Do NOT add tenant_id when TenantContext is not set") {
                // Given - NO TenantContext set (system event)

                // When - Create provider and generate correlation data
                val config = TenantEventProcessingConfiguration()
                val provider = config.tenantCorrelationDataProvider()

                val message =
                    object : org.axonframework.messaging.Message<String> {
                        override fun getIdentifier() = "system-event-id"

                        override fun getMetaData() =
                            org.axonframework.messaging.MetaData
                                .emptyInstance()

                        override fun getPayload() = "system-event-payload"

                        override fun getPayloadType(): Class<String> = String::class.java

                        override fun withMetaData(metaData: MutableMap<String, *>) = this

                        override fun andMetaData(metaData: MutableMap<String, *>) = this
                    }

                val correlationData = provider.correlationDataFor(message)

                // Then - No tenant_id in metadata (empty map)
                correlationData.containsKey("tenant_id") shouldBe false
                correlationData.size shouldBe 0
            }
        }
    })
