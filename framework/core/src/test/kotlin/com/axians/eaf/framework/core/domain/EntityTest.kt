package com.axians.eaf.framework.core.domain

import com.axians.eaf.framework.core.common.types.Identifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for Entity base class - identity-based domain objects.
 *
 * Validates the Entity abstraction in DDD, ensuring entities are compared by identity (ID)
 * rather than attribute values, unlike value objects which use structural equality.
 *
 * **Test Coverage:**
 * - Identity-based equality (entities with same ID are equal regardless of attributes)
 * - HashCode consistency based on ID (required for collections)
 * - Collections behavior (HashSet deduplication, HashMap key usage)
 * - Different instances with same ID are equal (entity identity principle)
 * - Entities with different IDs are not equal even if attributes match
 *
 * **DDD Patterns:**
 * - Entity identity (ID-based equality, not structural)
 * - Entities vs Value Objects distinction
 * - Lifecycle independence (mutable entities with stable identity)
 * - Collection safety (consistent hashCode/equals)
 *
 * @see Entity Primary class under test
 * @see Identifier Entity identity type
 * @since JUnit 6 Migration (2025-11-20)
 * @author EAF Testing Framework
 */
class EntityTest {

    // Test entity and identifier implementations
    data class TestId(
        override val value: String,
    ) : Identifier

    // Regular class (not data class) to preserve Entity base class equals/hashCode
    class TestEntity(
        override val id: TestId,
        val name: String,
        val value: Int,
    ) : Entity<TestId>(id)

    @Test
    fun `should be equal when same ID (different instances)`() {
        val entity1 = TestEntity(id = TestId("id-123"), name = "Entity1", value = 100)
        val entity2 = TestEntity(id = TestId("id-123"), name = "Entity2", value = 200)

        assertThat(entity1).isEqualTo(entity2) // Same ID, different attributes
    }

    @Test
    fun `should not be equal when different IDs`() {
        val entity1 = TestEntity(id = TestId("id-123"), name = "Entity", value = 100)
        val entity2 = TestEntity(id = TestId("id-456"), name = "Entity", value = 100)

        assertThat(entity1).isNotEqualTo(entity2)
    }

    @Test
    fun `should be equal to itself (reflexive)`() {
        val entity = TestEntity(id = TestId("id-123"), name = "Entity", value = 100)

        assertThat(entity).isEqualTo(entity)
    }

    @Test
    fun `should have consistent hashCode for same ID`() {
        val entity1 = TestEntity(id = TestId("id-123"), name = "Entity1", value = 100)
        val entity2 = TestEntity(id = TestId("id-123"), name = "Entity2", value = 200)

        assertThat(entity1.hashCode()).isEqualTo(entity2.hashCode())
    }

    @Test
    fun `should work correctly in HashSet (no duplicates with same ID)`() {
        val entity1 = TestEntity(id = TestId("id-123"), name = "Entity1", value = 100)
        val entity2 = TestEntity(id = TestId("id-123"), name = "Entity2", value = 200)
        val entity3 = TestEntity(id = TestId("id-456"), name = "Entity3", value = 300)

        val set = hashSetOf(entity1, entity2, entity3)
        assertThat(set).hasSize(2) // entity1 and entity2 are duplicates
    }

    @Test
    fun `should work correctly in HashMap as key`() {
        val entity1 = TestEntity(id = TestId("id-123"), name = "Entity1", value = 100)
        val entity2 = TestEntity(id = TestId("id-123"), name = "Entity2", value = 200)

        val map = hashMapOf<TestEntity, String>()
        map[entity1] = "first"
        map[entity2] = "second"

        assertThat(map).hasSize(1) // Same key (same ID)
        assertThat(map[entity1]).isEqualTo("second") // Overwritten value
    }

    @Test
    fun `should maintain identity equality despite attribute changes`() {
        val id = TestId("id-123")
        val entity1 = TestEntity(id = id, name = "Original", value = 100)
        val entity2 = TestEntity(id = id, name = "Modified", value = 999)

        assertThat(entity1).isEqualTo(entity2) // Same ID, attributes don't matter for equality
    }

    @Test
    fun `should support different entity types with same ID structure`() {
        class AnotherEntity(
            override val id: TestId,
            val description: String,
        ) : Entity<TestId>(id)

        val entity1 = TestEntity(id = TestId("id-123"), name = "Test", value = 100)
        val entity2 = AnotherEntity(id = TestId("id-123"), description = "Description")

        // Different types, not equal even with same ID type and value
        assertThat(entity1).isNotEqualTo(entity2)
    }
}
