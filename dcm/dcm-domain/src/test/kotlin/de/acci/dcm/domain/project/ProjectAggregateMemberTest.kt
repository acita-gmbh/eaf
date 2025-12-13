package de.acci.dcm.domain.project

import de.acci.dcm.domain.project.events.UserAssignedToProject
import de.acci.dcm.domain.project.events.UserRemovedFromProject
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.EventMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

@DisplayName("ProjectAggregate Member Management")
class ProjectAggregateMemberTest {

    private val testTenantId = TenantId(UUID.randomUUID())
    private val creatorUserId = UserId(UUID.randomUUID())

    private fun createTestMetadata(userId: UserId = creatorUserId): EventMetadata = EventMetadata(
        tenantId = testTenantId,
        userId = userId,
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
    @DisplayName("User Assignment (AC-4.1.5)")
    inner class UserAssignment {

        @Test
        fun `assigns user as MEMBER`() {
            // Given: An active project
            val aggregate = createTestProject()
            aggregate.clearUncommittedEvents()
            val newUserId = UserId(UUID.randomUUID())
            val metadata = createTestMetadata()

            // When: Assign user as MEMBER
            aggregate.assignUser(
                userId = newUserId,
                role = ProjectRole.MEMBER,
                metadata = metadata
            )

            // Then: User is a member
            assertTrue(aggregate.hasMember(newUserId))
            assertFalse(aggregate.isAdmin(newUserId))

            // And: UserAssignedToProject event is emitted
            val events = aggregate.uncommittedEvents
            assertEquals(1, events.size)
            val assignedEvent = events[0] as UserAssignedToProject
            assertEquals(newUserId, assignedEvent.userId)
            assertEquals(ProjectRole.MEMBER, assignedEvent.role)
            assertEquals(creatorUserId, assignedEvent.assignedBy)
        }

        @Test
        fun `assigns user as PROJECT_ADMIN`() {
            // Given: An active project
            val aggregate = createTestProject()
            aggregate.clearUncommittedEvents()
            val newAdminId = UserId(UUID.randomUUID())

            // When: Assign user as PROJECT_ADMIN
            aggregate.assignUser(
                userId = newAdminId,
                role = ProjectRole.PROJECT_ADMIN,
                metadata = createTestMetadata()
            )

            // Then: User is an admin
            assertTrue(aggregate.hasMember(newAdminId))
            assertTrue(aggregate.isAdmin(newAdminId))
        }

        @Test
        fun `assigning same user is idempotent`() {
            // Given: A project with a member
            val aggregate = createTestProject()
            val userId = UserId(UUID.randomUUID())
            aggregate.assignUser(userId, ProjectRole.MEMBER, createTestMetadata())
            aggregate.clearUncommittedEvents()

            // When: Assign same user again
            aggregate.assignUser(userId, ProjectRole.MEMBER, createTestMetadata())

            // Then: No new events (idempotent)
            assertTrue(aggregate.uncommittedEvents.isEmpty())
        }

        @Test
        fun `can upgrade member to admin`() {
            // Given: A project with a regular member
            val aggregate = createTestProject()
            val userId = UserId(UUID.randomUUID())
            aggregate.assignUser(userId, ProjectRole.MEMBER, createTestMetadata())
            aggregate.clearUncommittedEvents()

            // When: Promote to admin
            aggregate.assignUser(userId, ProjectRole.PROJECT_ADMIN, createTestMetadata())

            // Then: User is now admin and event is emitted
            assertTrue(aggregate.isAdmin(userId))
            assertEquals(1, aggregate.uncommittedEvents.size)
        }
    }

    @Nested
    @DisplayName("User Removal (AC-4.1.6, AC-4.1.7)")
    inner class UserRemoval {

        @Test
        fun `removes member from project`() {
            // Given: A project with a member
            val aggregate = createTestProject()
            val memberId = UserId(UUID.randomUUID())
            aggregate.assignUser(memberId, ProjectRole.MEMBER, createTestMetadata())
            aggregate.clearUncommittedEvents()

            // When: Remove member
            aggregate.removeUser(memberId, createTestMetadata())

            // Then: User is no longer a member
            assertFalse(aggregate.hasMember(memberId))

            // And: UserRemovedFromProject event is emitted
            val events = aggregate.uncommittedEvents
            assertEquals(1, events.size)
            val removedEvent = events[0] as UserRemovedFromProject
            assertEquals(memberId, removedEvent.userId)
            assertEquals(creatorUserId, removedEvent.removedBy)
        }

        @Test
        fun `cannot remove project creator (AC-4-1-7)`() {
            // Given: A project (creator is auto-assigned)
            val aggregate = createTestProject()

            // When/Then: Attempting to remove creator throws exception
            val exception = assertThrows<IllegalArgumentException> {
                aggregate.removeUser(creatorUserId, createTestMetadata())
            }
            assertTrue(exception.message!!.contains("creator"))
        }

        @Test
        fun `removing non-member is idempotent`() {
            // Given: A project
            val aggregate = createTestProject()
            aggregate.clearUncommittedEvents()
            val nonMemberId = UserId(UUID.randomUUID())

            // When: Remove user who is not a member
            aggregate.removeUser(nonMemberId, createTestMetadata())

            // Then: No exception, no events (idempotent)
            assertTrue(aggregate.uncommittedEvents.isEmpty())
        }
    }

    @Nested
    @DisplayName("Member Queries")
    inner class MemberQueries {

        @Test
        fun `getMember returns member if exists`() {
            // Given: A project with members
            val aggregate = createTestProject()
            val memberId = UserId(UUID.randomUUID())
            aggregate.assignUser(memberId, ProjectRole.MEMBER, createTestMetadata())

            // When: Get member
            val member = aggregate.getMember(memberId)

            // Then: Member is returned with correct data
            assertTrue(member != null)
            assertEquals(memberId, member!!.userId)
            assertEquals(ProjectRole.MEMBER, member.role)
        }

        @Test
        fun `getMember returns null for non-member`() {
            // Given: A project
            val aggregate = createTestProject()
            val nonMemberId = UserId(UUID.randomUUID())

            // When: Get non-existent member
            val member = aggregate.getMember(nonMemberId)

            // Then: Null is returned
            assertEquals(null, member)
        }

        @Test
        fun `getMembers returns all members`() {
            // Given: A project with multiple members
            val aggregate = createTestProject()
            val memberId1 = UserId(UUID.randomUUID())
            val memberId2 = UserId(UUID.randomUUID())
            aggregate.assignUser(memberId1, ProjectRole.MEMBER, createTestMetadata())
            aggregate.assignUser(memberId2, ProjectRole.PROJECT_ADMIN, createTestMetadata())

            // When: Get all members
            val members = aggregate.getMembers()

            // Then: All members including creator are returned
            assertEquals(3, members.size) // creator + 2 new members
        }
    }
}
