package de.acci.dvmm.infrastructure.projection

import de.acci.dvmm.application.vmrequest.NewVmRequestProjection
import de.acci.dvmm.application.vmrequest.VmRequestProjectionUpdater
import de.acci.dvmm.application.vmrequest.VmRequestStatusUpdate
import de.acci.dvmm.infrastructure.jooq.`public`.tables.pojos.VmRequestsProjection
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.eventsourcing.projection.ProjectionError
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Infrastructure adapter that implements VmRequestProjectionUpdater
 * using the jOOQ-based VmRequestProjectionRepository.
 *
 * This adapter translates application-layer projection operations
 * into infrastructure-layer database operations.
 *
 * ## Error Handling
 *
 * Projection updates return [Result] types to make errors explicit.
 * Errors are logged at the infrastructure layer, but callers decide
 * whether to propagate failures or allow the command to succeed.
 * Failed projections can be reconstructed from the event store.
 */
public class VmRequestProjectionUpdaterAdapter(
    private val projectionRepository: VmRequestProjectionRepository
) : VmRequestProjectionUpdater {

    private val logger = KotlinLogging.logger {}

    override suspend fun insert(data: NewVmRequestProjection): Result<Unit, ProjectionError> {
        return try {
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val projection = VmRequestsProjection(
                id = data.id.value,
                tenantId = data.tenantId.value,
                requesterId = data.requesterId.value,
                requesterName = data.requesterName,
                projectId = data.projectId.value,
                projectName = data.projectName,
                vmName = data.vmName.value,
                size = data.size.name,
                cpuCores = data.size.cpuCores,
                memoryGb = data.size.memoryGb,
                diskGb = data.size.diskGb,
                justification = data.justification,
                status = data.status.name,
                approvedBy = null,
                approvedByName = null,
                rejectedBy = null,
                rejectedByName = null,
                rejectionReason = null,
                createdAt = now,
                updatedAt = now,
                version = data.version
            )
            projectionRepository.insert(projection)
            logger.debug { "Inserted projection for VM request: ${data.id.value}" }
            Unit.success()
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to insert projection for VM request: ${data.id.value}. " +
                    "Projection can be reconstructed from event store."
            }
            ProjectionError.DatabaseError(
                aggregateId = data.id.value.toString(),
                message = "Failed to insert projection: ${e.message}",
                cause = e
            ).failure()
        }
    }

    override suspend fun updateStatus(data: VmRequestStatusUpdate): Result<Unit, ProjectionError> {
        return try {
            val rowsUpdated = projectionRepository.updateStatus(
                id = data.id.value,
                status = data.status.name,
                approvedBy = data.approvedBy?.value,
                approvedByName = data.approvedByName,
                rejectedBy = data.rejectedBy?.value,
                rejectedByName = data.rejectedByName,
                rejectionReason = data.rejectionReason,
                version = data.version
            )
            if (rowsUpdated > 0) {
                logger.debug {
                    "Updated projection status for VM request: " +
                        "${data.id.value} -> ${data.status}"
                }
                Unit.success()
            } else {
                logger.warn {
                    "No projection found to update for VM request: ${data.id.value}. " +
                        "Projection may need to be reconstructed from event store."
                }
                ProjectionError.NotFound(
                    aggregateId = data.id.value.toString()
                ).failure()
            }
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to update projection for VM request: ${data.id.value}. " +
                    "Projection can be reconstructed from event store."
            }
            ProjectionError.DatabaseError(
                aggregateId = data.id.value.toString(),
                message = "Failed to update projection: ${e.message}",
                cause = e
            ).failure()
        }
    }
}
