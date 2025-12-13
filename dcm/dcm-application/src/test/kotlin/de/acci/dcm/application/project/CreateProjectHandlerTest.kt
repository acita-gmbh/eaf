package de.acci.dcm.application.project

import de.acci.dcm.domain.project.ProjectId
import de.acci.dcm.domain.project.ProjectName
import de.acci.dcm.domain.project.events.ProjectCreated
import de.acci.dcm.domain.project.events.UserAssignedToProject
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventStore
import de.acci.eaf.eventsourcing.EventStoreError
import de.acci.eaf.core.result.failure
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
import java.util.UUID

@DisplayName("CreateProjectHandler")
class CreateProjectHandlerTest {

    private val eventStore = mockk<EventStore>()
    private val projectQueryService = mockk<ProjectQueryService>()

    private fun createCommand(
        tenantId: TenantId = TenantId(UUID.randomUUID()),
        createdBy: UserId = UserId(UUID.randomUUID()),
        name: ProjectName = ProjectName.of("Test Project"),
        description: String? = "Test project description"
    ) = CreateProjectCommand(
        tenantId = tenantId,
        createdBy = createdBy,
        name = name,
        description = description
    )

    @Nested
    @DisplayName("handle()")
    inner class HandleTests {

        @Test
        @DisplayName("should create aggregate and persist events to store")
        fun `should create aggregate and persist events to store`() = runTest {
            // Given
            val command = createCommand()
            val eventsSlot = slot<List<DomainEvent>>()

            coEvery {
                projectQueryService.findByName(any(), any())
            } returns null

            coEvery {
                eventStore.append(any(), capture(eventsSlot), any())
            } returns 2L.success()

            val handler = CreateProjectHandler(eventStore, projectQueryService)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            coVerify(exactly = 1) {
                eventStore.append(any(), any(), eq(0L))
            }

            val events = eventsSlot.captured
            assertEquals(2, events.size) // ProjectCreated + UserAssignedToProject
            assertTrue(events[0] is ProjectCreated)
            assertTrue(events[1] is UserAssignedToProject)
        }

        @Test
        @DisplayName("should return created project ID on success")
        fun `should return created project ID on success`() = runTest {
            // Given
            val command = createCommand()

            coEvery {
                projectQueryService.findByName(any(), any())
            } returns null

            coEvery {
                eventStore.append(any(), any(), any())
            } returns 2L.success()

            val handler = CreateProjectHandler(eventStore, projectQueryService)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            val success = result as Result.Success
            assertTrue(success.value.projectId.value.toString().isNotEmpty())
        }

        @Test
        @DisplayName("should return NameAlreadyExists when project name is taken")
        fun `should return NameAlreadyExists when project name is taken`() = runTest {
            // Given
            val command = createCommand()
            val existingId = ProjectId.generate()

            coEvery {
                projectQueryService.findByName(command.tenantId, command.name)
            } returns existingId

            val handler = CreateProjectHandler(eventStore, projectQueryService)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is CreateProjectError.NameAlreadyExists)

            // And event store should not be called
            coVerify(exactly = 0) {
                eventStore.append(any(), any(), any())
            }
        }

        @Test
        @DisplayName("should return ConcurrencyConflict on event store conflict")
        fun `should return ConcurrencyConflict on event store conflict`() = runTest {
            // Given
            val command = createCommand()

            coEvery {
                projectQueryService.findByName(any(), any())
            } returns null

            coEvery {
                eventStore.append(any(), any(), any())
            } returns EventStoreError.ConcurrencyConflict(
                aggregateId = UUID.randomUUID(),
                expectedVersion = 0,
                actualVersion = 1
            ).failure()

            val handler = CreateProjectHandler(eventStore, projectQueryService)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is CreateProjectError.ConcurrencyConflict)
        }

        @Test
        @DisplayName("should return PersistenceFailure on event store exception")
        fun `should return PersistenceFailure on event store exception`() = runTest {
            // Given
            val command = createCommand()

            coEvery {
                projectQueryService.findByName(any(), any())
            } returns null

            coEvery {
                eventStore.append(any(), any(), any())
            } throws RuntimeException("Database error")

            val handler = CreateProjectHandler(eventStore, projectQueryService)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is CreateProjectError.PersistenceFailure)
        }

        @Test
        @DisplayName("should check uniqueness before creating aggregate")
        fun `should check uniqueness before creating aggregate`() = runTest {
            // Given
            val command = createCommand()

            coEvery {
                projectQueryService.findByName(command.tenantId, command.name)
            } returns null

            coEvery {
                eventStore.append(any(), any(), any())
            } returns 2L.success()

            val handler = CreateProjectHandler(eventStore, projectQueryService)

            // When
            handler.handle(command)

            // Then
            coVerify(exactly = 1) {
                projectQueryService.findByName(command.tenantId, command.name)
            }
        }

        @Test
        @DisplayName("should auto-assign creator as PROJECT_ADMIN")
        fun `should auto-assign creator as PROJECT_ADMIN`() = runTest {
            // Given
            val createdBy = UserId(UUID.randomUUID())
            val command = createCommand(createdBy = createdBy)
            val eventsSlot = slot<List<DomainEvent>>()

            coEvery {
                projectQueryService.findByName(any(), any())
            } returns null

            coEvery {
                eventStore.append(any(), capture(eventsSlot), any())
            } returns 2L.success()

            val handler = CreateProjectHandler(eventStore, projectQueryService)

            // When
            handler.handle(command)

            // Then
            val events = eventsSlot.captured
            val assignedEvent = events[1] as UserAssignedToProject
            assertEquals(createdBy, assignedEvent.userId)
            assertEquals("PROJECT_ADMIN", assignedEvent.role.name)
            assertEquals(createdBy, assignedEvent.assignedBy)
        }

        @Test
        @DisplayName("should create project without description")
        fun `should create project without description`() = runTest {
            // Given
            val command = createCommand(description = null)

            coEvery {
                projectQueryService.findByName(any(), any())
            } returns null

            coEvery {
                eventStore.append(any(), any(), any())
            } returns 2L.success()

            val handler = CreateProjectHandler(eventStore, projectQueryService)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
        }
    }
}
