# Clarifications for External AI Agent - Story 9.2 Research

**Date**: 2025-10-20
**In Response To**: Pre-investigation clarification questions

---

## Question 1: Minimal Reproducible Example

**Answer**: YES - We have a complete reproducible project.

**Location**: `products/widget-demo` (within the monorepo)

**How to Reproduce**:
```bash
# Navigate to repository root
cd $(git rev-parse --show-toplevel)

# Start infrastructure (PostgreSQL, Keycloak)
./scripts/init-dev.sh  # Starts Docker Compose services

# Start widget-demo application
./gradlew :products:widget-demo:bootRun

# In another terminal - run E2E test
./scripts/test-story-9.2-e2e.sh
```

**Expected**: HTTP 200 OK from GET /widgets
**Actual**: HTTP 500 with NoHandlerForQueryException

**Key Files for Investigation**:
- Query Handler: `products/widget-demo/src/main/kotlin/com/axians/eaf/products/widgetdemo/query/WidgetQueryHandler.kt`
- Controller: `products/widget-demo/src/main/kotlin/com/axians/eaf/products/widgetdemo/controllers/WidgetController.kt`
- Axon Config: `framework/cqrs/src/main/kotlin/com/axians/eaf/framework/cqrs/config/AxonConfiguration.kt`
- App Main: `products/widget-demo/src/main/kotlin/com/axians/eaf/products/widgetdemo/WidgetDemoApplication.kt`
- Config: `products/widget-demo/src/main/resources/application.yml`
- DTOs: `shared/shared-api/src/main/kotlin/com/axians/eaf/api/widget/dto/PagedResponse.kt`

**Project Structure**:
```
eaf-monorepo/
├── framework/cqrs/          # Axon configuration, interceptors
├── products/widget-demo/    # Failing query handler
├── shared/shared-api/       # Query/DTO definitions
└── scripts/                 # Test scripts
```

**Note**: This is NOT a minimal hello-world example - it's our actual production codebase with multi-tenancy, security, and full CQRS infrastructure. However, it IS self-contained and reproducible.

---

## Question 2: Non-Generic Return Type Test

**Answer**: YES - We can test this immediately to isolate the generic type issue.

**Current State**:
- Handler returns: `PagedResponse<WidgetResponse>` (concrete data class with generic parameter)
- This is already more concrete than Spring's `Page<T>` interface

**Test Proposal - Create Non-Generic Wrapper**:

```kotlin
// Create a sealed, non-generic wrapper
data class WidgetPagedResult(  // NO generic parameter!
    val content: List<WidgetResponse>,
    val totalElements: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int,
)

// Update handler
@QueryHandler
fun handle(query: FindWidgetsQuery): WidgetPagedResult {  // Non-generic!
    return WidgetPagedResult(...)
}

// Update controller
val response = queryGateway.query(query, WidgetPagedResult::class.java)
```

**Value of This Test**:
If this works, it CONFIRMS the issue is purely generic type erasure, not handler discovery.

**Can Execute**: Yes, I can implement this test now if you'd like to verify the hypothesis before deep research.

**Expected Outcome**:
- ✅ If it works: Problem is **definitely** generic type matching
- ❌ If it fails: Problem is handler auto-discovery itself (more fundamental)

**Decision**: Should I run this test now, or proceed with full research assuming generics are the issue?

---

## Question 3: DEBUG Logs Configuration

**Answer**: YES - We have extensive DEBUG logging enabled (see below).

**Current Logging Configuration** (`application.yml`):
```yaml
logging:
  level:
    com.axians.eaf: DEBUG                           # Our application code
    com.axians.eaf.framework.security.tenant: TRACE # Tenant context debugging
    com.axians.eaf.products.widgetdemo.query: DEBUG # Query handler debugging
    org.axonframework: INFO                         # ⚠️ Could be DEBUG
    org.axonframework.queryhandling: DEBUG          # Query bus debugging
    org.springframework.security: INFO
    org.springframework.boot.autoconfigure: DEBUG   # Spring autoconfiguration
    org.springframework.context: DEBUG              # Spring context
    org.springframework.beans.factory: DEBUG        # Bean creation
    org.jooq: DEBUG                                 # jOOQ debugging
```

**What We Already See in Logs**:
```json
{"level":"DEBUG", "message":"Creating shared instance of singleton bean 'widgetQueryHandler'"}
{"level":"DEBUG", "message":"Autowiring by type from bean name 'widgetQueryHandler'..."}
{"level":"DEBUG", "message":"Identified candidate component class: .../WidgetQueryHandler.class"}
```

**What We DON'T See** (Expected but Missing):
- Query handler subscription logs (e.g., "Subscribed query handler for...")
- Query handler registration confirmation from Axon internals
- MessageHandlerConfigurer scanning logs showing handler discovery

**Recommended Additional Logging**:
```yaml
logging:
  level:
    org.axonframework: DEBUG  # ← Currently INFO, should be DEBUG
    org.axonframework.config: TRACE  # Configuration debugging
    org.axonframework.messaging.annotation: TRACE  # Annotation scanning
    org.axonframework.spring: TRACE  # Spring integration
```

**Can Enable**: Yes, I can update application.yml with these settings and re-run tests.

**Decision**: Should I enable TRACE logging for Axon internals now, or do you want to proceed with current logs?

---

## Question 4: Kotlin Type Annotations

**Answer**: NO - We are NOT using @JvmSuppressWildcards, reified generics, or typealias.

**Verification Performed**:
```bash
grep -r "@JvmSuppressWildcards\|@JvmWildcard\|typealias\|reified" \
  shared/shared-api/src/main/kotlin/com/axians/eaf/api/widget/ \
  products/widget-demo/src/main/kotlin/com/axians/eaf/products/widgetdemo/
# Result: No matches found
```

**Our DTOs are Pure Kotlin Data Classes**:

```kotlin
// Query
data class FindWidgetsQuery(
    val tenantId: String,
    val page: Int,
    val size: Int,
    val category: String?,
    val search: String?,
    val sort: List<String>,
)

// Response
data class PagedResponse<T>(  // Simple generic parameter, no wildcards
    val content: List<T>,
    val totalElements: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int,
)

data class WidgetResponse(
    val id: String,
    val name: String,
    val description: String?,
    val value: BigDecimal,
    val category: String,
    val metadata: Map<String, Any>?,
    val createdAt: LocalDateTime,
)
```

**Annotations Present**:
- ✅ `@Suppress("UNCHECKED_CAST")` - Used in controller for ResponseType casting
- ✅ `@Component`, `@Service`, `@QueryHandler`, `@Transactional` - Standard Spring/Axon annotations
- ❌ NO JVM interop annotations
- ❌ NO reified generics
- ❌ NO typealiases

**Kotlin Language Features Used**:
- `open class` - Required for Spring CGLIB proxying with @Transactional
- Nullable types (`String?`, `Map<String, Any>?`)
- Data classes (for immutable DTOs)
- Default parameters in functions

**Nothing Exotic** - Standard Kotlin patterns for Spring Boot applications.

---

## Additional Information

### Minimal Test to Run

If you want the absolute minimal reproduction:

**1. Start Application**:
```bash
cd /Users/michael/acci_eaf
./gradlew :products:widget-demo:bootRun
```

**2. Wait for Startup** (~10 seconds)

**3. Test Query** (without auth to simplify):
```bash
curl -v http://localhost:8081/widgets?page=0&size=10
```

**Expected**: HTTP 401 Unauthorized (security working, handler found)
**Actual**: HTTP 500 Internal Server Error with NoHandlerForQueryException

### Current Codebase State

**Files Modified During Investigation** (can be reset if needed):
```
M products/widget-demo/src/main/kotlin/com/axians/eaf/products/widgetdemo/query/WidgetQueryHandler.kt
M products/widget-demo/src/main/kotlin/com/axians/eaf/products/widgetdemo/controllers/WidgetController.kt
A shared/shared-api/src/main/kotlin/com/axians/eaf/api/widget/dto/PagedResponse.kt
M products/widget-demo/src/main/resources/application.yml
```

**Can Provide**:
- Git repository access (if needed)
- Full application logs with any logging level
- Additional test scenarios
- Heap dumps, thread dumps if useful

### Quick Hypothesis Test Available

**Non-Generic Wrapper Test** (Question #2):

I can immediately create and test:
```kotlin
// Non-generic concrete class
data class FindWidgetsResult(
    val widgets: List<WidgetResponse>,  // Not generic!
    val totalElements: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int,
)

@QueryHandler
fun handle(query: FindWidgetsQuery): FindWidgetsResult {  // No <T>!
    return FindWidgetsResult(...)
}
```

**Time to Execute**: ~5 minutes
**Value**: Definitively confirms if generics are the root cause

**Should I run this test now before you begin deep research?**

---

## Calibration Recommendations

Based on your questions, here's my recommendation for research priorities:

**Priority 1 (Highest)**: **Non-Generic Type Test** (Question #2)
- Quick to execute, high diagnostic value
- If it works → Problem is generics (focus research there)
- If it fails → Problem is handler discovery (broader research needed)

**Priority 2**: **Enhanced Axon Logging** (Question #3)
- Enable TRACE for `org.axonframework.config` and `org.axonframework.messaging.annotation`
- Re-run and capture full handler discovery process
- Look for "why" Axon isn't finding our handler

**Priority 3**: **Deep Dive** (Questions #1, #4)
- Review our reproducible example with fresh eyes
- Confirm no exotic Kotlin features interfering
- Search for similar issues in Axon community

---

## Immediate Actions Available

**Option A: Run Non-Generic Test First** (Recommended)
- I execute the test with `FindWidgetsResult` (no generics)
- Results inform your research direction
- ~5 minutes

**Option B: Enable TRACE Logging**
- Update application.yml with full Axon TRACE logging
- Re-run and provide complete logs
- ~10 minutes

**Option C: Proceed with Full Research**
- You begin deep investigation with current information
- I can run additional tests in parallel if needed

**Your Call**: Which calibration approach do you prefer before diving into deep research?

---

**Ready to Execute Tests**: Standing by for your direction.

**Contact**: Awaiting your response on preferred investigation approach.
