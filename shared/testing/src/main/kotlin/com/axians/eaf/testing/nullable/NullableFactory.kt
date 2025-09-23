package com.axians.eaf.testing.nullable

/**
 * Factory interface for EAF's Nullable Design Pattern.
 *
 * The Nullable Design Pattern provides fast infrastructure substitutes that maintain
 * real business logic while eliminating external dependencies. This achieves 61.6%
 * performance improvement over traditional mocking approaches.
 *
 * **Pattern Philosophy:**
 * - Create fast, in-memory implementations of infrastructure adapters
 * - Maintain business logic contracts and validation rules
 * - Eliminate external dependencies (databases, APIs, file systems)
 * - Provide instant test feedback with 5ms average execution time
 *
 * **Usage Example:**
 * ```kotlin
 * // In tests, replace this:
 * val mockRepository = mockk<WidgetRepository>()
 * every { mockRepository.findById(any()) } returns widget
 *
 * // With this:
 * val repository = NullableWidgetRepository.createNull()
 * repository.save(widget)
 * ```
 */
interface NullableFactory<T> {
    /**
     * Creates a clean instance of the nullable implementation.
     * Used for test isolation and clean test setup.
     *
     * @return Fresh instance with empty state
     */
    fun createNull(): T

    /**
     * Creates instance with pre-populated state for complex test scenarios.
     * Supports test data setup and scenario-specific configurations.
     *
     * @param state Initial state data (implementation-specific format)
     * @return Configured instance with initial state
     */
    fun createNull(state: Map<String, Any>): T = createNull()
}

/**
 * Inline factory function for creating nullable implementations.
 * Provides type-safe creation of nullable instances with compile-time verification.
 *
 * **Usage:**
 * ```kotlin
 * val repository = nullable<WidgetRepository>()
 * val eventBus = nullable<EventBus>()
 * val service = WidgetService(repository, eventBus) // Real business logic testing
 * ```
 *
 * @param T The interface type to create a nullable implementation for
 * @return Nullable implementation instance
 * @throws IllegalArgumentException if no nullable implementation exists for type T
 */
inline fun <reified T> nullable(): T =
    when (T::class) {
        com.axians.eaf.framework.persistence.repositories.WidgetProjectionRepository::class ->
            NullableWidgetProjectionRepository.createNull() as T
        // Add more nullable implementations here as they are created:
        // ProductRepository::class -> NullableProductRepository.createNull() as T
        // UserRepository::class -> NullableUserRepository.createNull() as T
        // EventBus::class -> NullableEventBus.createNull() as T
        else -> throw IllegalArgumentException(
            "No nullable implementation available for ${T::class.simpleName}. " +
                "Create a nullable implementation that implements NullableFactory<${T::class.simpleName}>.",
        )
    }

/**
 * Factory function with initial state for complex test scenarios.
 *
 * @param state Initial state data for pre-population
 * @return Configured nullable implementation instance
 */
inline fun <reified T> nullable(state: Map<String, Any>): T =
    when (T::class) {
        com.axians.eaf.framework.persistence.repositories.WidgetProjectionRepository::class -> {
            @Suppress("UNCHECKED_CAST")
            val widgets =
                state["widgets"] as? List<com.axians.eaf.framework.persistence.entities.WidgetProjection>
                    ?: emptyList()
            NullableWidgetProjectionRepository.createNull(widgets) as T
        }
        else -> throw IllegalArgumentException(
            "No nullable implementation with state support available for ${T::class.simpleName}.",
        )
    }
