# Epic 8: Code Quality & Architectural Alignment

**Epic Goal:** Systematically address critical architectural deviations and technical debt identified through comprehensive codebase analysis, ensuring the EAF framework aligns with architectural specifications before MVP validation (Epic 9).

---

## Epic Description

### Context

The EAF framework (v0.1) has been developed through Epics 1-7 with comprehensive architectural documentation. A deep codebase investigation combined with architectural review (Winston) and analytical validation (Mary) identified **3 critical gaps** requiring resolution before Epic 9 (Licensing Server MVP).

### Validation Methodology

**Investigation Process:**
1. **Initial Analysis**: Deep codebase scan touching thousands of lines
2. **Architectural Review**: Winston (Architect) validated findings against Epic 7 decisions
3. **Analytical Validation**: Mary (Analyst) cross-referenced all findings with architecture docs

**Results:**
- 6 initial findings identified
- 3 findings confirmed as architectural gaps (50% accuracy)
- 3 findings invalidated (architectural misunderstandings corrected)

### Confirmed Architectural Gaps

#### **Gap 1: Read Projections Use JPA Instead of jOOQ** 🚨
- **Architecture Requirement**: "jOOQ for read projections" (docs/architecture/tech-stack.md)
- **Current Implementation**: JPA/Hibernate with @Entity and JpaRepository
- **Root Cause**: Early prototyping in Epic 2 used JPA for speed; jOOQ migration deferred
- **Impact**: Sub-optimal read performance, architectural non-compliance
- **Evidence**: WidgetProjectionRepository extends JpaRepository (should use DSLContext)

#### **Gap 2: 7 Integration Tests Disabled** ⚠️
- **Current State**: 7 critical tests in `kotlin-disabled/` folders not executing
- **Disabled Tests**:
  - Widget domain: WidgetIntegrationTest, WidgetApiIntegrationTest, WidgetEventStoreIntegrationTest, WidgetWalkingSkeletonIntegrationTest, WidgetEventProcessingIntegrationTest
  - Observability: LoggingContextIntegrationTest, StructuredLoggingIntegrationTest
- **Root Cause**: Story 4.6 discovered Kotest+Spring Boot complexity; disabled to unblock Epic 4
- **Impact**: Test coverage gap, no end-to-end validation
- **Evidence**: Intentional technical debt documented in git history

#### **Gap 3: React-Admin Consumer Application Missing** 🔴
- **Current State**: Micro-frontend architecture partially implemented
  - ✅ Framework admin shell exists (`framework/admin-shell/`) - published npm library
  - ✅ UI resource generator exists (`eaf scaffold ui-resource`) - CLI tool functional
  - ❌ Consumer application missing (`apps/admin/`) - only placeholder build files
- **Root Cause**: Epic 7.4 delivered infrastructure (shell + generator) but not integration layer
- **Impact**: Cannot use React-Admin portal; frontend development blocked
- **Evidence**: apps/admin/ contains only build.gradle.kts (737 bytes) and empty package-lock.json

### Architectural Misunderstandings Corrected

The following were initially flagged but validated as **architecturally correct**:

1. **Test Naming Patterns** ✅ VALID
   - Mixed `*Test.kt` and `*Spec.kt` is **intentional** (architecture allows both FunSpec and BehaviorSpec)
   - No violation exists; both patterns follow test-strategy-and-standards-revision-3.md

2. **REST API Layer** ✅ EXISTS
   - WidgetController.kt provides complete CRUD REST API
   - Search error initially missed the controller
   - Architecture compliance verified

3. **Event Sourcing Handlers** ✅ CORRECT
   - @EventSourcingHandler (aggregate reconstitution) vs @EventHandler (projections) correctly separated
   - Standard CQRS/ES pattern properly implemented
   - No architectural gap exists

---

## Stories

### Story 8.1: Migrate Read Projections from JPA to jOOQ

**As a** Developer, **I want** to replace JPA-based CQRS read projections with jOOQ DSL, **so that** the read model achieves optimal performance and aligns with architectural specifications.

**Context:**
- Architecture explicitly specifies jOOQ for read projections (tech-stack.md, data-models.md)
- Current JPA implementation was temporary solution from Epic 2 (Widget walking skeleton)
- Migration provides type-safe queries, better performance, and architectural compliance

**Scope:**
1. Configure jOOQ code generation for PostgreSQL schema
2. Generate jOOQ Records/POJOs from widget_projection table
3. Implement jOOQ-based WidgetProjectionRepository using DSLContext
4. Migrate WidgetQueryHandler to use jOOQ queries
5. Update event projection handlers to write via jOOQ
6. Remove JPA dependencies from projection layer
7. Performance benchmark: verify ≥20% improvement over JPA baseline

**Acceptance Criteria:**
- [ ] jOOQ code generation configured in build.gradle.kts with PostgreSQL dialect
- [ ] Generated jOOQ code for widget_projection table (WIDGET_PROJECTION record)
- [ ] WidgetProjectionRepository rewritten using DSLContext (zero JPA)
- [ ] All query methods use jOOQ DSL:
  ```kotlin
  fun findByWidgetIdAndTenantId(widgetId: String, tenantId: String): WidgetProjection? {
      return dsl.selectFrom(WIDGET_PROJECTION)
          .where(WIDGET_PROJECTION.WIDGET_ID.eq(widgetId))
          .and(WIDGET_PROJECTION.TENANT_ID.eq(tenantId))
          .fetchOneInto(WidgetProjection::class.java)
  }
  ```
- [ ] WidgetQueryHandler integrated with jOOQ repository
- [ ] Projection event handlers write via jOOQ (INSERT/UPDATE/DELETE)
- [ ] All tests passing (unit + integration)
- [ ] Performance benchmarks show ≥20% improvement (query latency)
- [ ] Zero @Entity, @Repository, JpaRepository in projection layer
- [ ] Documentation updated: architecture reflects jOOQ usage

**Technical Notes:**
- Use jOOQ Kotlin extensions for type-safe DSL
- Preserve tenant isolation in all WHERE clauses
- Configure Flyway migrations if schema changes needed
- Rollback plan: Feature flag to toggle JPA/jOOQ implementations

**Estimated Effort:** 5-8 days

---

### Story 8.2: Re-enable and Fix 7 Disabled Integration Tests

**As a** QA Engineer, **I want** all disabled integration tests re-enabled and passing, **so that** critical system behaviors are continuously validated and test coverage gaps are eliminated.

**Context:**
- 7 integration tests disabled in Story 4.6 due to Kotest+Spring Boot complexity
- Tests are valid and comprehensive (cover end-to-end flows, event processing, API validation)
- Solution pattern exists: TenantContextFilterIntegrationTest.kt demonstrates working @SpringBootTest pattern
- Intentional technical debt requiring systematic resolution

**Scope:**
1. Investigate each disabled test's root cause
2. Apply Story 4.6 solution pattern (@Autowired field injection + plugin order)
3. Move tests from `kotlin-disabled/` to `kotlin/` source sets
4. Ensure Testcontainers lifecycle correct (companion object init)
5. Validate tests pass consistently in CI pipeline
6. Document test purposes and coverage areas

**Disabled Tests to Re-enable:**
1. `products/widget-demo/src/integration-test/kotlin-disabled/WidgetIntegrationTest.kt`
2. `products/widget-demo/src/integration-test/kotlin-disabled/WidgetApiIntegrationTest.kt`
3. `products/widget-demo/src/integration-test/kotlin-disabled/WidgetEventStoreIntegrationTest.kt`
4. `products/widget-demo/src/integration-test/kotlin-disabled/WidgetWalkingSkeletonIntegrationTest.kt`
5. `products/widget-demo/src/integration-test/kotlin-disabled/projections/WidgetEventProcessingIntegrationTest.kt`
6. `framework/observability/src/integration-test/kotlin-disabled/LoggingContextIntegrationTest.kt`
7. `framework/observability/src/integration-test/kotlin-disabled/StructuredLoggingIntegrationTest.kt`

**Acceptance Criteria:**
- [ ] All 7 tests moved from `kotlin-disabled/` to `kotlin/` directories
- [ ] Each test follows @SpringBootTest pattern:
  ```kotlin
  @SpringBootTest
  @ActiveProfiles("test")
  class WidgetIntegrationTest : FunSpec() {
      @Autowired
      private lateinit var commandGateway: CommandGateway

      init {
          extension(SpringExtension())
          test("end-to-end widget creation") { /* logic */ }
      }

      companion object {
          @Container
          private val postgres = PostgreSQLContainer<Nothing>("postgres:16.1")

          @JvmStatic
          @DynamicPropertySource
          fun properties(registry: DynamicPropertyRegistry) {
              postgres.start()
              registry.add("spring.datasource.url") { postgres.jdbcUrl }
          }
      }
  }
  ```
- [ ] Build.gradle.kts plugin order correct: `id("eaf.testing")` before `id("eaf.spring-boot")`
- [ ] All 7 tests pass locally (3 consecutive runs)
- [ ] All 7 tests pass in CI with Testcontainers
- [ ] Test execution time <5 minutes total (parallelization if needed)
- [ ] Each test has descriptive comment explaining coverage area
- [ ] No `@Disabled` or `@Ignore` annotations remain

**Technical Notes:**
- Reference TenantContextFilterIntegrationTest.kt for working pattern
- Use `@ActiveProfiles("test")` for test-specific configuration
- Avoid constructor injection (@Autowired fields only for @SpringBootTest)
- Delete `kotlin-disabled/` folders after migration

**Estimated Effort:** 5-7 days

---

### Story 8.3: Implement React-Admin Consumer Application

**As an** Administrator, **I want** a functional React-Admin portal that integrates the framework shell with product UI modules, **so that** I can manage widgets through a modern web interface.

**Context:**
- Epic 7.4 implemented **micro-frontend architecture**:
  - ✅ Framework admin shell (`framework/admin-shell/`) - Publishable npm library with auth, theming, data providers
  - ✅ UI resource generator (`eaf scaffold ui-resource`) - CLI tool generates product UI modules
  - ❌ Consumer application (`apps/admin/`) - Integration layer MISSING
- Architecture pattern: Consumer app imports framework shell + dynamically registers product UI modules
- All backend infrastructure ready: REST API (WidgetController), authentication (Keycloak), projections (to be jOOQ after 8.1)

**Scope:**
1. Generate Widget UI module using existing CLI tool
2. Create React-Admin consumer application in `apps/admin/`
3. Wire TypeScript/Vite build into Gradle build system
4. Integrate Keycloak authentication
5. Verify full CRUD operations functional

**Acceptance Criteria:**

**Phase 1: Generate Widget UI Module**
- [ ] Execute CLI command:
  ```bash
  eaf scaffold ui-resource Widget --module widget-demo --fields id,name,description,value,category
  ```
- [ ] Widget UI module created at `products/widget-demo/ui-module/`
- [ ] Generated module exports WidgetList, WidgetEdit, WidgetCreate, WidgetShow components
- [ ] Module imports `@axians/eaf-admin-shell` framework components

**Phase 2: Create Consumer Application**
- [ ] Initialize TypeScript React app in `apps/admin/`:
  ```bash
  npm create vite@latest . -- --template react-ts
  ```
- [ ] Configure package.json with dependencies:
  ```json
  {
    "name": "@eaf/admin-portal",
    "dependencies": {
      "@axians/eaf-admin-shell": "workspace:*",
      "@eaf/widget-demo-ui": "workspace:*",
      "react": "^18.3.1",
      "react-dom": "^18.3.1"
    }
  }
  ```
- [ ] Create App.tsx integrating shell + widget resource:
  ```typescript
  import { AdminShell } from '@axians/eaf-admin-shell';
  import { widgetResource } from '@eaf/widget-demo-ui';

  const App = () => (
    <AdminShell
      resources={[widgetResource]}
      apiBaseUrl={import.meta.env.VITE_API_URL || 'http://localhost:8080'}
      keycloakConfig={{
        realm: 'eaf',
        clientId: 'eaf-admin',
        url: 'http://localhost:8180',
      }}
    />
  );

  export default App;
  ```
- [ ] Configure Vite for development and production builds
- [ ] Wire npm scripts into Gradle:
  ```kotlin
  // apps/admin/build.gradle.kts
  tasks.register<Exec>("npmInstall") {
      commandLine("npm", "install")
  }
  tasks.register<Exec>("npmBuild") {
      dependsOn("npmInstall")
      commandLine("npm", "run", "build")
  }
  ```

**Phase 3: Integration Validation**
- [ ] Development server runs: `npm run dev` (port 5173)
- [ ] Production build succeeds: `npm run build` (dist/ folder created)
- [ ] Keycloak authentication flow working (redirect to login, token refresh)
- [ ] Widget CRUD operations functional:
  - List widgets with pagination/filtering
  - Create new widget with form validation
  - Edit existing widget with optimistic updates
  - View widget details
- [ ] Tenant context propagated (JWT header in all API calls)
- [ ] Error handling displays user-friendly messages (RFC 7807 problem details parsed)
- [ ] Build integrated into Gradle: `./gradlew :apps:admin:npmBuild` succeeds

**Technical Notes:**
- Follow micro-frontend pattern: thin integration layer, logic in shell/modules
- Use workspace protocol for local package dependencies (pnpm/yarn workspaces)
- Environment variables for API URL and Keycloak config
- Proxy API requests through Vite dev server (avoid CORS in development)

**Estimated Effort:** 5-7 days

---

## Compatibility Requirements

- [ ] jOOQ migration maintains backward compatibility with event store schema
- [ ] Database migrations are backward compatible (no data loss)
- [ ] jOOQ queries produce identical results to JPA baseline (validation tests)
- [ ] Re-enabled tests do not introduce new Testcontainers conflicts
- [ ] React-Admin integrates with existing Keycloak configuration (Epic 3)
- [ ] Consumer app follows existing security patterns (10-layer JWT, 3-layer tenant)

---

## Risk Mitigation

**Risk 1: jOOQ Migration Data Inconsistency**
- **Risk**: Query behavior differences between JPA and jOOQ causing projection errors
- **Mitigation**:
  - Parallel run: Keep JPA implementation, add jOOQ, compare results
  - Feature flag: Toggle between implementations via Spring profile
  - Validation test suite: Compare JPA vs jOOQ outputs for 100+ scenarios
  - Rollback: Revert to JPA if issues found in production

**Risk 2: Integration Test Instability**
- **Risk**: Re-enabled tests fail intermittently due to timing/concurrency issues
- **Mitigation**:
  - Timebox investigation: 2 days per test maximum
  - If unfixable: Document issue, create follow-up story, keep disabled
  - CI validation: 10 consecutive successful runs required before merge
  - Testcontainers tuning: Adjust startup timeouts, resource limits

**Risk 3: Frontend-Backend Integration Gaps**
- **Risk**: React-Admin consumer app has authentication/CORS/data format issues
- **Mitigation**:
  - Contract-first: Use existing OpenAPI spec (WidgetController)
  - Mock API: json-server for frontend development isolation
  - CORS validation: Test CORS headers early in integration
  - End-to-end tests: Playwright/Cypress for full flow validation

**Rollback Plan:**
- Story 8.1: Feature flag reverts to JPA projections
- Story 8.2: Tests remain in kotlin-disabled if unfixable
- Story 8.3: Consumer app is optional; framework shell + generator already delivered

---

## Definition of Done

- [ ] All 3 stories completed with acceptance criteria met
- [ ] jOOQ projections outperform JPA baseline by ≥20%
- [ ] All 7 integration tests passing in CI (<5 min execution)
- [ ] React-Admin consumer app functional with Widget CRUD
- [ ] Zero architectural violations (Konsist, Detekt, ktlint)
- [ ] Test coverage ≥85% line, ≥80% mutation (maintained)
- [ ] Documentation updated:
  - Architecture docs reflect jOOQ usage (tech-stack.md updated)
  - Test re-enablement strategy documented (test-strategy.md)
  - Frontend integration guide (consumer app setup)
- [ ] No regressions in existing functionality
- [ ] Code review approved by Architecture team
- [ ] Epic 9 (Licensing Server MVP) prerequisites satisfied

---

## Dependencies

**Blocks:**
- Epic 9 (Licensing Server MVP Validation) - Requires stable foundation with correct architecture

**Depends On:**
- Epic 1-7 (completed) - Provides base framework
- PostgreSQL 16.1+ (available via Testcontainers)
- Node.js 18+ (for npm builds)

**External Dependencies:**
- jOOQ 3.20.7 (gradle/libs.versions.toml)
- React-Admin v5.4.0 (framework/admin-shell/package.json)
- Keycloak 25.0.6 (authentication)

---

## Estimated Effort

| Story | Complexity | Estimated Effort |
|-------|-----------|-----------------|
| 8.1 - jOOQ Migration | High | 5-8 days |
| 8.2 - Re-enable Tests | High | 5-7 days |
| 8.3 - React-Admin Consumer | Medium | 5-7 days |
| **Total Epic** | **High** | **15-22 days** |

**Notes:**
- Stories 8.1 and 8.3 can be parallelized (different developers)
- Story 8.2 requires systematic investigation (potential blockers)
- Buffer included for integration issues and testing
- Assumes familiarity with jOOQ, Kotest, React-Admin

---

## Success Metrics

Track these KPIs throughout epic execution:

- **Code Quality**: Zero violations (ktlint, detekt, Konsist)
- **Test Coverage**: ≥85% line, ≥80% mutation (maintain)
- **Performance**: jOOQ queries ≥20% faster than JPA (p95 latency)
- **Test Stability**: 100% pass rate for re-enabled tests (10 consecutive runs)
- **Frontend Load**: Consumer app initial load <2 seconds
- **Build Time**: Full CI pipeline <10 minutes
- **API Integration**: All Widget CRUD operations functional in React-Admin

---

## Post-Epic Validation

After completing Epic 8, validate:

### 1. Architectural Compliance Audit
- Run full Konsist test suite (zero violations)
- Verify jOOQ usage matches tech-stack.md specifications
- Confirm all integration tests enabled and passing
- Validate micro-frontend pattern correctly implemented

### 2. Performance Benchmarks
- Compare jOOQ vs JPA read query performance (≥20% improvement required)
- Measure React-Admin page load times (<2 seconds)
- Validate API response times (<200ms p95)

### 3. Integration Verification
- End-to-end test: Create widget via React-Admin UI → Verify in database
- Multi-tenant isolation test: Verify tenant boundaries in all layers
- Authentication flow test: Keycloak login → Token refresh → API calls

### 4. Documentation Review
- Architecture docs match actual implementation
- Test strategy reflects re-enabled tests
- Frontend setup guide accurate and complete

**Gate for Epic 9:** All validation criteria must pass before starting Licensing Server MVP validation.

---

## Validation Summary

**Investigation Methodology:**
- Initial scan: 6 potential findings identified
- Architectural review (Winston): Frontend architecture clarified
- Analytical validation (Mary): 3 findings confirmed, 3 invalidated

**Confirmed Gaps:**
1. ✅ jOOQ not used (JPA instead) - Architectural violation
2. ✅ 7 tests disabled - Intentional technical debt
3. ✅ Consumer app missing - Epic 7 infrastructure exists, integration missing

**Invalidated Findings:**
1. ❌ Test naming inconsistency - Intentional architectural choice
2. ❌ Missing REST API - Already implemented (WidgetController)
3. ❌ Incomplete event sourcing - Correct CQRS/ES pattern

**Epic 8 Accuracy:** 50% (3 of 6 findings valid)

This epic addresses the confirmed architectural gaps, ensuring Epic 9 (Licensing Server MVP) builds on a solid, compliant foundation.
