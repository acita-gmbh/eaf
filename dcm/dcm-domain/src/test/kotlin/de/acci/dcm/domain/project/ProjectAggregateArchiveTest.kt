package de.acci.dcm.domain.project

import de.acci.dcm.domain.project.events.ProjectArchived
import de.acci.dcm.domain.project.events.ProjectUnarchived
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.EventMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

@DisplayName("ProjectAggregate Archive/Unarchive")
class ProjectAggregateArchiveTest {

    private val testTenantId = TenantId(UUID.randomUUID())
    private val testUserId = UserId(UUID.randomUUID())

    private fun createTestMetadata(): EventMetadata = EventMetadata(
        tenantId = testTenantId,
        userId = testUserId,
        correlationId = CorrelationId(UUID.randomUUID()),
        timestamp = Instant.now()
    )

    private fun createTestProject(): ProjectAggregate {
        return ProjectAggregate.create(
            name = ProjectName.of("Test Project"),
            description = "Test description",
            metadata = createTestMetadata()
        )
    }

    @Nested
    @DisplayName("Project Archive (AC-4.1.8)")
    inner class ProjectArchive {

        @Test
        fun `archives active project`() {
            // Given: An active project
            val aggregate = createTestProject()
            aggregate.clearUncommittedEvents()
            val metadata = createTestMetadata()

            // When: Archive
            aggregate.archive(metadata)

            // Then: Status is ARCHIVED
            assertEquals(ProjectStatus.ARCHIVED, aggregate.status)

            // And: ProjectArchived event is emitted
            val events = aggregate.uncommittedEvents
            assertEquals(1, events.size)
            assertTrue(events[0] is ProjectArchived)
        }

        @Test
        fun `archiving already archived project is idempotent`() {
            // Given: An archived project
            val aggregate = createTestProject()
            aggregate.archive(createTestMetadata())
            aggregate.clearUncommittedEvents()

            // When: Archive again
            aggregate.archive(createTestMetadata())

            // Then: No exception, no new events
            assertTrue(aggregate.uncommittedEvents.isEmpty())
            assertEquals(ProjectStatus.ARCHIVED, aggregate.status)
        }
    }

    @Nested
    @DisplayName("Project Unarchive (AC-4.1.9)")
    inner class ProjectUnarchive {

        @Test
        fun `unarchives archived project`() {
            // Given: An archived project
            val aggregate = createTestProject()
            aggregate.archive(createTestMetadata())
            aggregate.clearUncommittedEvents()
            val metadata = createTestMetadata()

            // When: Unarchive
            aggregate.unarchive(metadata)

            // Then: Status is ACTIVE
            assertEquals(ProjectStatus.ACTIVE, aggregate.status)

            // And: ProjectUnarchived event is emitted
            val events = aggregate.uncommittedEvents
            assertEquals(1, events.size)
            assertTrue(events[0] is ProjectUnarchived)
        }

        @Test
        fun `unarchiving active project is idempotent`() {
            // Given: An active project
            val aggregate = createTestProject()
            aggregate.clearUncommittedEvents()

            // When: Unarchive (already active)
            aggregate.unarchive(createTestMetadata())

            // Then: No exception, no new events
            assertTrue(aggregate.uncommittedEvents.isEmpty())
            assertEquals(ProjectStatus.ACTIVE, aggregate.status)
        }
    }

    @Nested
    @DisplayName("Archived Project Restrictions")
    inner class ArchivedProjectRestrictions {

        @Test
        fun `cannot assign user to archived project`() {
            // Given: An archived project
            val aggregate = createTestProject()
            aggregate.archive(createTestMetadata())
            val newUserId = UserId(UUID.randomUUID())

            // When/Then: Assign throws exception
            val exception = assertThrows<IllegalStateException> {
                aggregate.assignUser(
                    userId = newUserId,
                    role = ProjectRole.MEMBER,
                    metadata = createTestMetadata()
                )
            }
            val message = requireNotNull(exception.message) { "Expected exception message" }
            assertTrue(message.contains("archived"))
        }

        @Test
        fun `cannot remove user from archived project`() {
            // Given: An archived project with members
            val aggregate = createTestProject()
            val memberId = UserId(UUID.randomUUID())
            aggregate.assignUser(memberId, ProjectRole.MEMBER, createTestMetadata())
            aggregate.archive(createTestMetadata())

            // When/Then: Remove throws exception
            val exception = assertThrows<IllegalStateException> {
                aggregate.removeUser(memberId, createTestMetadata())
            }
            val message = requireNotNull(exception.message) { "Expected exception message" }
            assertTrue(message.contains("archived"))
        }
    }
}
