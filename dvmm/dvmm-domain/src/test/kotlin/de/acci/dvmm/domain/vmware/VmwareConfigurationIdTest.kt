package de.acci.dvmm.domain.vmware

import de.acci.eaf.core.error.InvalidIdentifierFormatException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

@DisplayName("VmwareConfigurationId")
class VmwareConfigurationIdTest {

    @Nested
    @DisplayName("generate()")
    inner class GenerateTests {

        @Test
        @DisplayName("should generate unique IDs")
        fun `should generate unique IDs`() {
            // When
            val id1 = VmwareConfigurationId.generate()
            val id2 = VmwareConfigurationId.generate()

            // Then
            assertNotEquals(id1, id2)
        }

        @Test
        @DisplayName("should generate valid UUIDs")
        fun `should generate valid UUIDs`() {
            // When
            val id = VmwareConfigurationId.generate()

            // Then
            // UUID.fromString will throw if invalid
            val parsed = UUID.fromString(id.value.toString())
            assertEquals(id.value, parsed)
        }
    }

    @Nested
    @DisplayName("fromString()")
    inner class FromStringTests {

        @Test
        @DisplayName("should parse valid UUID string")
        fun `should parse valid UUID string`() {
            // Given
            val uuidString = "550e8400-e29b-41d4-a716-446655440000"

            // When
            val id = VmwareConfigurationId.fromString(uuidString)

            // Then
            assertEquals(UUID.fromString(uuidString), id.value)
        }

        @Test
        @DisplayName("should throw InvalidIdentifierFormatException for invalid string")
        fun `should throw InvalidIdentifierFormatException for invalid string`() {
            // Given
            val invalidString = "not-a-uuid"

            // When/Then
            val exception = assertThrows<InvalidIdentifierFormatException> {
                VmwareConfigurationId.fromString(invalidString)
            }

            assertEquals("VmwareConfigurationId", exception.identifierType)
            assertEquals(invalidString, exception.raw)
            assertTrue(exception.cause is IllegalArgumentException)
        }

        @Test
        @DisplayName("should throw InvalidIdentifierFormatException for empty string")
        fun `should throw InvalidIdentifierFormatException for empty string`() {
            // When/Then
            val exception = assertThrows<InvalidIdentifierFormatException> {
                VmwareConfigurationId.fromString("")
            }

            assertEquals("VmwareConfigurationId", exception.identifierType)
            assertEquals("", exception.raw)
        }

        @Test
        @DisplayName("should throw InvalidIdentifierFormatException for malformed UUID")
        fun `should throw InvalidIdentifierFormatException for malformed UUID`() {
            // Given - UUID with wrong format (missing hyphens)
            val malformedUuid = "550e8400e29b41d4a716446655440000"

            // When/Then
            assertThrows<InvalidIdentifierFormatException> {
                VmwareConfigurationId.fromString(malformedUuid)
            }
        }
    }

    @Nested
    @DisplayName("value class behavior")
    inner class ValueClassTests {

        @Test
        @DisplayName("should equal another ID with same UUID")
        fun `should equal another ID with same UUID`() {
            // Given
            val uuid = UUID.randomUUID()
            val id1 = VmwareConfigurationId(uuid)
            val id2 = VmwareConfigurationId(uuid)

            // Then
            assertEquals(id1, id2)
            assertEquals(id1.hashCode(), id2.hashCode())
        }

        @Test
        @DisplayName("should not equal ID with different UUID")
        fun `should not equal ID with different UUID`() {
            // Given
            val id1 = VmwareConfigurationId(UUID.randomUUID())
            val id2 = VmwareConfigurationId(UUID.randomUUID())

            // Then
            assertNotEquals(id1, id2)
        }

        @Test
        @DisplayName("should have readable toString")
        fun `should have readable toString`() {
            // Given
            val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            val id = VmwareConfigurationId(uuid)

            // Then
            assertTrue(id.toString().contains("550e8400-e29b-41d4-a716-446655440000"))
        }
    }
}
