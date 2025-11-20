package com.axians.eaf.framework.multitenancy

import com.axians.eaf.framework.multitenancy.config.TenantEventProcessingConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Unit test for tenant CorrelationDataProvider.
 *
 * **AC5 & AC6 Coverage:**
 * - AC5: Event metadata enriched with tenant_id during command processing
 * - AC6: Validates metadata enrichment (unit test level - E2E deferred to Story 4.6)
 *
 * **Note:** Full integration test with Widget aggregate deferred to Story 4.6
 * as Widget commands/events need tenantId field added first.
 *
 * Migrated from Kotest to JUnit 6 on 2025-11-20
 *
 * Epic 4, Story 4.5: AC5, AC6
 */
class TenantCorrelationDataProviderTest {

    @AfterEach
    fun afterEach() {
        // Ensure context is always cleared after each test
        @Suppress("SwallowedException")
        try {
            TenantContext.clearCurrentTenant()
        } catch (e: Exception) {
            // Ignore if already cleared - safe to swallow in test cleanup
        }
    }

    @Test
    fun `Add tenant_id to metadata when TenantContext is set`() {
        // Given - TenantContext set
        val tenantId = "tenant-test-123"
        TenantContext.setCurrentTenantId(tenantId)

        // When - Create provider and generate correlation data
        val config = TenantEventProcessingConfiguration()
        val provider = config.tenantCorrelationDataProvider()

        val message = object : org.axonframework.messaging.Message<String> {
            override fun getIdentifier() = "test-event-id"

            override fun getMetaData() = org.axonframework.messaging.MetaData.emptyInstance()

            override fun getPayload() = "test-event-payload"

            override fun getPayloadType(): Class<String> = String::class.java

            override fun withMetaData(metaData: MutableMap<String, *>) = this

            override fun andMetaData(metaData: MutableMap<String, *>) = this
        }

        val correlationData = provider.correlationDataFor(message)

        // Then - AC5: tenant_id added to metadata
        assertThat(correlationData.containsKey("tenant_id")).isTrue()
        assertThat(correlationData["tenant_id"]).isEqualTo(tenantId)
    }

    @Test
    fun `Do NOT add tenant_id when TenantContext is not set`() {
        // Given - NO TenantContext set (system event)

        // When - Create provider and generate correlation data
        val config = TenantEventProcessingConfiguration()
        val provider = config.tenantCorrelationDataProvider()

        val message = object : org.axonframework.messaging.Message<String> {
            override fun getIdentifier() = "system-event-id"

            override fun getMetaData() = org.axonframework.messaging.MetaData.emptyInstance()

            override fun getPayload() = "system-event-payload"

            override fun getPayloadType(): Class<String> = String::class.java

            override fun withMetaData(metaData: MutableMap<String, *>) = this

            override fun andMetaData(metaData: MutableMap<String, *>) = this
        }

        val correlationData = provider.correlationDataFor(message)

        // Then - No tenant_id in metadata (empty map)
        assertThat(correlationData.containsKey("tenant_id")).isFalse()
        assertThat(correlationData.size).isEqualTo(0)
    }
}
