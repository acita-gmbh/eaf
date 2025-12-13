package de.acci.dcm.infrastructure.eventsourcing

import de.acci.dcm.domain.project.ProjectId
import de.acci.dcm.domain.project.ProjectName
import de.acci.dcm.domain.project.ProjectRole
import de.acci.dcm.domain.project.events.ProjectArchived
import de.acci.dcm.domain.project.events.ProjectCreated
import de.acci.dcm.domain.project.events.ProjectUnarchived
import de.acci.dcm.domain.project.events.ProjectUpdated
import de.acci.dcm.domain.project.events.UserAssignedToProject
import de.acci.dcm.domain.project.events.UserRemovedFromProject
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.EventMetadata
import de.acci.eaf.eventsourcing.EventStoreObjectMapper
import de.acci.eaf.eventsourcing.StoredEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

@DisplayName("JacksonProjectEventDeserializer")
class JacksonProjectEventDeserializerTest {

    private val objectMapper = EventStoreObjectMapper.create()
    private val deserializer = JacksonProjectEventDeserializer(objectMapper)

    private val testTenantId = TenantId(UUID.randomUUID())
    private val testUserId = UserId(UUID.randomUUID())
    private val testCorrelationId = CorrelationId(UUID.randomUUID())
    private val testProjectId = ProjectId.generate()

    private fun createTestMetadata(): EventMetadata = EventMetadata(
        tenantId = testTenantId,
        userId = testUserId,
        correlationId = testCorrelationId,
        timestamp = Instant.now()
    )

    @Nested
    @DisplayName("ProjectCreated Deserialization")
    inner class ProjectCreatedDeserialization {

        @Test
        fun `deserializes ProjectCreated event correctly`() {
            // Given: A stored ProjectCreated event
            val projectName = ProjectName.of("Alpha Project")
            val event = ProjectCreated(
                aggregateId = testProjectId,
                name = projectName,
                description = "Test project description",
                metadata = createTestMetadata()
            )
            val payload = objectMapper.writeValueAsString(event)
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = testProjectId.value,
                aggregateType = "Project",
                eventType = "ProjectCreated",
                payload = payload,
                metadata = createTestMetadata(),
                version = 1,
                createdAt = Instant.now()
            )

            // When: Deserialize
            val result = deserializer.deserialize(storedEvent)

            // Then: Correct event type returned
            assertTrue(result is ProjectCreated)
            val created = result as ProjectCreated
            assertEquals(event.aggregateId.value, created.aggregateId.value)
            assertEquals(event.name.value, created.name.value)
            assertEquals(event.description, created.description)
        }

        @Test
        fun `deserializes ProjectCreated with null description`() {
            // Given: A ProjectCreated event without description
            val event = ProjectCreated(
                aggregateId = testProjectId,
                name = ProjectName.of("Beta Project"),
                description = null,
                metadata = createTestMetadata()
            )
            val payload = objectMapper.writeValueAsString(event)
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = testProjectId.value,
                aggregateType = "Project",
                eventType = "ProjectCreated",
                payload = payload,
                metadata = createTestMetadata(),
                version = 1,
                createdAt = Instant.now()
            )

            // When: Deserialize
            val result = deserializer.deserialize(storedEvent)

            // Then: Description is null
            assertTrue(result is ProjectCreated)
            val created = result as ProjectCreated
            assertEquals(null, created.description)
        }
    }

    @Nested
    @DisplayName("ProjectUpdated Deserialization")
    inner class ProjectUpdatedDeserialization {

        @Test
        fun `deserializes ProjectUpdated event correctly`() {
            // Given: A stored ProjectUpdated event
            val event = ProjectUpdated(
                aggregateId = testProjectId,
                name = ProjectName.of("Updated Project Name"),
                description = "Updated description",
                metadata = createTestMetadata()
            )
            val payload = objectMapper.writeValueAsString(event)
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = testProjectId.value,
                aggregateType = "Project",
                eventType = "ProjectUpdated",
                payload = payload,
                metadata = createTestMetadata(),
                version = 2,
                createdAt = Instant.now()
            )

            // When: Deserialize
            val result = deserializer.deserialize(storedEvent)

            // Then: Correct event type returned
            assertTrue(result is ProjectUpdated)
            val updated = result as ProjectUpdated
            assertEquals(event.aggregateId.value, updated.aggregateId.value)
            assertEquals(event.name.value, updated.name.value)
            assertEquals(event.description, updated.description)
        }
    }

    @Nested
    @DisplayName("ProjectArchived Deserialization")
    inner class ProjectArchivedDeserialization {

        @Test
        fun `deserializes ProjectArchived event correctly`() {
            // Given: A stored ProjectArchived event
            val event = ProjectArchived(
                aggregateId = testProjectId,
                metadata = createTestMetadata()
            )
            val payload = objectMapper.writeValueAsString(event)
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = testProjectId.value,
                aggregateType = "Project",
                eventType = "ProjectArchived",
                payload = payload,
                metadata = createTestMetadata(),
                version = 3,
                createdAt = Instant.now()
            )

            // When: Deserialize
            val result = deserializer.deserialize(storedEvent)

            // Then: Correct event type returned
            assertTrue(result is ProjectArchived)
            val archived = result as ProjectArchived
            assertEquals(event.aggregateId.value, archived.aggregateId.value)
        }
    }

    @Nested
    @DisplayName("ProjectUnarchived Deserialization")
    inner class ProjectUnarchivedDeserialization {

        @Test
        fun `deserializes ProjectUnarchived event correctly`() {
            // Given: A stored ProjectUnarchived event
            val event = ProjectUnarchived(
                aggregateId = testProjectId,
                metadata = createTestMetadata()
            )
            val payload = objectMapper.writeValueAsString(event)
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = testProjectId.value,
                aggregateType = "Project",
                eventType = "ProjectUnarchived",
                payload = payload,
                metadata = createTestMetadata(),
                version = 4,
                createdAt = Instant.now()
            )

            // When: Deserialize
            val result = deserializer.deserialize(storedEvent)

            // Then: Correct event type returned
            assertTrue(result is ProjectUnarchived)
            val unarchived = result as ProjectUnarchived
            assertEquals(event.aggregateId.value, unarchived.aggregateId.value)
        }
    }

    @Nested
    @DisplayName("UserAssignedToProject Deserialization")
    inner class UserAssignedToProjectDeserialization {

        @Test
        fun `deserializes UserAssignedToProject with PROJECT_ADMIN role`() {
            // Given: A stored UserAssignedToProject event
            val assignedUserId = UserId(UUID.randomUUID())
            val event = UserAssignedToProject(
                aggregateId = testProjectId,
                userId = assignedUserId,
                role = ProjectRole.PROJECT_ADMIN,
                assignedBy = testUserId,
                metadata = createTestMetadata()
            )
            val payload = objectMapper.writeValueAsString(event)
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = testProjectId.value,
                aggregateType = "Project",
                eventType = "UserAssignedToProject",
                payload = payload,
                metadata = createTestMetadata(),
                version = 2,
                createdAt = Instant.now()
            )

            // When: Deserialize
            val result = deserializer.deserialize(storedEvent)

            // Then: Correct event type returned
            assertTrue(result is UserAssignedToProject)
            val assigned = result as UserAssignedToProject
            assertEquals(event.aggregateId.value, assigned.aggregateId.value)
            assertEquals(event.userId.value, assigned.userId.value)
            assertEquals(ProjectRole.PROJECT_ADMIN, assigned.role)
            assertEquals(event.assignedBy.value, assigned.assignedBy.value)
        }

        @Test
        fun `deserializes UserAssignedToProject with MEMBER role`() {
            // Given: A member assignment
            val assignedUserId = UserId(UUID.randomUUID())
            val event = UserAssignedToProject(
                aggregateId = testProjectId,
                userId = assignedUserId,
                role = ProjectRole.MEMBER,
                assignedBy = testUserId,
                metadata = createTestMetadata()
            )
            val payload = objectMapper.writeValueAsString(event)
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = testProjectId.value,
                aggregateType = "Project",
                eventType = "UserAssignedToProject",
                payload = payload,
                metadata = createTestMetadata(),
                version = 3,
                createdAt = Instant.now()
            )

            // When: Deserialize
            val result = deserializer.deserialize(storedEvent)

            // Then: MEMBER role preserved
            assertTrue(result is UserAssignedToProject)
            val assigned = result as UserAssignedToProject
            assertEquals(ProjectRole.MEMBER, assigned.role)
        }
    }

    @Nested
    @DisplayName("UserRemovedFromProject Deserialization")
    inner class UserRemovedFromProjectDeserialization {

        @Test
        fun `deserializes UserRemovedFromProject event correctly`() {
            // Given: A stored UserRemovedFromProject event
            val removedUserId = UserId(UUID.randomUUID())
            val event = UserRemovedFromProject(
                aggregateId = testProjectId,
                userId = removedUserId,
                removedBy = testUserId,
                metadata = createTestMetadata()
            )
            val payload = objectMapper.writeValueAsString(event)
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = testProjectId.value,
                aggregateType = "Project",
                eventType = "UserRemovedFromProject",
                payload = payload,
                metadata = createTestMetadata(),
                version = 4,
                createdAt = Instant.now()
            )

            // When: Deserialize
            val result = deserializer.deserialize(storedEvent)

            // Then: Correct event type returned
            assertTrue(result is UserRemovedFromProject)
            val removed = result as UserRemovedFromProject
            assertEquals(event.aggregateId.value, removed.aggregateId.value)
            assertEquals(event.userId.value, removed.userId.value)
            assertEquals(event.removedBy.value, removed.removedBy.value)
        }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandling {

        @Test
        fun `throws exception for unknown event type`() {
            // Given: A stored event with unknown type
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = UUID.randomUUID(),
                aggregateType = "Project",
                eventType = "UnknownProjectEventType",
                payload = "{}",
                metadata = createTestMetadata(),
                version = 1,
                createdAt = Instant.now()
            )

            // When/Then: Exception thrown with helpful message
            val exception = assertThrows<IllegalArgumentException> {
                deserializer.deserialize(storedEvent)
            }
            assertTrue(exception.message!!.contains("UnknownProjectEventType"))
            assertTrue(exception.message!!.contains("JacksonProjectEventDeserializer"))
        }

        @Test
        fun `throws exception for malformed JSON payload`() {
            // Given: A stored event with invalid JSON
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = UUID.randomUUID(),
                aggregateType = "Project",
                eventType = "ProjectCreated",
                payload = "not valid json",
                metadata = createTestMetadata(),
                version = 1,
                createdAt = Instant.now()
            )

            // When/Then: Jackson throws parsing exception
            assertThrows<Exception> {
                deserializer.deserialize(storedEvent)
            }
        }
    }

    @Nested
    @DisplayName("Serialization Roundtrip")
    inner class SerializationRoundtrip {

        @Test
        fun `ProjectCreated survives serialization roundtrip`() {
            // Given: Original event
            val original = ProjectCreated(
                aggregateId = testProjectId,
                name = ProjectName.of("Roundtrip Test"),
                description = "Testing serialization roundtrip",
                metadata = createTestMetadata()
            )

            // When: Serialize then deserialize
            val json = objectMapper.writeValueAsString(original)
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = testProjectId.value,
                aggregateType = "Project",
                eventType = "ProjectCreated",
                payload = json,
                metadata = original.metadata,
                version = 1,
                createdAt = Instant.now()
            )
            val deserialized = deserializer.deserialize(storedEvent) as ProjectCreated

            // Then: All fields preserved
            assertEquals(original.aggregateId.value, deserialized.aggregateId.value)
            assertEquals(original.name.value, deserialized.name.value)
            assertEquals(original.description, deserialized.description)
            assertEquals(original.metadata.tenantId.value, deserialized.metadata.tenantId.value)
            assertEquals(original.metadata.userId.value, deserialized.metadata.userId.value)
        }

        @Test
        fun `UserAssignedToProject survives serialization roundtrip`() {
            // Given: Original event
            val assignedUser = UserId(UUID.randomUUID())
            val original = UserAssignedToProject(
                aggregateId = testProjectId,
                userId = assignedUser,
                role = ProjectRole.PROJECT_ADMIN,
                assignedBy = testUserId,
                metadata = createTestMetadata()
            )

            // When: Serialize then deserialize
            val json = objectMapper.writeValueAsString(original)
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = testProjectId.value,
                aggregateType = "Project",
                eventType = "UserAssignedToProject",
                payload = json,
                metadata = original.metadata,
                version = 2,
                createdAt = Instant.now()
            )
            val deserialized = deserializer.deserialize(storedEvent) as UserAssignedToProject

            // Then: All fields preserved including enum
            assertEquals(original.aggregateId.value, deserialized.aggregateId.value)
            assertEquals(original.userId.value, deserialized.userId.value)
            assertEquals(original.role, deserialized.role)
            assertEquals(original.assignedBy.value, deserialized.assignedBy.value)
        }
    }
}
