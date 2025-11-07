package com.axians.eaf.framework.web

import org.springframework.modulith.ApplicationModule

/**
 * Spring Modulith metadata for framework/web module (Story 2.9).
 *
 * Defines module boundaries and allowed dependencies following hexagonal architecture.
 *
 * **Module Purpose:**
 * REST API foundation layer providing:
 * - RFC 7807 error handling (@RestControllerAdvice)
 * - Jackson JSON configuration (ISO-8601 dates, Kotlin support)
 * - CORS configuration (development + production)
 * - Cursor-based pagination utilities
 *
 * **Allowed Dependencies:**
 * - core: Domain primitives, base exceptions
 * - security: Authentication/authorization (JWT validation, tenant context - Story 4.1+)
 * - shared.api: Command/query/event contracts
 *
 * **Dependency Rules:**
 * - ✅ Web → Core (domain exceptions, primitives)
 * - ✅ Web → Security (JWT validation, tenant context)
 * - ✅ Web → Shared API (command/query contracts)
 * - ❌ Web → CQRS (no direct Axon dependencies - use injected services)
 * - ❌ Web → Persistence (no direct jOOQ/DB access)
 *
 * **Verification:**
 * Spring Modulith enforces these rules at compile-time via @ApplicationModule.
 * Run `./gradlew :framework:web:test` to validate module boundaries.
 *
 * **References:**
 * - Architecture: Section 3.1 (Module Structure)
 * - Coding Standards: Spring Modulith Configuration
 *
 * @see org.springframework.modulith.ApplicationModule
 */
@ApplicationModule(
    displayName = "EAF Web Framework - REST API Foundation",
    allowedDependencies = ["core", "security", "shared.api"],
)
class WebModule
