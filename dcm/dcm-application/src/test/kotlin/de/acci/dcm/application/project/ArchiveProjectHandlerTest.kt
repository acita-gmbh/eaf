package de.acci.dcm.application.project

import de.acci.dcm.domain.project.ProjectId
import de.acci.dcm.domain.project.ProjectName
import de.acci.dcm.domain.project.ProjectRole
import de.acci.dcm.domain.project.events.ProjectArchived
import de.acci.dcm.domain.project.events.ProjectCreated
import de.acci.dcm.domain.project.events.UserAssignedToProject
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata
import de.acci.eaf.eventsourcing.EventStore
import de.acci.eaf.eventsourcing.StoredEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("ArchiveProjectHandler")
class ArchiveProjectHandlerTest {

    private val eventStore = mockk<EventStore>()
    private val eventDeserializer = mockk<ProjectEventDeserializer>()

    private val testTenantId = TenantId(UUID.randomUUID())
    private val testUserId = UserId(UUID.randomUUID())
    private val testProjectId = ProjectId.generate()

    private fun createTestMetadata(): EventMetadata = EventMetadata(
        tenantId = testTenantId,
        userId = testUserId,
        correlationId = CorrelationId(UUID.randomUUID()),
        timestamp = Instant.now()
    )

    private fun createArchiveCommand(
        projectId: ProjectId = testProjectId,
        tenantId: TenantId = testTenantId,
        archivedBy: UserId = testUserId,
        version: Long = 2L
    ) = ArchiveProjectCommand(
        tenantId = tenantId,
        projectId = projectId,
        archivedBy = archivedBy,
        version = version
    )

    private fun createActiveProjectEvents(): List<DomainEvent> {
        val metadata = createTestMetadata()
        return listOf(
            ProjectCreated(
                aggregateId = testProjectId,
                name = ProjectName.of("Test Project"),
                description = "Test description",
                metadata = metadata
            ),
            UserAssignedToProject(
                aggregateId = testProjectId,
                userId = testUserId,
                role = ProjectRole.PROJECT_ADMIN,
                assignedBy = testUserId,
                metadata = metadata
            )
        )
    }

    private fun createArchivedProjectEvents(): List<DomainEvent> {
        val metadata = createTestMetadata()
        return listOf(
            ProjectCreated(
                aggregateId = testProjectId,
                name = ProjectName.of("Test Project"),
                description = "Test description",
                metadata = metadata
            ),
            UserAssignedToProject(
                aggregateId = testProjectId,
                userId = testUserId,
                role = ProjectRole.PROJECT_ADMIN,
                assignedBy = testUserId,
                metadata = metadata
            ),
            ProjectArchived(
                aggregateId = testProjectId,
                metadata = metadata
            )
        )
    }

    private fun createStoredEvents(
        count: Int = 2,
        tenantId: TenantId = testTenantId
    ): List<StoredEvent> {
        val metadata = EventMetadata(
            tenantId = tenantId,
            userId = testUserId,
            correlationId = CorrelationId(UUID.randomUUID()),
            timestamp = Instant.now()
        )
        return (1..count).map { i ->
            StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = testProjectId.value,
                aggregateType = "Project",
                eventType = "ProjectCreated",
                payload = "{}",
                metadata = metadata,
                version = i.toLong(),
                createdAt = Instant.now()
            )
        }
    }

    @Nested
    @DisplayName("handle()")
    inner class HandleTests {

        @Test
        @DisplayName("should archive project and persist events")
        fun `should archive project and persist events`() = runTest {
            // Given
            val command = createArchiveCommand()
            val events = createActiveProjectEvents()
            val eventsSlot = slot<List<DomainEvent>>()

            coEvery { eventStore.load(testProjectId.value) } returns createStoredEvents(count = 2)

            coEvery { eventDeserializer.deserialize(any()) } returnsMany events

            coEvery {
                eventStore.append(any(), capture(eventsSlot), eq(2L))
            } returns 3L.success()

            val handler = ArchiveProjectHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            coVerify(exactly = 1) {
                eventStore.append(testProjectId.value, any(), 2L)
            }

            val capturedEvents = eventsSlot.captured
            assertEquals(1, capturedEvents.size)
            assertTrue(capturedEvents[0] is ProjectArchived)
        }

        @Test
        @DisplayName("should return NotFound when project does not exist")
        fun `should return NotFound when project does not exist`() = runTest {
            // Given
            val command = createArchiveCommand()

            coEvery { eventStore.load(testProjectId.value) } returns emptyList()

            val handler = ArchiveProjectHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is ArchiveProjectError.NotFound)
        }

        @Test
        @DisplayName("should return AlreadyArchived when project is already archived")
        fun `should return AlreadyArchived when project is already archived`() = runTest {
            // Given
            val command = createArchiveCommand(version = 3L)
            val events = createArchivedProjectEvents()

            coEvery { eventStore.load(testProjectId.value) } returns createStoredEvents(count = 3)

            coEvery { eventDeserializer.deserialize(any()) } returnsMany events

            val handler = ArchiveProjectHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is ArchiveProjectError.AlreadyArchived)
        }

        @Test
        @DisplayName("should return ConcurrencyConflict on version mismatch")
        fun `should return ConcurrencyConflict on version mismatch`() = runTest {
            // Given
            val command = createArchiveCommand(version = 5L) // Wrong version
            val events = createActiveProjectEvents()

            coEvery { eventStore.load(testProjectId.value) } returns createStoredEvents(count = 2)

            coEvery { eventDeserializer.deserialize(any()) } returnsMany events

            val handler = ArchiveProjectHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is ArchiveProjectError.ConcurrencyConflict)
        }

        @Test
        @DisplayName("should return NotFound when tenant does not match (security)")
        fun `should return NotFound when tenant does not match`() = runTest {
            // Given: Events belong to a different tenant
            val otherTenantId = TenantId(UUID.randomUUID())
            val command = createArchiveCommand()

            coEvery { eventStore.load(testProjectId.value) } returns createStoredEvents(
                count = 2,
                tenantId = otherTenantId
            )

            val handler = ArchiveProjectHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then: Returns NotFound (not Forbidden) to prevent tenant enumeration
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is ArchiveProjectError.NotFound)
        }
    }
}
