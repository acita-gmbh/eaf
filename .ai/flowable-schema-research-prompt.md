# Flowable BPMN Engine - PostgreSQL Dedicated Schema Configuration Research

## Research Objective

Investigate how to configure Flowable BPMN Engine 7.1.0 to create database tables in a **dedicated PostgreSQL schema** (e.g., `flowable`) instead of the default `public` schema, when using Spring Boot 3.5.6 auto-configuration with Kotlin 2.2.20.

## Current Problem

**What We Want**: Flowable tables in dedicated `flowable` schema for isolation from application data
**What We Get**: Flowable tables created in `public` schema despite configuration

**Evidence**:
```text
DEBUG: PostgreSQL schemas: [information_schema, pg_catalog, pg_toast, public]
DEBUG: All Flowable tables in PostgreSQL: [{table_schema=public, table_name=act_ru_execution}, ...]
DEBUG: Tables in 'flowable' schema: 0 tables - []
```

**Result**: 56 Flowable ACT_* tables created successfully in PostgreSQL Testcontainers, but ALL in `public` schema.

---

## Technology Stack Context

### Versions (LOCKED - Cannot Change Without Architecture Review)

```kotlin
// gradle/libs.versions.toml
kotlin = "2.2.20"              // PINNED
spring-boot = "3.5.6"          // LOCKED for Spring Modulith 1.4.3
flowable = "7.1.0"             // Current target version
java = "21"                    // LTS requirement
postgresql = "42.7.8"          // PostgreSQL JDBC driver
testcontainers = "1.21.3"      // Integration testing
```

### Flowable Dependency

```kotlin
// gradle/libs.versions.toml
flowable-spring-boot-starter = { module = "org.flowable:flowable-spring-boot-starter", version.ref = "flowable" }

// framework/workflow/build.gradle.kts
dependencies {
    implementation(libs.flowable.spring.boot.starter)  // Via WorkflowConventionPlugin
    integrationTestImplementation(libs.spring.boot.starter.test)
    integrationTestImplementation(libs.postgresql)
    integrationTestImplementation(libs.testcontainers.postgresql)
}
```

---

## Current Configuration (Not Working for Schema Isolation)

### Main Application Configuration

**File**: `framework/workflow/src/main/resources/application.yml`

```yaml
spring:
  flowable:
    database-schema-update: true      # Works - tables are created
    database-schema: flowable         # NOT WORKING - tables still in 'public'
    async-executor-activate: true     # Works
    check-process-definitions: true   # Works
```

### Test Configuration

**File**: `framework/workflow/src/integration-test/resources/application.yml`

```yaml
spring:
  flowable:
    database-type: postgres           # Explicit PostgreSQL
    database-schema-update: true      # Works
    database-schema: flowable         # NOT WORKING
    async-executor-activate: true     # Works
    check-process-definitions: true   # Works
    use-default-data-source: true     # Use Spring DataSource

  jpa:
    hibernate:
      ddl-auto: none  # Disable Hibernate DDL
```

### Test DataSource Configuration

**File**: `FlowableEngineIntegrationTest.kt`

```kotlin
@SpringBootTest(classes = [WorkflowTestApplication::class])
@ActiveProfiles("test")
class FlowableEngineIntegrationTest : FunSpec() {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            TestContainers.startAll()
            registry.add("spring.datasource.url") { TestContainers.postgres.jdbcUrl }
            registry.add("spring.datasource.username") { TestContainers.postgres.username }
            registry.add("spring.datasource.password") { TestContainers.postgres.password }

            // These also attempted but not working for schema isolation
            registry.add("spring.flowable.database-schema-update") { "true" }
            registry.add("spring.flowable.database-schema") { "flowable" }
            registry.add("spring.flowable.async-executor-activate") { "true" }
            registry.add("spring.flowable.check-process-definitions") { "true" }
        }
    }
}
```

---

## What We've Tried (All Failed for Schema Isolation)

### Attempt 1: YAML Configuration Only
- Added `spring.flowable.database-schema: flowable` to application.yml
- **Result**: Tables still in `public` schema

### Attempt 2: Programmatic ProcessEngineConfiguration Bean
```kotlin
@Configuration
open class FlowableConfiguration {
    @Bean
    @ConfigurationProperties(prefix = "spring.flowable")
    open fun springProcessEngineConfiguration(
        dataSource: DataSource,
        transactionManager: PlatformTransactionManager
    ): SpringProcessEngineConfiguration {
        return SpringProcessEngineConfiguration().apply {
            this.dataSource = dataSource
            this.transactionManager = transactionManager
            this.databaseSchema = "flowable"  // Explicitly set
            this.databaseSchemaUpdate = SpringProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE
        }
    }
}
```
- **Result**: Spring CGLIB proxy errors, configuration conflicts

### Attempt 3: DynamicPropertySource Programmatic Config
- Added Flowable properties via `DynamicPropertySource` in test
- **Result**: Tables still in `public` schema

### Attempt 4: Explicit `database-type: postgres`
- Added explicit PostgreSQL database type property
- **Result**: Tables still in `public` schema

---

## What IS Working

✅ **Flowable Integration**: All 6 engines initialize (ProcessEngine, EventRegistryEngine, IdmEngine, DmnEngine, CmmnEngine, AppEngine)
✅ **PostgreSQL Connection**: 56 tables created in PostgreSQL Testcontainers (not H2)
✅ **Auto-Migration**: All ACT_* tables created successfully
✅ **Async Executor**: Background job executors running
✅ **BPMN Deployment**: Can deploy and query process definitions
✅ **Bean Injection**: All Flowable beans available via @Autowired

---

## Research Questions

### Primary Questions

1. **How to configure Flowable 7.1.0 Spring Boot Starter to use dedicated PostgreSQL schema?**
   - What is the correct property name/format? (`database-schema` vs `databaseSchema` vs something else?)
   - Does this require custom ProcessEngineConfiguration bean?
   - Are there Spring Boot auto-configuration exclusions needed?

2. **Does Flowable 7.1.0 support schema isolation with Spring Boot Starter?**
   - Is this feature supported in `flowable-spring-boot-starter`?
   - Or only in `flowable-spring-boot-starter-process` (we're using base starter)?
   - Does it require `flowable-spring-boot-autoconfigure` customization?

3. **What's the correct way to programmatically configure ProcessEngineConfiguration?**
   - How to avoid Spring CGLIB proxy conflicts?
   - How to prevent configuration bean conflicts with auto-configuration?
   - Do we need `@Primary` or `@ConditionalOnMissingBean`?

### Secondary Questions

4. **Schema Creation Mechanics**:
   - Does Flowable create the schema automatically or must it pre-exist?
   - What SQL permissions are required for schema creation?
   - Is there a Flowable property to enable schema auto-creation?

5. **Multi-Engine Schema Configuration**:
   - Flowable has 6 engines (Process, Event, Idm, Dmn, Cmmn, App) - do they all respect `database-schema` property?
   - Or do we need to configure each engine separately?
   - Is there a parent configuration that applies to all engines?

6. **Spring Boot Flowable Properties**:
   - What is the complete list of supported `spring.flowable.*` properties in 7.1.0?
   - Are there undocumented properties for schema configuration?
   - Does the property structure differ between flowable-spring-boot-starter versions?

---

## Expected Research Outputs

### 1. Working Configuration Solution

Provide **complete, tested Kotlin/Spring Boot configuration** that achieves dedicated schema isolation:

```kotlin
// Example desired output format:
@Configuration
open class FlowableSchemaConfiguration {
    @Bean
    @Primary  // or other annotations needed
    open fun customProcessEngineConfiguration(
        dataSource: DataSource,
        transactionManager: PlatformTransactionManager
    ): SpringProcessEngineConfiguration {
        return SpringProcessEngineConfiguration().apply {
            // COMPLETE working configuration here
        }
    }
}
```

#### Alternative YAML Configuration

```yaml
# Complete working YAML configuration
spring:
  flowable:
    # All required properties for schema isolation
```

### 2. Schema Creation SQL

If schema must be pre-created, provide the SQL:

```sql
CREATE SCHEMA IF NOT EXISTS flowable;
-- Any additional setup required
```

### 3. Migration Strategy

If schema isolation isn't possible/recommended with Spring Boot Starter, explain:
- Why it's not supported
- Alternative approaches (separate DataSource, manual configuration)
- Best practices for Flowable multi-tenancy/isolation

### 4. Verification Method

Provide SQL query or test code to verify schema isolation works:

```sql
-- Expected result: tables in 'flowable' schema, not 'public'
SELECT table_schema, COUNT(*)
FROM information_schema.tables
WHERE table_name LIKE 'act_%'
GROUP BY table_schema;
```

---

## Additional Context

### Project Architecture Requirements

- **Pattern**: Hexagonal Architecture + Spring Modulith
- **Testing**: Constitutional TDD with Testcontainers (H2 explicitly forbidden)
- **Schema Isolation Goal**: Separate Flowable workflow state from Axon event store and JPA projections
- **Production Target**: Customer-hosted on-premise deployment with PostgreSQL 16.1+

### Why Schema Isolation Matters

1. **Clear Separation**: Workflow engine state distinct from domain event store
2. **Independent Evolution**: Flowable schema can evolve without affecting application schema
3. **Backup Granularity**: Can backup/restore workflow state independently
4. **Performance Isolation**: Flowable's transaction logging doesn't interfere with event store queries

### Acceptable Alternatives

If dedicated `flowable` schema proves impractical:
1. Accept `public` schema with clear table naming (ACT_* prefix provides natural separation)
2. Use table prefixing strategy for additional isolation
3. Document as technical debt for future enhancement

---

## Research Scope

### Must Have (Critical)

- Definitive answer: Is dedicated schema possible with `flowable-spring-boot-starter` 7.1.0?
- If YES: Complete working configuration (Kotlin/Spring Boot compatible)
- If NO: Explanation why + recommended alternatives

### Should Have (Important)

- Code examples tested with Spring Boot 3.5.x + Kotlin 2.2.x
- Explanation of Flowable's schema configuration mechanics
- Migration path if current approach needs refactoring

### Nice to Have (Optional)

- Flowable best practices for multi-tenant scenarios
- Performance implications of schema isolation vs public schema
- Comparison with other workflow engines (Camunda, Zeebe) schema strategies

---

## Success Criteria

Research is successful if it provides:

1. ✅ **Actionable solution** - Copy-paste ready configuration that works
2. ✅ **Clear explanation** - Why our current approach doesn't work
3. ✅ **Verification method** - How to test schema isolation is working
4. ✅ **Production readiness** - Any caveats or considerations for deployment

---

## Research Constraints

**Do NOT suggest**:
- Using H2 database (explicitly forbidden in architecture)
- Changing Spring Boot version (locked at 3.5.6)
- Changing Kotlin version (pinned at 2.2.20)
- Downgrading Flowable (7.1.0 is target)
- Switching to JUnit (Kotest 6.0.3 mandatory)

**Prefer solutions that**:
- Use Spring Boot auto-configuration (minimal custom code)
- Follow Spring Boot 3.x conventions
- Work with Kotlin idioms (not Java-specific patterns)
- Are testable with Testcontainers
- Align with enterprise best practices

---

## Reference Links for Research

Suggest researching:
- Official Flowable 7.1.x documentation (Spring Boot integration)
- Flowable GitHub repository (issues, examples)
- Spring Boot Flowable Starter source code
- Flowable user forum discussions about schema configuration
- Stack Overflow questions about Flowable schema isolation
- Comparison with Camunda/Activiti schema strategies

---

## Output Format

Please provide research findings in markdown with:

### Executive Summary
- Can it be done? Yes/No/Conditionally
- Recommended approach in 1-2 sentences

### Detailed Solution
- Complete configuration code
- Step-by-step implementation guide
- Any prerequisites or setup required

### Verification
- How to test it works
- Expected vs actual results
- SQL queries for validation

### Explanation
- Why the property `database-schema: flowable` doesn't work by default
- What Flowable does during schema initialization
- How schema configuration differs across Flowable modules (Process, Cmmn, Dmn, Idm, etc.)

### Alternative Approaches
- If dedicated schema isn't practical, what are the alternatives?
- Pros/cons of each approach
- Recommendation for production use

### Production Considerations
- Any performance impact of dedicated schema
- Backup/restore implications
- Migration strategy if schema approach changes

---

**Time Budget**: Deep research - take the time needed to find definitive answers
**Depth**: Comprehensive - investigate Flowable source code if necessary
**Sources**: Prioritize official documentation, then source code, then community discussions

---

## Current Working Directory

`/Users/michael/acci_eaf`

## Relevant Files for Context

- `gradle/libs.versions.toml` - Version catalog with Flowable 7.1.0
- `framework/workflow/src/main/resources/application.yml` - Main Flowable config
- `framework/workflow/src/integration-test/resources/application.yml` - Test Flowable config
- `framework/workflow/src/integration-test/kotlin/.../FlowableEngineIntegrationTest.kt` - Integration test showing current behavior
- `build-logic/src/main/kotlin/conventions/WorkflowConventionPlugin.kt` - Flowable dependency management

---

### End of Research Prompt

This prompt should be sent to an external research agent (Gemini, GPT-4, Claude via consult tool, etc.) for comprehensive investigation of Flowable schema configuration in Spring Boot environments.