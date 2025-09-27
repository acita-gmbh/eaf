package com.axians.eaf.framework.cqrs.interceptors

import com.axians.eaf.framework.security.tenant.TenantContext
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import org.axonframework.commandhandling.GenericCommandMessage

/**
 * Unit tests for TenantCorrelationDataProvider validating metadata enrichment.
 */
class TenantCorrelationDataProviderSpec :
    FunSpec({
        val tenantContext = TenantContext(meterRegistry = null)
        val provider = TenantCorrelationDataProvider(tenantContext)

        test("should enrich metadata with tenantId when context is present") {
            tenantContext.setCurrentTenantId("tenant-abc")

            val message = GenericCommandMessage.asCommandMessage<String>("test-command")
            val metadata = provider.correlationDataFor(message)

            metadata shouldContainExactly mapOf("tenantId" to "tenant-abc")

            tenantContext.clearCurrentTenant()
        }

        test("should return empty metadata when tenant context is absent") {
            val message = GenericCommandMessage.asCommandMessage<String>("test-command")
            val metadata = provider.correlationDataFor(message)

            metadata.shouldBeEmpty()
        }

        test("should handle multiple invocations with different tenants") {
            val message = GenericCommandMessage.asCommandMessage<String>("test-command")

            tenantContext.setCurrentTenantId("tenant-1")
            val metadata1 = provider.correlationDataFor(message)
            tenantContext.clearCurrentTenant()

            tenantContext.setCurrentTenantId("tenant-2")
            val metadata2 = provider.correlationDataFor(message)
            tenantContext.clearCurrentTenant()

            metadata1 shouldContainExactly mapOf("tenantId" to "tenant-1")
            metadata2 shouldContainExactly mapOf("tenantId" to "tenant-2")
        }
    })
