# Epic 2: Story Context XML Batch Generation Guide

## Status

✅ **Completed**: Story 2.1 - Axon Core Configuration (full context XML)
⏳ **Remaining**: Stories 2.2 through 2.13 (12 stories)

## Context XML Template Structure

Each Story Context XML follows this proven pattern (from Story 2.1):

### 1. XML Header & Metadata (15-20 lines)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<story-context id="{story-key}" v="1.0">
  <metadata>
    <epicId>2</epicId>
    <storyId>{N}</storyId>
    <title>{Story Title}</title>
    <status>drafted</status>
    <generatedAt>2025-11-04</generatedAt>
    <generator>BMAD Story Context Workflow</generator>
    <sourceStoryPath>docs/stories/epic-2/story-2.{N}-{name}.md</sourceStoryPath>
  </metadata>
```

### 2. Story Section (20-30 lines)
- Extract from story markdown files: `## User Story` section
- Tasks from `## Implementation Checklist`

### 3. Acceptance Criteria (10-15 lines)
- Extract from story markdown: `## Acceptance Criteria` section
- 7-9 criteria per story (verified count from tech-spec-epic-2.md)

### 4. Artifacts Section (100-150 lines)
**Documentation References (4-6 per story):**
- docs/PRD.md (FR003, FR011, FR014, FR015 sections)
- docs/architecture.md (CQRS/ES, PostgreSQL, jOOQ, Testing sections)
- docs/tech-spec-epic-2.md (Epic-specific technical details)
- docs/architecture/coding-standards.md (Zero-tolerance policies)
- docs/architecture/test-strategy.md (Constitutional TDD)

**Code Artifacts (3-5 per story):**
- Existing modules from Epic 1 (framework/core)
- Story-specific modules (framework/cqrs, framework/persistence, products/widget-demo)

**Dependencies (4-6 per story):**
From gradle/libs.versions.toml:
- axon-spring-boot-starter: 4.12.1
- kotest-framework-engine-jvm: 6.0.4
- testcontainers-postgresql: 1.21.3
- jooq-core: 3.20.8
- spring-boot-starter-web: 3.5.7
- spring-modulith-starter-core: 1.4.4

### 5. Constraints (50-70 lines)
Standard constraints apply to ALL Epic 2 stories:
```xml
<constraints>
  <constraint priority="critical">NO wildcard imports - Every import must be explicit</constraint>
  <constraint priority="critical">NO generic exceptions - Always use specific exception types</constraint>
  <constraint priority="critical">Use Kotest ONLY - JUnit is explicitly forbidden</constraint>
  <constraint priority="critical">Version Catalog REQUIRED - All versions in gradle/libs.versions.toml</constraint>
  <constraint priority="high">Constitutional TDD - Write tests FIRST (Red-Green-Refactor cycle)</constraint>
  <constraint priority="high">Test execution must be &lt;10 seconds for unit tests, &lt;3min for integration</constraint>
  <constraint priority="medium">Use @Autowired field injection + init block for @SpringBootTest</constraint>
  <constraint priority="medium">Plugin order: id("eaf.testing") BEFORE id("eaf.spring-boot")</constraint>
</constraints>
```

### 6. Interfaces (40-60 lines)
Story-specific interfaces and patterns from tech-spec-epic-2.md:
- **2.1**: CommandGateway, QueryGateway
- **2.2**: JdbcEventStorageEngine, DataSource
- **2.3**: Partitioning SQL patterns
- **2.4**: SnapshotTriggerDefinition
- **2.5**: @Aggregate, @CommandHandler, @EventSourcingHandler
- **2.6**: DSLContext, jOOQ code generation
- **2.7**: @EventHandler, TrackingEventProcessor
- **2.8**: @QueryHandler, Cursor pagination
- **2.9**: RFC 7807 ProblemDetail
- **2.10**: @RestController, CommandGateway/QueryGateway usage
- **2.11**: Testcontainers pattern
- **2.12**: @Tag, @Operation, @ApiResponses
- **2.13**: JMeter/Gatling load testing

### 7. Tests Section (60-80 lines)
**Standards (same for all stories):**
```xml
<tests>
  <standards>
    <framework>Kotest 6.0.4 (FunSpec, BehaviorSpec)</framework>
    <approach>Constitutional TDD - Red-Green-Refactor</approach>
    <coverage>85%+ line coverage target (Kover)</coverage>
    <performance>Unit tests &lt;10s, Integration &lt;3min</performance>
  </standards>
```

**Test Ideas (10-15 per story):**
- Map directly to each acceptance criterion
- Include positive and negative test cases
- Specify test type (unit, integration, performance)

## Story-Specific Details

### Story 2.2: PostgreSQL Event Store
- **Focus**: JdbcEventStorageEngine, Flyway migrations, Testcontainers
- **Key Artifacts**: V001__event_store_schema.sql
- **Tests**: Event persistence, retrieval, Testcontainers startup

### Story 2.3: Event Store Partitioning
- **Focus**: Monthly partitioning, BRIN indexes, performance
- **Key Artifacts**: V002__partitioning_setup.sql, V003__brin_indexes.sql
- **Tests**: 100K+ events performance, partitioning validation

### Story 2.4: Snapshot Support
- **Focus**: SnapshotTriggerDefinition, performance optimization
- **Key Artifacts**: SnapshotTriggerDefinition configuration
- **Tests**: 250+ events, snapshot creation, >10x performance improvement

### Story 2.5: Widget Aggregate
- **Focus**: CQRS aggregate pattern, Axon Test Fixtures
- **Key Artifacts**: Widget.kt, Commands, Events
- **Tests**: Axon Test Fixtures for all command scenarios

### Story 2.6: jOOQ Configuration
- **Focus**: Type-safe SQL, code generation, DSLContext
- **Key Artifacts**: JooqConfiguration.kt, V100__widget_projections.sql
- **Tests**: Code generation, type-safe queries

### Story 2.7: Widget Projection Handler
- **Focus**: TrackingEventProcessor, projection updates, &lt;10s lag
- **Key Artifacts**: WidgetProjectionEventHandler.kt
- **Tests**: Command → Event → Projection flow

### Story 2.8: Widget Query Handler
- **Focus**: @QueryHandler, cursor pagination, Nullable Pattern
- **Key Artifacts**: WidgetQueryHandler.kt, FindWidgetQuery, ListWidgetsQuery
- **Tests**: Nullable Pattern unit tests, integration tests

### Story 2.9: REST API Foundation
- **Focus**: RFC 7807 ProblemDetail, CursorPaginationSupport
- **Key Artifacts**: framework/web module, ProblemDetailExceptionHandler.kt
- **Tests**: Error format validation, HTTP status codes

### Story 2.10: Widget REST Controller
- **Focus**: @RestController, CRUD endpoints, OpenAPI annotations
- **Key Artifacts**: WidgetController.kt, Request/Response DTOs
- **Tests**: Full CRUD flow integration tests

### Story 2.11: End-to-End Integration Test
- **Focus**: Complete CQRS flow validation, performance measurement
- **Key Artifacts**: WalkingSkeletonIntegrationTest.kt
- **Tests**: POST → Command → Event → Projection → GET flow

### Story 2.12: OpenAPI/Swagger
- **Focus**: Springdoc OpenAPI, Swagger UI, API documentation
- **Key Artifacts**: OpenApiConfiguration.kt, @Tag/@Operation annotations
- **Tests**: Swagger UI accessibility, JSON spec validation

### Story 2.13: Performance Baseline
- **Focus**: JMeter/Gatling load testing, baseline documentation
- **Key Artifacts**: Performance test suite, Prometheus metrics
- **Tests**: 100 users, 1000 req/s, &lt;200ms p95, &lt;10s lag

## Rapid Generation Approach

For each story (2.2-2.13):

1. **Read Story File**: Extract User Story, ACs, Tasks, Technical Notes
2. **Extract from Tech-Spec**: Get story-specific patterns and code examples
3. **Map Dependencies**: Identify relevant libs.versions.toml entries
4. **Generate Test Ideas**: Create 10-15 test ideas from ACs
5. **Apply Template**: Use Story 2.1 as structural reference
6. **Validate**: Ensure all 7 sections present and complete

## Quality Checklist

Each context XML must have:
- [ ] Valid XML structure
- [ ] All 7 sections present (metadata, story, ACs, artifacts, constraints, interfaces, tests)
- [ ] 4-6 documentation references with actual snippets
- [ ] 3-5 code artifacts with paths
- [ ] 4-6 dependencies with versions
- [ ] 8-10 constraints
- [ ] 10-15 test ideas mapped to ACs
- [ ] Project-relative paths (NOT absolute)
- [ ] 300-400 lines total length

## Next Steps

Use this guide to systematically generate the remaining 12 context XML files, following the proven pattern from Story 2.1.
