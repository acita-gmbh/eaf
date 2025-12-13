package de.acci.dcm.domain.project

import de.acci.dcm.domain.project.events.ProjectCreated
import de.acci.dcm.domain.project.events.ProjectUpdated
import de.acci.dcm.domain.project.events.UserAssignedToProject
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.EventMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

@DisplayName("ProjectAggregate")
class ProjectAggregateTest {

    private val testTenantId = TenantId(UUID.randomUUID())
    private val testUserId = UserId(UUID.randomUUID())

    private fun createTestMetadata(): EventMetadata = EventMetadata(
        tenantId = testTenantId,
        userId = testUserId,
        correlationId = CorrelationId(UUID.randomUUID()),
        timestamp = Instant.now()
    )

    @Nested
    @DisplayName("Project Creation (AC-4.1.1, AC-4.1.2)")
    inner class ProjectCreation {

        @Test
        fun `creates project with valid data`() {
            // Given: Valid project data
            val name = ProjectName.of("Alpha Project")
            val description = "Test project description"
            val metadata = createTestMetadata()

            // When: Create project
            val aggregate = ProjectAggregate.create(
                name = name,
                description = description,
                metadata = metadata
            )

            // Then: Project is created with correct state
            assertNotNull(aggregate.id)
            assertEquals(name.value, aggregate.name.value)
            assertEquals(description, aggregate.description)
            assertEquals(ProjectStatus.ACTIVE, aggregate.status)
            assertEquals(testUserId, aggregate.createdBy)
            assertEquals(2L, aggregate.version) // After both events (ProjectCreated + UserAssigned)
        }

        @Test
        fun `creates project without description`() {
            // Given: No description
            val name = ProjectName.of("Beta Project")
            val metadata = createTestMetadata()

            // When: Create project
            val aggregate = ProjectAggregate.create(
                name = name,
                description = null,
                metadata = metadata
            )

            // Then: Description is null
            assertEquals(null, aggregate.description)
        }

        @Test
        fun `emits ProjectCreated event on creation`() {
            // Given: Valid project data
            val name = ProjectName.of("Gamma Project")
            val metadata = createTestMetadata()

            // When: Create project
            val aggregate = ProjectAggregate.create(
                name = name,
                description = "Test",
                metadata = metadata
            )

            // Then: ProjectCreated event is emitted
            val events = aggregate.uncommittedEvents
            assertTrue(events.isNotEmpty())
            assertTrue(events[0] is ProjectCreated)
            val created = events[0] as ProjectCreated
            assertEquals(aggregate.id, created.aggregateId)
            assertEquals(name.value, created.name.value)
        }

        @Test
        fun `auto-assigns creator as PROJECT_ADMIN (AC-4-1-2)`() {
            // Given: A new project
            val name = ProjectName.of("Delta Project")
            val metadata = createTestMetadata()

            // When: Create project
            val aggregate = ProjectAggregate.create(
                name = name,
                description = null,
                metadata = metadata
            )

            // Then: Creator is auto-assigned as PROJECT_ADMIN
            val events = aggregate.uncommittedEvents
            assertEquals(2, events.size) // ProjectCreated + UserAssignedToProject

            val assignedEvent = events[1] as UserAssignedToProject
            assertEquals(testUserId, assignedEvent.userId)
            assertEquals(ProjectRole.PROJECT_ADMIN, assignedEvent.role)
            assertEquals(testUserId, assignedEvent.assignedBy)

            // And: Creator is in members list
            assertTrue(aggregate.hasMember(testUserId))
            assertTrue(aggregate.isAdmin(testUserId))
        }
    }

    @Nested
    @DisplayName("Project Update (AC-4.1.4)")
    inner class ProjectUpdate {

        @Test
        fun `updates project name and description`() {
            // Given: An active project
            val aggregate = createTestProject()
            aggregate.clearUncommittedEvents()
            val newName = ProjectName.of("Updated Name")
            val newDescription = "Updated description"
            val metadata = createTestMetadata()

            // When: Update project
            aggregate.update(
                name = newName,
                description = newDescription,
                metadata = metadata
            )

            // Then: Project is updated
            assertEquals(newName.value, aggregate.name.value)
            assertEquals(newDescription, aggregate.description)

            // And: ProjectUpdated event is emitted
            val events = aggregate.uncommittedEvents
            assertEquals(1, events.size)
            assertTrue(events[0] is ProjectUpdated)
        }

        @Test
        fun `rejects update on archived project (AC-4-1-4)`() {
            // Given: An archived project
            val aggregate = createTestProject()
            aggregate.archive(createTestMetadata())
            aggregate.clearUncommittedEvents()

            // When/Then: Update throws exception
            val exception = assertThrows<IllegalStateException> {
                aggregate.update(
                    name = ProjectName.of("New Name"),
                    description = "New description",
                    metadata = createTestMetadata()
                )
            }
            assertTrue(exception.message!!.contains("archived"))
        }
    }

    @Nested
    @DisplayName("Reconstitution")
    inner class Reconstitution {

        @Test
        fun `reconstitutes project from events`() {
            // Given: A project with events
            val original = createTestProject()
            original.update(
                name = ProjectName.of("Modified Name"),
                description = "Modified description",
                metadata = createTestMetadata()
            )
            val events = original.uncommittedEvents

            // When: Reconstitute from events
            val reconstituted = ProjectAggregate.reconstitute(original.id, events)

            // Then: State matches
            assertEquals(original.id, reconstituted.id)
            assertEquals(original.name.value, reconstituted.name.value)
            assertEquals(original.description, reconstituted.description)
            assertEquals(original.status, reconstituted.status)
            assertEquals(original.createdBy, reconstituted.createdBy)

            // And: No uncommitted events (all were replayed)
            assertTrue(reconstituted.uncommittedEvents.isEmpty())
        }

        @Test
        fun `reconstituted project has correct version`() {
            // Given: A project with 3 events
            val original = createTestProject() // 2 events: Created + UserAssigned
            original.archive(createTestMetadata()) // 3rd event
            val events = original.uncommittedEvents

            // When: Reconstitute
            val reconstituted = ProjectAggregate.reconstitute(original.id, events)

            // Then: Version equals number of events
            assertEquals(3L, reconstituted.version)
        }
    }

    private fun createTestProject(): ProjectAggregate {
        return ProjectAggregate.create(
            name = ProjectName.of("Test Project"),
            description = "Test description",
            metadata = createTestMetadata()
        )
    }
}
