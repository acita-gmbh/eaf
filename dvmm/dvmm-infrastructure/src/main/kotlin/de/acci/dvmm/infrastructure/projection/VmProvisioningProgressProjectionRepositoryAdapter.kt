package de.acci.dvmm.infrastructure.projection

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import de.acci.dvmm.application.vm.VmProvisioningProgressProjection
import de.acci.dvmm.application.vm.VmProvisioningProgressProjectionRepository
import de.acci.dvmm.domain.vm.VmProvisioningStage
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.infrastructure.jooq.public.tables.references.PROVISIONING_PROGRESS
import de.acci.eaf.core.types.TenantId
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

private val logger = KotlinLogging.logger {}

@Repository
internal class VmProvisioningProgressProjectionRepositoryAdapter(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper
) : VmProvisioningProgressProjectionRepository {

    override suspend fun save(projection: VmProvisioningProgressProjection, tenantId: TenantId) {
        // Serialize stage timestamps to JSON
        val timestampsJson = serializeStageTimestamps(projection.stageTimestamps)

        // VM_REQUEST_ID is the PK and globally unique (UUID from VmRequest aggregate).
        // ON CONFLICT targets PK only - RLS ensures tenant isolation via WHERE clause.
        dsl.insertInto(PROVISIONING_PROGRESS)
            .set(PROVISIONING_PROGRESS.VM_REQUEST_ID, projection.vmRequestId.value)
            .set(PROVISIONING_PROGRESS.STAGE, projection.stage.name)
            .set(PROVISIONING_PROGRESS.DETAILS, projection.details)
            .set(PROVISIONING_PROGRESS.STARTED_AT, OffsetDateTime.ofInstant(projection.startedAt, ZoneOffset.UTC))
            .set(PROVISIONING_PROGRESS.UPDATED_AT, OffsetDateTime.ofInstant(projection.updatedAt, ZoneOffset.UTC))
            .set(PROVISIONING_PROGRESS.TENANT_ID, tenantId.value)
            .set(PROVISIONING_PROGRESS.STAGE_TIMESTAMPS, timestampsJson)
            .onConflict(PROVISIONING_PROGRESS.VM_REQUEST_ID)
            .doUpdate()
            .set(PROVISIONING_PROGRESS.STAGE, projection.stage.name)
            .set(PROVISIONING_PROGRESS.DETAILS, projection.details)
            // Keep startedAt unchanged on update - only set on first insert
            .set(PROVISIONING_PROGRESS.UPDATED_AT, OffsetDateTime.ofInstant(projection.updatedAt, ZoneOffset.UTC))
            .set(PROVISIONING_PROGRESS.STAGE_TIMESTAMPS, timestampsJson)
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

        val stage = VmProvisioningStage.valueOf(record.stage)
        val stageTimestamps = deserializeStageTimestamps(record.stageTimestamps ?: "{}")
        val estimatedRemaining = VmProvisioningProgressProjection.calculateEstimatedRemaining(stage)

        return VmProvisioningProgressProjection(
            vmRequestId = VmRequestId(record.vmRequestId),
            stage = stage,
            details = record.details,
            startedAt = record.startedAt.toInstant(),
            updatedAt = record.updatedAt.toInstant(),
            stageTimestamps = stageTimestamps,
            estimatedRemainingSeconds = estimatedRemaining
        )
    }

    /**
     * Serializes stage timestamps to JSON string for JSONB column storage.
     *
     * Note: PostgreSQL JSONB column accepts JSON as a string, which is then parsed
     * and stored in binary format. jOOQ's JSONB binding expects String input,
     * so we serialize the map to JSON here. On read, PostgreSQL returns the JSONB
     * as a JSON string which we deserialize back to the map.
     */
    private fun serializeStageTimestamps(timestamps: Map<VmProvisioningStage, Instant>): String {
        val stringMap = timestamps.mapKeys { it.key.name }.mapValues { it.value.toString() }
        return objectMapper.writeValueAsString(stringMap)
    }

    private fun deserializeStageTimestamps(json: String): Map<VmProvisioningStage, Instant> {
        if (json.isBlank() || json == "{}") return emptyMap()
        val stringMap: Map<String, String> = objectMapper.readValue(json, object : TypeReference<Map<String, String>>() {})
        return stringMap.mapNotNull { (key, value) ->
            try {
                VmProvisioningStage.valueOf(key) to Instant.parse(value)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to deserialize stage timestamp entry: key='$key', value='$value'" }
                null
            }
        }.toMap()
    }
}
