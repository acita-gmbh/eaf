package de.acci.dcm.application.project

import de.acci.dcm.domain.project.ProjectId
import de.acci.dcm.domain.project.ProjectName
import de.acci.dcm.domain.project.ProjectRole
import de.acci.dcm.domain.project.events.ProjectCreated
import de.acci.dcm.domain.project.events.ProjectUpdated
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

@DisplayName("UpdateProjectHandler")
class UpdateProjectHandlerTest {

    private val eventStore = mockk<EventStore>()
    private val eventDeserializer = mockk<ProjectEventDeserializer>()
    private val projectQueryService = mockk<ProjectQueryService>()

    private val testTenantId = TenantId(UUID.randomUUID())
    private val testUserId = UserId(UUID.randomUUID())
    private val testProjectId = ProjectId.generate()

    private fun createTestMetadata(): EventMetadata = EventMetadata(
        tenantId = testTenantId,
        userId = testUserId,
        correlationId = CorrelationId(UUID.randomUUID()),
        timestamp = Instant.now()
    )

    private fun createUpdateCommand(
        projectId: ProjectId = testProjectId,
        tenantId: TenantId = testTenantId,
        updatedBy: UserId = testUserId,
        name: ProjectName = ProjectName.of("Updated Name"),
        description: String? = "Updated description",
        version: Long = 2L
    ) = UpdateProjectCommand(
        tenantId = tenantId,
        projectId = projectId,
        updatedBy = updatedBy,
        name = name,
        description = description,
        version = version
    )

    private fun createProjectEvents(): List<DomainEvent> {
        val metadata = createTestMetadata()
        return listOf(
            ProjectCreated(
                aggregateId = testProjectId,
                name = ProjectName.of("Original Name"),
                description = "Original description",
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

    @Nested
    @DisplayName("handle()")
    inner class HandleTests {

        @Test
        @DisplayName("should update project and persist events")
        fun `should update project and persist events`() = runTest {
            // Given
            val command = createUpdateCommand()
            val events = createProjectEvents()
            val eventsSlot = slot<List<DomainEvent>>()

            coEvery { eventStore.load(testProjectId.value) } returns listOf(
                mockk<StoredEvent>(),
                mockk<StoredEvent>()
            )

            coEvery { eventDeserializer.deserialize(any()) } returnsMany events

            coEvery {
                projectQueryService.findByName(command.tenantId, command.name)
            } returns null

            coEvery {
                eventStore.append(any(), capture(eventsSlot), eq(2L))
            } returns 3L.success()

            val handler = UpdateProjectHandler(eventStore, eventDeserializer, projectQueryService)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            coVerify(exactly = 1) {
                eventStore.append(testProjectId.value, any(), 2L)
            }

            val capturedEvents = eventsSlot.captured
            assertEquals(1, capturedEvents.size)
            assertTrue(capturedEvents[0] is ProjectUpdated)
        }

        @Test
        @DisplayName("should return NotFound when project does not exist")
        fun `should return NotFound when project does not exist`() = runTest {
            // Given
            val command = createUpdateCommand()

            coEvery { eventStore.load(testProjectId.value) } returns emptyList()

            val handler = UpdateProjectHandler(eventStore, eventDeserializer, projectQueryService)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is UpdateProjectError.NotFound)
        }

        @Test
        @DisplayName("should return ConcurrencyConflict on version mismatch")
        fun `should return ConcurrencyConflict on version mismatch`() = runTest {
            // Given
            val command = createUpdateCommand(version = 5L) // Wrong version
            val events = createProjectEvents()

            coEvery { eventStore.load(testProjectId.value) } returns listOf(
                mockk<StoredEvent>(),
                mockk<StoredEvent>()
            )

            coEvery { eventDeserializer.deserialize(any()) } returnsMany events

            val handler = UpdateProjectHandler(eventStore, eventDeserializer, projectQueryService)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is UpdateProjectError.ConcurrencyConflict)
        }

        @Test
        @DisplayName("should return NameAlreadyExists when new name conflicts")
        fun `should return NameAlreadyExists when new name conflicts`() = runTest {
            // Given
            val command = createUpdateCommand(name = ProjectName.of("Conflicting Name"))
            val events = createProjectEvents()
            val conflictingId = ProjectId.generate()

            coEvery { eventStore.load(testProjectId.value) } returns listOf(
                mockk<StoredEvent>(),
                mockk<StoredEvent>()
            )

            coEvery { eventDeserializer.deserialize(any()) } returnsMany events

            coEvery {
                projectQueryService.findByName(command.tenantId, command.name)
            } returns conflictingId

            val handler = UpdateProjectHandler(eventStore, eventDeserializer, projectQueryService)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is UpdateProjectError.NameAlreadyExists)
        }

        @Test
        @DisplayName("should allow update when name stays the same")
        fun `should allow update when name stays the same`() = runTest {
            // Given: Command uses same name (case-insensitive)
            val command = createUpdateCommand(name = ProjectName.of("Original Name"))
            val events = createProjectEvents()

            coEvery { eventStore.load(testProjectId.value) } returns listOf(
                mockk<StoredEvent>(),
                mockk<StoredEvent>()
            )

            coEvery { eventDeserializer.deserialize(any()) } returnsMany events

            // Name check returns the same project (not a conflict)
            coEvery {
                projectQueryService.findByName(command.tenantId, command.name)
            } returns testProjectId

            coEvery {
                eventStore.append(any(), any(), any())
            } returns 3L.success()

            val handler = UpdateProjectHandler(eventStore, eventDeserializer, projectQueryService)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
        }
    }
}
