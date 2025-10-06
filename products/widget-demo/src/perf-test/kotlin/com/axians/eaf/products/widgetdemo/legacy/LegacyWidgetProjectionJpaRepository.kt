package com.axians.eaf.products.widgetdemo.legacy

import org.springframework.data.jpa.repository.JpaRepository

interface LegacyWidgetProjectionJpaRepository : JpaRepository<LegacyWidgetProjectionEntity, String> {
    fun findByTenantIdOrderByCreatedAtDesc(tenantId: String): List<LegacyWidgetProjectionEntity>
}
