package de.acci.dvmm.infrastructure.projection

import de.acci.dvmm.application.vm.VmProvisioningProgressProjection
import de.acci.dvmm.application.vm.VmProvisioningProgressProjectionRepository
import de.acci.dvmm.domain.vm.VmProvisioningStage
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.infrastructure.jooq.public.tables.references.PROVISIONING_PROGRESS
import de.acci.eaf.core.types.TenantId
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneId

@Repository
class VmProvisioningProgressProjectionRepositoryAdapter(
    private val dsl: DSLContext
) : VmProvisioningProgressProjectionRepository {

    override suspend fun save(projection: VmProvisioningProgressProjection, tenantId: TenantId) {
        dsl.insertInto(PROVISIONING_PROGRESS)
            .set(PROVISIONING_PROGRESS.VM_REQUEST_ID, projection.vmRequestId.value)
            .set(PROVISIONING_PROGRESS.STAGE, projection.stage.name)
            .set(PROVISIONING_PROGRESS.DETAILS, projection.details)
            .set(PROVISIONING_PROGRESS.STARTED_AT, OffsetDateTime.ofInstant(projection.startedAt, ZoneId.of("UTC")))
            .set(PROVISIONING_PROGRESS.UPDATED_AT, OffsetDateTime.ofInstant(projection.updatedAt, ZoneId.of("UTC")))
            .set(PROVISIONING_PROGRESS.TENANT_ID, tenantId.value)
            .onConflict(PROVISIONING_PROGRESS.VM_REQUEST_ID)
            .doUpdate()
            .set(PROVISIONING_PROGRESS.STAGE, projection.stage.name)
            .set(PROVISIONING_PROGRESS.DETAILS, projection.details)
            // Keep startedAt unchanged on update - only set on first insert
            .set(PROVISIONING_PROGRESS.UPDATED_AT, OffsetDateTime.ofInstant(projection.updatedAt, ZoneId.of("UTC")))
            .awaitFirstOrNull()
    }

    override suspend fun delete(vmRequestId: VmRequestId, tenantId: TenantId) {
        dsl.deleteFrom(PROVISIONING_PROGRESS)
            .where(PROVISIONING_PROGRESS.VM_REQUEST_ID.eq(vmRequestId.value))
            .and(PROVISIONING_PROGRESS.TENANT_ID.eq(tenantId.value))
            .awaitFirstOrNull()
    }

    override suspend fun findByVmRequestId(vmRequestId: VmRequestId, tenantId: TenantId): VmProvisioningProgressProjection? {
        val record = dsl.selectFrom(PROVISIONING_PROGRESS)
            .where(PROVISIONING_PROGRESS.VM_REQUEST_ID.eq(vmRequestId.value))
            .and(PROVISIONING_PROGRESS.TENANT_ID.eq(tenantId.value))
            .awaitFirstOrNull() ?: return null

        return VmProvisioningProgressProjection(
            vmRequestId = VmRequestId(record.vmRequestId),
            stage = VmProvisioningStage.valueOf(record.stage),
            details = record.details,
            startedAt = record.startedAt.toInstant(),
            updatedAt = record.updatedAt.toInstant()
        )
    }
}
