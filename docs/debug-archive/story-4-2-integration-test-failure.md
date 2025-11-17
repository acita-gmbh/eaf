# Deep Research Prompt: TenantContextFilter Integration Test Failure (Story 4.2)

> **⚠️ HISTORICAL DEBUGGING DOCUMENTATION**
>
> **Status:** RESOLVED - Story 4.2 completed successfully
> **Date Created:** 2025-11-17
> **Purpose:** This document was created during Story 4.2 implementation to facilitate external AI debugging of the "Two Filter Chain" problem. It documents 19 debugging commits and the complete investigation process.
>
> **Resolution:** The issue was resolved via dual AI analysis identifying filter auto-registration and SimpleMeterRegistry bean conflicts. Complete solution implemented with FilterRegistrationBean(enabled=false) + SecurityConfiguration.addFilterAfter().
>
> **Why Preserved:** Valuable reference for future Spring Security filter integration challenges and demonstrates systematic debugging approach for complex Spring Boot/Security issues.

---

## Problem Statement

Spring Boot integration tests for `TenantContextFilter` (Layer 1 of 3-layer tenant isolation) consistently fail with `TenantContext` returning NULL, despite all diagnostic tests passing. After 19 commits and extensive debugging, the root cause remains unidentified.

**GitHub Repository:** https://github.com/acita-gmbh/eaf (Public)
**Branch:** `feature/4-2-tenant-context-filter`
**Pull Request:** #110

---

## Technology Stack

- **Language:** Kotlin 2.2.21
- **Framework:** Spring Boot 3.5.7
- **Architecture:** Hexagonal + CQRS/Event Sourcing (Axon 4.12.1)
- **Security:** Spring Security 6.x with OAuth2 Resource Server (Keycloak 26.4.2)
- **Testing:** Kotest 6.0.4 + Testcontainers 1.21.3
- **Build:** Gradle 9.1.0 monorepo

---

## What Works ✅

1. **Unit Tests:** 6/6 PASSED
   - Filter logic is correct
   - Tenant extraction, validation, cleanup all working
   - Metrics emission verified

2. **Diagnostic Tests:** Both PASSED
   - Filter bean is loaded and autowired successfully
   - JWT contains `tenant_id` claim (Base64 decoded and verified: `tenant-test-001`)

3. **Build & Quality:** All passing
   - ktlint, detekt, build, security scans

4. **Component Loading:**
   - `TenantContextFilter` registered as `@Component`
   - `MultiTenancyTestApplication` loads with ComponentScan
   - `SecurityConfiguration` present in classpath

---

## What Fails ❌

**Integration Test Failure Signature:**
```
Response: {"tenantId":"MISSING","message":"Tenant context: null"}
Expected: tenant-test-001
```

**All integration tests return NULL tenant context:**
- AC2+AC3: Extract tenant_id from JWT → FAILED
- AC4: Missing tenant_id → 400 Bad Request → FAILED (gets 200 instead)
- AC5: ThreadLocal cleanup → FAILED
- AC6: Concurrent requests → FAILED
- AC7: Metrics emitted → PASSED (only checks HTTP 200, not tenant_id)

---

## Core Implementation

### TenantContextFilter (Main Production Code)

```kotlin
// framework/multi-tenancy/src/main/kotlin/.../TenantContextFilter.kt
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class TenantContextFilter(
    private val meterRegistry: MeterRegistry,
    private val objectMapper: ObjectMapper,
) : Filter {
    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain,
    ) {
        val httpResponse = response as HttpServletResponse
        val sample = Timer.start(meterRegistry)

        try {
            val authentication = SecurityContextHolder.getContext().authentication

            // DEBUG LOGGING (added for troubleshooting)
            println("🔍 TenantContextFilter - Authentication type: ${authentication?.javaClass?.simpleName ?: "NULL"}")

            // CRITICAL: Skip tenant extraction for non-JWT requests
            if (authentication !is JwtAuthenticationToken) {
                println("⚠️ Non-JWT authentication - skipping tenant extraction")
                chain.doFilter(request, response)
                return  // ← EARLY RETURN - No tenant extraction!
            }

            println("✅ JWT Authentication found - proceeding with tenant extraction")

            val jwt = authentication.token
            val tenantId = jwt?.getClaimAsString("tenant_id")

            val error = validateTenantId(tenantId, httpResponse)
            if (error != null) {
                return
            }

            TenantContext.setCurrentTenantId(tenantId!!)
            chain.doFilter(request, response)
        } catch (ex: Exception) {
            meterRegistry.counter("tenant_context_extraction_errors", ...).increment()
            throw ex
        } finally {
            TenantContext.clearCurrentTenant()
            sample.stop(extractionTimer)
        }
    }
}
```

**Key Execution Path:**
- Line 88: `val authentication = SecurityContextHolder.getContext().authentication`
- Line 99-102: `if (authentication !is JwtAuthenticationToken) { ... return }` ← **SKIPS EXTRACTION**
- Line 106: `TenantContext.setCurrentTenantId(tenantId!!)` ← **NEVER REACHED**

---

## Test Configuration

### Integration Test Class

```kotlin
// framework/multi-tenancy/src/integration-test/kotlin/.../TenantContextFilterIntegrationTest.kt
@SpringBootTest(
    classes = [MultiTenancyTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=\${eaf.security.jwt.issuer-uri}",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=\${eaf.security.jwt.jwks-uri}",
        "eaf.security.jwt.audience=eaf-api",
        "eaf.security.role-whitelist=WIDGET_ADMIN,WIDGET_VIEWER,ADMIN",
        "logging.level.com.axians.eaf=DEBUG",
        "spring.modulith.events.jdbc.schema-initialization.enabled=false",
    ],
)
@ActiveProfiles("keycloak-test")
class TenantContextFilterIntegrationTest : FunSpec() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var tenantContextFilter: TenantContextFilter  // ✅ Autowires successfully

    init {
        extension(SpringExtension())

        test("DIAGNOSTIC: Verify JWT contains tenant_id claim") {
            val jwt = KeycloakTestContainer.generateToken("admin", "password")
            val payload = String(java.util.Base64.getUrlDecoder().decode(jwt.split(".")[1]))
            println("JWT Payload: $payload")
            payload shouldContain "tenant_id"
            payload shouldContain "tenant-test-001"  // ✅ PASSES
        }

        test("AC2+AC3: Extract tenant_id from JWT and populate TenantContext") {
            val jwt = KeycloakTestContainer.generateToken("admin", "password")
            val headers = HttpHeaders().apply { setBearerAuth(jwt) }
            val response = restTemplate.exchange(
                "http://localhost:$port/test/tenant-info",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                String::class.java,
            )

            response.statusCode shouldBe HttpStatus.OK
            response.body shouldContain "tenant-test-001"  // ❌ FAILS - gets "MISSING"
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            KeycloakTestContainer.start()
            registry.add("eaf.security.jwt.issuer-uri") {
                KeycloakTestContainer.getIssuerUri()
            }
            registry.add("eaf.security.jwt.jwks-uri") {
                KeycloakTestContainer.getJwksUri()
            }
        }
    }
}
```

### Test Application

```kotlin
// framework/multi-tenancy/src/integration-test/kotlin/.../test/MultiTenancyTestApplication.kt
@SpringBootApplication
@ComponentScan(
    basePackages = [
        "com.axians.eaf.framework.multitenancy",
        "com.axians.eaf.framework.security",  // ← Security module for JWT validation
    ],
)
open class MultiTenancyTestApplication {
    @Bean
    open fun meterRegistry() = SimpleMeterRegistry()
}
```

### Test Controller

```kotlin
// framework/multi-tenancy/src/integration-test/kotlin/.../TenantTestController.kt
@RestController
@RequestMapping("/test")
open class TenantTestController {
    @GetMapping("/tenant-info")
    open fun getTenantInfo(): ResponseEntity<Map<String, String>> {
        val tenantId = TenantContext.current()  // ← Returns NULL!
        return ResponseEntity.ok(
            mapOf(
                "tenantId" to (tenantId ?: "MISSING"),
                "message" to "Tenant context: $tenantId",
            ),
        )
    }
}
```

### Profile Configuration

```yaml
# framework/multi-tenancy/src/integration-test/resources/application-keycloak-test.yml
spring:
  jmx:
    enabled: false
  main:
    allow-bean-definition-overriding: true
  modulith:
    events:
      jdbc:
        schema-initialization:
          enabled: false

logging:
  level:
    root: WARN
    com.axians.eaf: DEBUG
    org.springframework.security: DEBUG
    org.testcontainers: INFO

eaf:
  security:
    revocation:
      fail-closed: false
```

### SecurityConfiguration

```kotlin
// framework/security/src/main/kotlin/.../SecurityConfiguration.kt
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
// NOTE: @Profile("!(test | rbac-test)") was REMOVED in Story 4.2
open class SecurityConfiguration {
    @Bean
    open fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/actuator/health").permitAll()
                    .anyRequest().authenticated()  // All other requests need JWT
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwtConfigurer ->
                    jwtConfigurer.jwtAuthenticationConverter(jwtAuthenticationConverter)
                }
            }
            .csrf { it.disable() }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
        return http.build()
    }

    @Bean
    open fun jwtDecoder(): JwtDecoder {
        val decoder = NimbusJwtDecoder.withJwkSetUri(keycloakConfig.jwksUri).build()
        // ... 10-layer JWT validation
        decoder.setJwtValidator(customValidators)
        return decoder
    }
}
```

---

## Keycloak Configuration

### Realm Export with Protocol Mapper

```json
// shared/testing/src/main/resources/keycloak/realm-export.json
{
  "realm": "eaf",
  "users": [
    {
      "username": "admin",
      "credentials": [{"type": "password", "value": "password"}],
      "realmRoles": ["WIDGET_ADMIN", "ADMIN"],
      "attributes": {
        "tenant_id": ["tenant-test-001"]  // ← User attribute
      }
    },
    {
      "username": "no-tenant",
      "attributes": {}  // ← No tenant_id for negative testing
    }
  ],
  "clients": [
    {
      "clientId": "eaf-api",
      "directAccessGrantsEnabled": true,
      "protocolMappers": [
        {
          "name": "tenant-id-mapper",
          "protocol": "openid-connect",
          "protocolMapper": "oidc-usermodel-attribute-mapper",
          "config": {
            "user.attribute": "tenant_id",
            "claim.name": "tenant_id",
            "access.token.claim": "true",
            "id.token.claim": "true",
            "jsonType.label": "String"
          }
        }
      ]
    }
  ]
}
```

### KeycloakTestContainer (Shared Utility)

```kotlin
// shared/testing/src/main/kotlin/.../KeycloakTestContainer.kt
object KeycloakTestContainer {
    private val container =
        KeycloakContainer("quay.io/keycloak/keycloak:26.4.2")
            .withReuse(true)
            .withRealmImportFile("keycloak/realm-export.json")  // ← Loads realm
            .withEnv("KC_HTTP_ENABLED", "true")

    fun start() { if (!container.isRunning) container.start() }

    fun getIssuerUri(): String = "${container.authServerUrl}/realms/eaf"
    fun getJwksUri(): String = "${getIssuerUri()}/protocol/openid-connect/certs"

    fun generateToken(username: String, password: String = "password"): String =
        KeycloakTokenGenerator.generateToken(
            keycloakUrl = container.authServerUrl,
            realm = "eaf",
            clientId = "eaf-api",
            username = username,
            password = password,
        )  // ← Uses password grant to get real JWT
}
```

---

## Working Reference: Epic 3 Security Tests

### Epic 3 Pattern (WORKS!)

```kotlin
// framework/security/src/integration-test/kotlin/.../KeycloakIntegrationTest.kt
@SpringBootTest(classes = [SecurityTestApplication::class])
@ActiveProfiles("keycloak-test")
@AutoConfigureMockMvc
class KeycloakIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    init {
        extension(SpringExtension())

        test("should allow authenticated requests with valid JWT") {
            val jwt = KeycloakTestContainer.generateToken("admin", "password")

            mockMvc
                .perform(
                    get("/api/widgets")  // ← Test endpoint
                        .header("Authorization", "Bearer $jwt"),
                ).andExpect(status().isOk())  // ✅ PASSES
        }
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            KeycloakTestContainer.start()
            registry.add("eaf.security.jwt.issuer-uri") {
                KeycloakTestContainer.getIssuerUri()
            }
            registry.add("eaf.security.jwt.jwks-uri") {
                KeycloakTestContainer.getJwksUri()
            }
            registry.add("eaf.security.jwt.audience") { "eaf-api" }
        }
    }
}
```

**Epic 3 Test Application:**
```kotlin
@SpringBootApplication
@ComponentScan(basePackages = ["com.axians.eaf.framework.security"])
class SecurityTestApplication  // No profile, no additional beans
```

**Epic 3 Test Controller:**
```kotlin
@RestController
@RequestMapping("/api/widgets")
open class TestController {  // NO @Profile annotation!
    @GetMapping
    @PreAuthorize("hasRole('WIDGET_ADMIN')")
    open fun getWidgets(): Map<String, String> = mapOf("status" to "ok")
}
```

---

## Observed Symptoms

### Symptom 1: TenantContext Always NULL

```
HTTP GET /test/tenant-info with "Authorization: Bearer <valid-jwt-with-tenant_id>"
Response: {"tenantId":"MISSING","message":"Tenant context: null"}
Expected: {"tenantId":"tenant-test-001","message":"Tenant context: tenant-test-001"}
```

### Symptom 2: No Debug Logs Visible

Despite adding `println()` statements in TenantContextFilter:
```kotlin
println("🔍 TenantContextFilter - Authentication type: ${authentication?.javaClass?.simpleName ?: "NULL"}")
```

**No output appears in CI logs** → Suggests filter may not be executing at all.

### Symptom 3: AC7 Passes (Only Checks HTTP 200)

```kotlin
test("AC7: Metrics emitted") {
    val response = restTemplate.exchange(...)
    response.statusCode shouldBe HttpStatus.OK  // ✅ PASSES
}
```

**AC7 passes because it only checks HTTP status, not tenant_id content.**

---

## Attempts Made (19 Commits)

### Configuration Attempts

1. **MockMvc vs TestRestTemplate**
   - Tried MockMvc with `@AutoConfigureMockMvc`
   - Tried `@AutoConfigureMockMvc(addFilters = true)`
   - Switched to TestRestTemplate with RANDOM_PORT
   - Result: All failed

2. **Profile Variants**
   - `@ActiveProfiles("test")` → Security disabled
   - `@ActiveProfiles("keycloak-test")` → Still fails
   - `@ActiveProfiles("test", "keycloak-test")` → Still fails
   - Removed @Profile from TenantTestController → Still fails
   - Removed @Profile from SecurityConfiguration → Still fails

3. **Bean Configuration**
   - Added SimpleMeterRegistry bean
   - Added integrationTestImplementation(security module)
   - ComponentScan includes both multitenancy + security
   - open class and open fun for Spring CGLIB

4. **Keycloak Setup**
   - Protocol mapper for tenant_id → JWT claim
   - User "no-tenant" without tenant_id for negative testing
   - KeycloakTestContainer from shared:testing
   - application-keycloak-test.yml created

5. **Spring Boot Configuration**
   - Disabled Spring Modulith Events
   - Bean overriding enabled
   - webEnvironment = MOCK → Still fails
   - webEnvironment = RANDOM_PORT → Still fails

---

## Diagnostic Evidence

### Evidence 1: Filter Bean Loads

```kotlin
@Autowired
private lateinit var tenantContextFilter: TenantContextFilter

test("DIAGNOSTIC: Verify TenantContextFilter bean loaded") {
    tenantContextFilter shouldBe instanceOf<TenantContextFilter>()
    // ✅ PASSES - Bean exists and autowires
}
```

### Evidence 2: JWT Contains Claim

```kotlin
test("DIAGNOSTIC: Verify JWT contains tenant_id claim") {
    val jwt = KeycloakTestContainer.generateToken("admin", "password")
    val payload = String(java.util.Base64.getUrlDecoder().decode(jwt.split(".")[1]))
    println("JWT Payload: $payload")
    payload shouldContain "tenant_id"
    payload shouldContain "tenant-test-001"
    // ✅ PASSES - JWT has correct claim
}
```

**Decoded JWT Payload Example:**
```json
{
  "sub": "admin",
  "iss": "http://localhost:xxxxx/realms/eaf",
  "aud": "eaf-api",
  "exp": 1700000000,
  "iat": 1699999000,
  "tenant_id": "tenant-test-001",  // ← PRESENT!
  "realm_access": { "roles": ["WIDGET_ADMIN", "ADMIN"] }
}
```

### Evidence 3: HTTP Request Succeeds

- All requests return HTTP 200 OK (not 401 Unauthorized)
- Controller endpoint is reachable
- No exceptions thrown

### Evidence 4: No Debug Logs

- `println()` statements in filter don't appear in CI logs
- Suggests filter may not execute OR println() doesn't work in filter context

---

## AI Expert Analysis (Already Attempted)

**Hypothesis:** SecurityConfiguration @Profile exclusion prevented loading.

**Fix Attempted:** Removed `@Profile("!(test | rbac-test)")` from SecurityConfiguration.

**Result:** Still fails - TenantContext remains NULL.

---

## Critical Questions to Answer

1. **Is the filter executing at all?**
   - How to verify filter execution in Spring Boot integration tests?
   - Why don't println() logs appear?

2. **What is the authentication type in SecurityContextHolder?**
   - Is it NULL?
   - Is it JwtAuthenticationToken?
   - Is it something else (AnonymousAuthenticationToken, etc.)?

3. **Why does Epic 3 pattern work but Story 4.2 fails?**
   - Both use @SpringBootTest + @ActiveProfiles("keycloak-test")
   - Both use KeycloakTestContainer
   - Both have test controllers
   - What's different?

4. **Is there a missing Spring Boot auto-configuration?**
   - Filter auto-registration
   - Security filter chain ordering
   - OAuth2 Resource Server configuration

5. **Is @Order(HIGHEST_PRECEDENCE + 10) correct?**
   - Should it run BEFORE or AFTER Spring Security?
   - Is the ordering working in tests?

---

## Success Criteria

**Integration tests should:**

1. ✅ Start embedded server with full filter chain
2. ✅ Load SecurityConfiguration with JWT validation
3. ✅ Generate real Keycloak JWT with tenant_id claim
4. ✅ Make HTTP request with JWT in Authorization header
5. ✅ Spring Security validates JWT → populates SecurityContextHolder with JwtAuthenticationToken
6. ✅ TenantContextFilter extracts tenant_id from JWT
7. ✅ TenantContext.setCurrentTenantId() populates ThreadLocal
8. ✅ Test controller returns tenant_id from TenantContext
9. ✅ Response contains: `{"tenantId":"tenant-test-001",...}`

**Expected Test Results:**
- AC2+AC3: PASS (tenant_id extracted)
- AC4: PASS (400 Bad Request for missing tenant_id)
- AC5: PASS (ThreadLocal cleanup/isolation)
- AC6: PASS (concurrent request isolation)
- AC7: PASS (metrics emitted)

---

## Module Structure

```
eaf/
├── framework/
│   ├── security/                    # Epic 3 - JWT validation (WORKS)
│   │   ├── src/main/.../SecurityConfiguration.kt
│   │   └── src/integration-test/
│   │       ├── .../KeycloakIntegrationTest.kt  # ✅ PASSES
│   │       ├── .../SecurityTestApplication.kt
│   │       ├── .../TestController.kt
│   │       └── resources/application-keycloak-test.yml
│   │
│   └── multi-tenancy/              # Story 4.2 - Tenant extraction (FAILS)
│       ├── src/main/.../TenantContextFilter.kt
│       ├── src/test/.../TenantContextFilterTest.kt  # ✅ 6/6 PASSED
│       └── src/integration-test/
│           ├── .../TenantContextFilterIntegrationTest.kt  # ❌ FAILS
│           ├── .../test/MultiTenancyTestApplication.kt
│           ├── .../TenantTestController.kt
│           └── resources/application-keycloak-test.yml
│
└── shared/
    └── testing/
        ├── src/main/kotlin/.../KeycloakTestContainer.kt
        └── src/main/resources/keycloak/realm-export.json
```

**Dependencies:**
- `framework/multi-tenancy/build.gradle.kts`:
  - `implementation(project(":framework:core"))`
  - `integrationTestImplementation(project(":framework:security"))`  # Story 4.2

---

## Specific Technical Context

### Filter Ordering

```kotlin
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)  // Runs AFTER Spring Security
class TenantContextFilter
```

**Expected Order:**
1. Spring Security (`@Order(HIGHEST_PRECEDENCE)`) - JWT validation
2. TenantContextFilter (`@Order(HIGHEST_PRECEDENCE + 10)`) - Tenant extraction
3. Business logic (default order)

### Spring Security JWT Flow (Epic 3)

1. `BearerTokenAuthenticationFilter` extracts JWT from Authorization header
2. `JwtDecoder` validates JWT (10 layers)
3. `JwtAuthenticationConverter` creates `JwtAuthenticationToken`
4. `SecurityContextHolder.setContext()` stores authentication
5. **TenantContextFilter should now extract tenant_id from SecurityContextHolder**

---

## Hypotheses to Investigate

### Hypothesis 1: Filter Not Registered in Servlet Container

**Evidence:**
- Bean exists (diagnostic test)
- No debug logs visible
- @Component should auto-register
- @Order should set execution priority

**Questions:**
- Does Spring Boot auto-register @Component Filter beans in integration tests?
- Is there a missing FilterRegistrationBean?
- Does webEnvironment = RANDOM_PORT affect filter registration?

### Hypothesis 2: SecurityContextHolder Empty

**Evidence:**
- Line 91: `if (authentication !is JwtAuthenticationToken)` → early return
- No debug logs suggesting JWT authentication found
- AC7 passes (200 OK) suggesting some authentication exists

**Questions:**
- What authentication type is in SecurityContextHolder?
- Does Spring Security OAuth2 Resource Server work in tests?
- Is JwtAuthenticationToken being created?

### Hypothesis 3: Test Profile Interference

**Evidence:**
- Originally had @Profile("!(test | rbac-test)") on SecurityConfiguration
- Test environment may activate "test" profile implicitly
- @ActiveProfiles("keycloak-test") explicitly set

**Questions:**
- Which profiles are actually active during test execution?
- Is "test" profile implicitly activated by @SpringBootTest?
- Does keycloak-test profile configuration conflict?

### Hypothesis 4: Missing Spring Boot Auto-Configuration

**Evidence:**
- Epic 3 tests work with identical setup
- Story 4.2 tests fail despite matching pattern
- Difference: Epic 3 only scans security package, Story 4.2 scans security + multitenancy

**Questions:**
- Does ComponentScan order matter?
- Are there conflicting beans between modules?
- Is there a missing @Import or @EnableAutoConfiguration?

---

## Research Tasks

### Task 1: Verify Filter Execution

**Objective:** Determine if TenantContextFilter.doFilter() executes at all.

**Approaches:**
1. Add counter metric at filter entry point (verifiable in test)
2. Use SLF4J logger instead of println() (appears in CI logs)
3. Create test that directly calls filter.doFilter() with mock request
4. Check Spring Boot filter registration debug logs

**Files to Modify:**
- `TenantContextFilter.kt` - Add SLF4J logging
- Integration test - Check metric counters

### Task 2: Inspect SecurityContextHolder

**Objective:** Determine authentication type during test execution.

**Approaches:**
1. Add test that injects SecurityContextHolder and logs authentication
2. Create Filter with higher priority (@Order(HIGHEST_PRECEDENCE + 5)) that logs authentication
3. Use Spring Security test utilities to inspect security context
4. Add @WithMockUser or @WithJwtToken to test

**Files to Create/Modify:**
- Diagnostic filter or test
- SecurityContextHolder inspection

### Task 3: Compare Working vs Failing Tests

**Objective:** Identify exact differences between Epic 3 (works) and Story 4.2 (fails).

**Comparison Matrix:**

| Aspect | Epic 3 (WORKS) | Story 4.2 (FAILS) | Difference? |
|--------|----------------|-------------------|-------------|
| Test Framework | Kotest FunSpec | Kotest FunSpec | ✅ Same |
| Spring Boot Test | @SpringBootTest | @SpringBootTest | ✅ Same |
| Profile | keycloak-test | keycloak-test | ✅ Same |
| MockMvc | @AutoConfigureMockMvc | TestRestTemplate | ⚠️ Different |
| Test App | SecurityTestApplication | MultiTenancyTestApplication | ⚠️ Different |
| ComponentScan | security only | security + multitenancy | ⚠️ Different |
| Endpoint | /api/widgets | /test/tenant-info | ⚠️ Different |
| Controller | TestController | TenantTestController | ⚠️ Different |
| Additional Beans | None | SimpleMeterRegistry | ⚠️ Different |

**Focus Investigation On:**
- MultiTenancyTestApplication differences
- Multi-package ComponentScan effects
- SimpleMeterRegistry bean conflicts

### Task 4: Spring Boot Filter Registration Deep Dive

**Objective:** Understand how Spring Boot registers @Component Filter beans.

**Research Areas:**
1. Spring Boot auto-configuration for filters
2. FilterRegistrationBean vs @Component Filter
3. @Order annotation behavior in tests vs production
4. ServletContext filter registration in embedded containers

**Documentation:**
- Spring Boot Reference: Filter Registration
- Spring Security Filter Chain Architecture
- Testcontainers Spring Boot Integration

### Task 5: Profile Activation Debugging

**Objective:** Determine which profiles are actually active.

**Approaches:**
1. Add test that logs `environment.activeProfiles`
2. Check Spring Boot startup logs for activated profiles
3. Verify @Profile conditions evaluate correctly
4. Test with explicit profile activation in test properties

**Files to Modify:**
- Add profile logging in MultiTenancyTestApplication
- Check CI logs for profile activation messages

---

## Expected Root Cause Categories

Based on symptoms, the root cause likely falls into one of these categories:

### Category A: Filter Not Registered

**Symptoms Match:**
- No debug logs
- Filter bean exists but doesn't execute
- @Component annotation present

**Possible Causes:**
- Spring Boot doesn't auto-register @Component Filter in tests
- webEnvironment configuration affects filter registration
- Missing ServletContext in test environment
- Filter excluded by test configuration

**Solution Direction:**
- Explicit FilterRegistrationBean
- Different webEnvironment setting
- @ServletComponentScan annotation

### Category B: SecurityContextHolder Not Populated

**Symptoms Match:**
- HTTP 200 OK (authentication works)
- TenantContext NULL (filter skips extraction)
- No JWT authentication logs

**Possible Causes:**
- SecurityConfiguration not loading (despite @Profile removal)
- JWT validation disabled in test
- OAuth2 Resource Server not configured
- BearerTokenAuthenticationFilter not in filter chain

**Solution Direction:**
- Verify SecurityFilterChain bean creation
- Check OAuth2 auto-configuration
- Ensure JwtDecoder bean exists
- Test SecurityContextHolder directly

### Category C: Test Configuration Conflict

**Symptoms Match:**
- Epic 3 works, Story 4.2 fails
- Multi-package ComponentScan
- Additional test beans

**Possible Causes:**
- Bean definition conflicts
- ComponentScan order matters
- SimpleMeterRegistry shadows auto-configured MeterRegistry
- Profile activation interference

**Solution Direction:**
- Minimal test application (remove SimpleMeterRegistry)
- Single package ComponentScan
- Check for bean definition conflicts

---

## Recommended Investigation Approach

### Phase 1: Verify Filter Execution (Highest Priority)

**Action:**
1. Replace `println()` with SLF4J logger:
```kotlin
private val logger = LoggerFactory.getLogger(TenantContextFilter::class.java)

override fun doFilter(...) {
    logger.info("=== TenantContextFilter EXECUTING ===")
    val authentication = SecurityContextHolder.getContext().authentication
    logger.info("Authentication type: ${authentication?.javaClass?.name ?: "NULL"}")
    logger.info("Is JwtAuthenticationToken: ${authentication is JwtAuthenticationToken}")
    // ...
}
```

2. Run integration test and check CI logs for logger output
3. If logs appear: Filter IS executing → investigate authentication type
4. If NO logs: Filter NOT executing → investigate filter registration

### Phase 2: Inspect SecurityContextHolder

**Action:**
1. Create diagnostic test that directly inspects security context:
```kotlin
@Autowired
private lateinit var securityContext: SecurityContext

test("DIAGNOSTIC: Inspect SecurityContextHolder during request") {
    val jwt = KeycloakTestContainer.generateToken("admin", "password")
    val headers = HttpHeaders().apply { setBearerAuth(jwt) }

    // Make request that populates SecurityContextHolder
    restTemplate.exchange("http://localhost:$port/test/tenant-info", ...)

    // Immediately after, check what's in SecurityContextHolder
    // (Note: This won't work as SecurityContextHolder is ThreadLocal)
}
```

2. Alternative: Create higher-priority filter that logs authentication:
```kotlin
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)  // Before TenantContextFilter
class DiagnosticSecurityFilter : Filter {
    override fun doFilter(...) {
        val auth = SecurityContextHolder.getContext().authentication
        logger.info("DIAGNOSTIC Filter - Auth: ${auth?.javaClass?.name}")
        logger.info("DIAGNOSTIC Filter - Principal: ${auth?.principal}")
        chain.doFilter(request, response)
    }
}
```

### Phase 3: Minimal Reproducible Test

**Action:**
1. Create absolute minimal test following Epic 3 pattern:
```kotlin
@SpringBootTest(classes = [SecurityTestApplication::class])  // Use Epic 3 app!
@ActiveProfiles("keycloak-test")
class MinimalTenantTest : FunSpec() {
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var tenantContextFilter: TenantContextFilter

    test("minimal tenant extraction test") {
        val jwt = KeycloakTestContainer.generateToken("admin", "password")

        mockMvc.perform(
            get("/api/widgets").header("Authorization", "Bearer $jwt")
        ).andExpect(status().isOk())

        // Check if filter executed (via metrics or logs)
    }
}
```

2. If this PASSES: Problem is in MultiTenancyTestApplication
3. If this FAILS: Problem is deeper (filter registration or security config)

### Phase 4: Compare Bean Definitions

**Action:**
1. Add test that lists all beans:
```kotlin
@Autowired
private lateinit var applicationContext: ApplicationContext

test("List all Filter beans") {
    val filterBeans = applicationContext.getBeansOfType(Filter::class.java)
    filterBeans.forEach { (name, bean) ->
        println("Filter: $name -> ${bean::class.simpleName}")
    }

    val securityBeans = applicationContext.getBeansOfType(SecurityFilterChain::class.java)
    securityBeans.forEach { (name, bean) ->
        println("SecurityFilterChain: $name")
    }
}
```

2. Verify TenantContextFilter is in the list
3. Verify SecurityFilterChain exists
4. Check for duplicate or conflicting beans

---

## Key Files to Examine

**Primary:**
1. `framework/multi-tenancy/src/integration-test/kotlin/.../TenantContextFilterIntegrationTest.kt`
2. `framework/multi-tenancy/src/integration-test/kotlin/.../test/MultiTenancyTestApplication.kt`
3. `framework/multi-tenancy/src/main/kotlin/.../TenantContextFilter.kt`
4. `framework/security/src/main/kotlin/.../SecurityConfiguration.kt`

**Reference (Working):**
5. `framework/security/src/integration-test/kotlin/.../KeycloakIntegrationTest.kt`
6. `framework/security/src/integration-test/kotlin/.../SecurityTestApplication.kt`
7. `framework/security/src/integration-test/kotlin/.../TestController.kt`

**Configuration:**
8. `framework/multi-tenancy/src/integration-test/resources/application-keycloak-test.yml`
9. `framework/security/src/integration-test/resources/application-keycloak-test.yml`
10. `shared/testing/src/main/resources/keycloak/realm-export.json`
11. `framework/multi-tenancy/build.gradle.kts`

---

## Best Practices to Follow

### Spring Boot Integration Testing

1. **Use Real HTTP Server for Filter Tests**
   - `webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT`
   - TestRestTemplate or WebTestClient
   - Full servlet container startup

2. **Minimal Test Application**
   - Only necessary beans
   - Avoid shadowing auto-configured beans
   - Use @TestConfiguration for overrides

3. **Profile Usage**
   - Positive profile declarations: `@Profile("keycloak-test")`
   - Avoid negative conditions: `@Profile("!test")` (fragile)
   - Document profile purposes clearly

4. **Testcontainers**
   - Singleton containers with `.withReuse(true)`
   - @DynamicPropertySource for runtime configuration
   - Proper health checks and startup timeouts

5. **Security Testing**
   - Real JWT tokens from Keycloak (not mocks)
   - SecurityContextHolder inspection for debugging
   - Verify filter chain order with logging

### Kotlin/Spring Boot Specifics

1. **CGLIB Proxies**
   - @Configuration classes must be `open`
   - @Bean methods must be `open`
   - @Component classes with injected beans should be `open`

2. **Kotest + Spring**
   - `extension(SpringExtension())` in init block
   - `@Autowired lateinit var` field injection
   - NOT constructor injection (causes issues)

---

## Debugging Output to Capture

When implementing solution, capture:

1. **Profile Activation:**
   ```
   Active profiles: [keycloak-test]
   Negative profile conditions: !(test | rbac-test) = TRUE/FALSE
   ```

2. **Bean Registration:**
   ```
   TenantContextFilter bean: REGISTERED
   SecurityConfiguration bean: REGISTERED/NOT FOUND
   SecurityFilterChain bean: REGISTERED/NOT FOUND
   ```

3. **Filter Chain:**
   ```
   Filter[0]: org.springframework.security.web...
   Filter[1]: com.axians.eaf.framework.multitenancy.TenantContextFilter
   Filter[2]: ...
   ```

4. **Authentication Type:**
   ```
   During /test/tenant-info request:
   SecurityContextHolder.authentication: JwtAuthenticationToken/NULL/Other
   Principal: <username>
   JWT Claims: {tenant_id: tenant-test-001, ...}
   ```

5. **Filter Execution:**
   ```
   TenantContextFilter.doFilter() ENTRY
   Authentication type: JwtAuthenticationToken
   tenant_id claim: tenant-test-001
   TenantContext.setCurrentTenantId("tenant-test-001") CALLED
   TenantContext.current(): tenant-test-001
   ```

---

## Success Metrics

**Test must show:**
```
✅ Filter bean registered
✅ SecurityFilterChain bean exists
✅ JWT validation active
✅ Filter executes during HTTP request
✅ SecurityContextHolder contains JwtAuthenticationToken
✅ tenant_id extracted: "tenant-test-001"
✅ TenantContext populated: "tenant-test-001"
✅ Response contains: {"tenantId":"tenant-test-001"}
✅ All 5 integration tests PASS
```

---

## Additional Context

### Project Philosophy

- **Constitutional TDD:** Test-first mandatory
- **No Mocks Policy:** Real dependencies (Testcontainers)
- **Kotest ONLY:** JUnit forbidden
- **Zero-Tolerance:** All tests must pass

### Related Stories

- **Story 4.1:** TenantContext ThreadLocal API (completed, tested)
- **Epic 3:** JWT Validation (10-layer system, all tests passing)
- **Story 4.3:** Axon Tenant Interceptor (next, depends on Story 4.2)

---

## Deliverable

Provide:

1. **Root Cause Analysis:**
   - Exact line/file where problem occurs
   - Why it occurs
   - How it manifests

2. **Complete Solution:**
   - All code changes needed
   - Configuration updates
   - Test modifications

3. **Verification:**
   - How to verify fix locally
   - Expected test output
   - Regression check strategy

4. **Prevention:**
   - Best practices to avoid similar issues
   - Recommended patterns
   - Documentation updates

---

## Notes for External Agent

- Repository is public: https://github.com/acita-gmbh/eaf
- All code in Kotlin (not Java)
- Hexagonal architecture with Spring Modulith
- Multi-tenancy is core requirement
- Integration tests must use real Keycloak JWTs (no mocking)
- This is Layer 1 of 3-layer tenant isolation (Layers 2-3 come in Stories 4.3-4.4)

---

## Complete Code Listings

### Current TenantContextFilter Implementation (FULL)

```kotlin
package com.axians.eaf.framework.multitenancy

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class TenantContextFilter(
    private val meterRegistry: MeterRegistry,
    private val objectMapper: ObjectMapper,
) : Filter {
    private val extractionTimer: Timer =
        Timer.builder("tenant_context_extraction_duration")
            .description("Time taken to extract and populate tenant context from JWT")
            .tags("layer", "1-jwt-extraction")
            .register(meterRegistry)

    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain,
    ) {
        val httpResponse = response as HttpServletResponse
        val sample = Timer.start(meterRegistry)

        try {
            val authentication = SecurityContextHolder.getContext().authentication

            // DEBUG LOGGING
            println("🔍 TenantContextFilter - Authentication type: ${authentication?.javaClass?.simpleName ?: "NULL"}")

            if (authentication !is JwtAuthenticationToken) {
                println("⚠️ Non-JWT authentication - skipping tenant extraction")
                chain.doFilter(request, response)
                return  // ← PROBLEM: This early return is executed!
            }

            println("✅ JWT Authentication found - proceeding with tenant extraction")

            val jwt = authentication.token
            val tenantId = jwt?.getClaimAsString("tenant_id")

            val error = validateTenantId(tenantId, httpResponse)
            if (error != null) {
                return
            }

            TenantContext.setCurrentTenantId(tenantId!!)
            chain.doFilter(request, response)
        } catch (ex: Exception) {
            meterRegistry.counter("tenant_context_extraction_errors", ...).increment()
            throw ex
        } finally {
            TenantContext.clearCurrentTenant()
            sample.stop(extractionTimer)
        }
    }

    @Suppress("ReturnCount")
    private fun validateTenantId(
        tenantId: String?,
        httpResponse: HttpServletResponse,
    ): String? {
        if (tenantId.isNullOrBlank()) {
            meterRegistry.counter("missing_tenant_failures", "reason", "missing_claim").increment()
            writeErrorResponse(httpResponse, "Missing required tenant context", 400)
            return "missing"
        }

        try {
            TenantId(tenantId)  // Domain validation
        } catch (ex: IllegalArgumentException) {
            meterRegistry.counter("missing_tenant_failures", "reason", "invalid_format").increment()
            writeErrorResponse(httpResponse, "Invalid tenant context format", 400)
            return "invalid"
        }

        return null
    }

    private fun writeErrorResponse(
        httpResponse: HttpServletResponse,
        errorMessage: String,
        status: Int,
    ) {
        httpResponse.status = status
        httpResponse.contentType = "application/json"
        objectMapper.writeValue(
            httpResponse.writer,
            ErrorResponse(error = errorMessage, status = status),
        )
    }

    private data class ErrorResponse(
        val error: String,
        val status: Int,
    )
}
```

### Current Integration Test (FULL)

```kotlin
package com.axians.eaf.framework.multitenancy

import com.axians.eaf.framework.multitenancy.test.MultiTenancyTestApplication
import com.axians.eaf.testing.keycloak.KeycloakTestContainer
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(
    classes = [MultiTenancyTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=\${eaf.security.jwt.issuer-uri}",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=\${eaf.security.jwt.jwks-uri}",
        "eaf.security.jwt.audience=eaf-api",
        "eaf.security.role-whitelist=WIDGET_ADMIN,WIDGET_VIEWER,ADMIN",
        "logging.level.com.axians.eaf=DEBUG",
        "spring.modulith.events.jdbc.schema-initialization.enabled=false",
    ],
)
@ActiveProfiles("keycloak-test")
class TenantContextFilterIntegrationTest : FunSpec() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var tenantContextFilter: TenantContextFilter

    init {
        extension(SpringExtension())

        test("DIAGNOSTIC: Verify TenantContextFilter bean loaded") {
            tenantContextFilter shouldBe instanceOf<TenantContextFilter>()
            // ✅ PASSES
        }

        test("DIAGNOSTIC: Verify JWT contains tenant_id claim") {
            val jwt = KeycloakTestContainer.generateToken("admin", "password")
            val payload = String(java.util.Base64.getUrlDecoder().decode(jwt.split(".")[1]))
            payload shouldContain "tenant_id"
            payload shouldContain "tenant-test-001"
            // ✅ PASSES
        }

        test("AC2+AC3: Extract tenant_id from JWT and populate TenantContext") {
            val expectedTenant = "tenant-test-001"
            val jwt = KeycloakTestContainer.generateToken("admin", "password")

            val headers = HttpHeaders().apply { setBearerAuth(jwt) }
            val response = restTemplate.exchange(
                "http://localhost:$port/test/tenant-info",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                String::class.java,
            )

            response.statusCode shouldBe HttpStatus.OK
            response.body shouldContain expectedTenant
            // ❌ FAILS: response.body = "{"tenantId":"MISSING","message":"Tenant context: null"}"
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            KeycloakTestContainer.start()
            registry.add("eaf.security.jwt.issuer-uri") {
                KeycloakTestContainer.getIssuerUri()
            }
            registry.add("eaf.security.jwt.jwks-uri") {
                KeycloakTestContainer.getJwksUri()
            }
        }
    }
}
```

---

## Advanced Debugging Techniques

### Technique 1: Spring Boot Actuator Beans Endpoint

**Add to test properties:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: beans,conditions,configprops
```

**Then create diagnostic test:**
```kotlin
test("DIAGNOSTIC: List all registered beans") {
    val beansJson = restTemplate.getForObject(
        "http://localhost:$port/actuator/beans",
        String::class.java
    )
    println("=== ALL BEANS ===")
    println(beansJson)

    // Check for:
    // - tenantContextFilter
    // - securityFilterChain
    // - jwtDecoder
}
```

### Technique 2: Filter Registration Verification

```kotlin
@Autowired
private lateinit var servletContext: ServletContext

test("DIAGNOSTIC: Verify filter registered in ServletContext") {
    val filterRegistrations = servletContext.filterRegistrations
    filterRegistrations.forEach { (name, registration) ->
        println("Filter: $name -> ${registration.className}")
        println("  URL Patterns: ${registration.urlPatternMappings}")
        println("  Servlet Names: ${registration.servletNameMappings}")
    }

    // Verify TenantContextFilter is in the list
    filterRegistrations.keys shouldContain "tenantContextFilter"
}
```

### Technique 3: SecurityContextHolder Debugging

**Create higher-priority diagnostic filter:**
```kotlin
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)  // Before TenantContextFilter
class SecurityContextDiagnosticFilter : Filter {
    private val logger = LoggerFactory.getLogger(SecurityContextDiagnosticFilter::class.java)

    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain,
    ) {
        val req = request as HttpServletRequest
        logger.info("=== DIAGNOSTIC FILTER (Order +5) ===")
        logger.info("Request URI: ${req.requestURI}")
        logger.info("Authorization Header: ${req.getHeader("Authorization")?.take(50)}...")

        val auth = SecurityContextHolder.getContext().authentication
        logger.info("Authentication: ${auth?.javaClass?.name ?: "NULL"}")
        logger.info("Principal: ${auth?.principal}")
        logger.info("Is JwtAuthenticationToken: ${auth is JwtAuthenticationToken}")

        if (auth is JwtAuthenticationToken) {
            logger.info("JWT Token present: ${auth.token.tokenValue.take(50)}...")
            logger.info("JWT Claims: ${auth.token.claims}")
        }

        chain.doFilter(request, response)
    }
}
```

### Technique 4: ApplicationContext Inspection

```kotlin
@Autowired
private lateinit var applicationContext: ApplicationContext

test("DIAGNOSTIC: Inspect application context") {
    // List all Filter beans
    val filters = applicationContext.getBeansOfType(Filter::class.java)
    println("=== FILTER BEANS ===")
    filters.forEach { (name, bean) ->
        println("$name: ${bean::class.qualifiedName}")
        if (bean is Ordered) {
            println("  Order: ${bean.order}")
        }
    }

    // List security beans
    println("=== SECURITY BEANS ===")
    val securityChains = applicationContext.getBeansOfType(SecurityFilterChain::class.java)
    println("SecurityFilterChain beans: ${securityChains.keys}")

    val jwtDecoders = applicationContext.getBeansOfType(JwtDecoder::class.java)
    println("JwtDecoder beans: ${jwtDecoders.keys}")

    // Check profiles
    val environment = applicationContext.environment
    println("=== ACTIVE PROFILES ===")
    println("Active: ${environment.activeProfiles.toList()}")
    println("Default: ${environment.defaultProfiles.toList()}")
}
```

### Technique 5: Request-Response Logging Interceptor

```kotlin
@Configuration
class RequestLoggingConfig {
    @Bean
    fun logFilter(): FilterRegistrationBean<CommonsRequestLoggingFilter> {
        val filter = CommonsRequestLoggingFilter()
        filter.setIncludeQueryString(true)
        filter.setIncludePayload(false)
        filter.setIncludeHeaders(true)
        filter.setIncludeClientInfo(true)
        filter.setMaxPayloadLength(10000)

        val registration = FilterRegistrationBean(filter)
        registration.order = Ordered.HIGHEST_PRECEDENCE + 1  // Before TenantContextFilter
        return registration
    }
}
```

---

## Alternative Solution Approaches

### Approach A: Simplify to Absolute Minimum

**Hypothesis:** MultiTenancyTestApplication is too complex.

**Action:**
1. Remove SimpleMeterRegistry bean (use Spring Boot Actuator instead)
2. Remove ComponentScan for multitenancy (let auto-configuration handle it)
3. Use only SecurityTestApplication from Epic 3

```kotlin
@SpringBootTest(classes = [SecurityTestApplication::class])  // Epic 3 app
@ActiveProfiles("keycloak-test")
class MinimalTenantTest : FunSpec() {
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var tenantContextFilter: TenantContextFilter  // Will fail if not found

    test("verify filter executes") {
        val jwt = KeycloakTestContainer.generateToken("admin", "password")
        mockMvc.perform(
            get("/api/widgets").header("Authorization", "Bearer $jwt")
        ).andExpect(status().isOk())
    }
}
```

### Approach B: Use @WebMvcTest Instead of @SpringBootTest

**Hypothesis:** Full @SpringBootTest has too many side effects.

**Action:**
```kotlin
@WebMvcTest(controllers = [TenantTestController::class])
@Import(
    SecurityConfiguration::class,
    TenantContextFilter::class,
    // ... all required beans
)
@ActiveProfiles("keycloak-test")
class WebMvcSliceTenantTest : FunSpec() {
    @Autowired private lateinit var mockMvc: MockMvc

    // Configure mocks for required dependencies
}
```

### Approach C: Manual Filter Registration

**Hypothesis:** Auto-registration isn't working.

**Action:**
```kotlin
@TestConfiguration
class TestFilterConfiguration {
    @Bean
    fun tenantFilterRegistration(
        tenantContextFilter: TenantContextFilter
    ): FilterRegistrationBean<TenantContextFilter> {
        val registration = FilterRegistrationBean(tenantContextFilter)
        registration.order = Ordered.HIGHEST_PRECEDENCE + 10
        registration.addUrlPatterns("/*")
        return registration
    }
}
```

### Approach D: Use Spring Security Test Utilities

**Hypothesis:** SecurityContextHolder needs explicit setup in tests.

**Action:**
```kotlin
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt

test("AC2+AC3 with Spring Security test utilities") {
    mockMvc.perform(
        get("/test/tenant-info")
            .with(
                jwt().jwt { jwt ->
                    jwt.claim("tenant_id", "tenant-test-001")
                    jwt.claim("sub", "admin")
                    // ...
                }
            )
    ).andExpect(status().isOk())
}
```

---

## Deep Dive Questions

### Question 1: Filter Registration Lifecycle

**Research:**
- How does Spring Boot register @Component Filter beans?
- When does registration happen (startup vs test initialization)?
- Does @Order annotation work in tests?
- Is there a difference between @Component and FilterRegistrationBean?

**Verify:**
```kotlin
test("When does filter registration happen?") {
    val filterRegistrations = servletContext.filterRegistrations
    // Check if tenantContextFilter is registered
    // Check order value
    // Check URL patterns
}
```

### Question 2: SecurityContextHolder Population

**Research:**
- When does Spring Security populate SecurityContextHolder?
- Which filter does this (BearerTokenAuthenticationFilter)?
- Does it happen in @SpringBootTest with RANDOM_PORT?
- Is JwtAuthenticationToken created automatically?

**Verify:**
```kotlin
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)  // AFTER TenantContextFilter
class PostTenantContextDiagnosticFilter : Filter {
    override fun doFilter(...) {
        // This runs AFTER TenantContextFilter
        println("After TenantContextFilter - TenantContext: ${TenantContext.current()}")
        chain.doFilter(request, response)
    }
}
```

### Question 3: Profile Activation Mechanism

**Research:**
- Which profiles are active during test execution?
- Does @SpringBootTest implicitly activate "test" profile?
- How to explicitly prevent "test" profile activation?

**Verify:**
```kotlin
@Autowired
private lateinit var environment: Environment

test("DIAGNOSTIC: Check active profiles") {
    println("Active profiles: ${environment.activeProfiles.toList()}")
    println("Default profiles: ${environment.defaultProfiles.toList()}")

    val testProfileActive = environment.acceptsProfiles(Profiles.of("test"))
    val keycloakTestActive = environment.acceptsProfiles(Profiles.of("keycloak-test"))

    println("'test' profile active: $testProfileActive")
    println("'keycloak-test' profile active: $keycloakTestActive")
}
```

### Question 4: Bean Definition Conflicts

**Research:**
- Are there multiple MeterRegistry beans?
- Is SimpleMeterRegistry shadowing auto-configured MeterRegistry?
- Bean definition ordering issues?

**Verify:**
```kotlin
@Autowired
private lateinit var applicationContext: ApplicationContext

test("DIAGNOSTIC: Check for bean conflicts") {
    val meterRegistries = applicationContext.getBeansOfType(MeterRegistry::class.java)
    println("MeterRegistry beans: ${meterRegistries.keys}")
    meterRegistries.forEach { (name, bean) ->
        println("  $name: ${bean::class.simpleName}")
        if (bean is CompositeMeterRegistry) {
            println("    Composite with ${bean.registries.size} registries")
        }
    }
}
```

---

## Comparison with Working Epic 3 Tests

### Side-by-Side Comparison

| Feature | Epic 3 (WORKS) | Story 4.2 (FAILS) |
|---------|----------------|-------------------|
| **Test Class** | `KeycloakIntegrationTest` | `TenantContextFilterIntegrationTest` |
| **Test App** | `SecurityTestApplication` | `MultiTenancyTestApplication` |
| **ComponentScan** | `["...security"]` | `["...multitenancy", "...security"]` |
| **Custom Beans** | None | `SimpleMeterRegistry` |
| **HTTP Client** | MockMvc | TestRestTemplate |
| **webEnvironment** | Default (MOCK) | RANDOM_PORT |
| **@AutoConfigureMockMvc** | Yes (default) | No (using TestRestTemplate) |
| **Profile** | `keycloak-test` | `keycloak-test` |
| **Endpoint** | `/api/widgets` | `/test/tenant-info` |
| **Controller** | `TestController` | `TenantTestController` |
| **Controller @Profile** | None | None (removed) |
| **Security** | Validates JWT ✅ | Should validate JWT but doesn't? |

**Key Differences to Investigate:**
1. Multi-package ComponentScan
2. SimpleMeterRegistry bean
3. TestRestTemplate vs MockMvc
4. Different endpoint paths

---

## Gradle Build Configuration

```kotlin
// framework/multi-tenancy/build.gradle.kts
plugins {
    id("eaf.kotlin-common")
    id("eaf.testing-v2")
    id("eaf.quality-gates")
}

dependencies {
    implementation(project(":framework:core"))
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.arrow)

    // Spring Framework dependencies
    implementation(libs.spring.boot.starter.web)
    implementation(libs.jakarta.servlet.api)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.micrometer.core)

    // Spring Modulith
    implementation(libs.spring.modulith.api)
    testImplementation(libs.spring.modulith.test)

    // Testing dependencies
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(project(":shared:testing"))

    // Integration test dependencies
    integrationTestImplementation(project(":framework:security"))  // Story 4.2
}
```

**Convention Plugins Applied:**
- `eaf.kotlin-common` - Kotlin compilation
- `eaf.testing-v2` - Creates integrationTest source set
- `eaf.quality-gates` - ktlint, detekt, Konsist

---

## Research Resources

### Spring Boot Documentation

1. **Filter Registration:**
   - https://docs.spring.io/spring-boot/reference/web/servlet.html#web.servlet.embedded-container.servlets-filters-listeners
   - Auto-registration of @Component Filter beans
   - FilterRegistrationBean for explicit control
   - Filter ordering with @Order

2. **Testing:**
   - https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html
   - @SpringBootTest webEnvironment options
   - TestRestTemplate usage
   - Integration testing best practices

3. **Security Testing:**
   - https://docs.spring.io/spring-security/reference/servlet/test/index.html
   - SecurityMockMvcRequestPostProcessors
   - @WithMockUser and @WithJwtToken
   - Testing filter chains

### Testcontainers

1. **Keycloak Module:**
   - https://java.testcontainers.org/modules/keycloak/
   - dasniko/testcontainers-keycloak documentation
   - Realm import configuration

2. **Spring Boot Integration:**
   - @ServiceConnection annotation
   - Dynamic property sources
   - Container reuse strategies

### Kotlin/Spring Integration

1. **CGLIB Proxies:**
   - Kotlin classes are final by default
   - `open` keyword required for @Configuration and @Bean
   - Spring Framework Kotlin support documentation

2. **Kotest Spring Extension:**
   - SpringExtension usage
   - @Autowired in Kotest specs
   - FunSpec lifecycle integration

---

## Potential Root Causes (Ranked by Likelihood)

### 1. Filter Not Registered in Servlet Container (HIGH)

**Evidence:**
- No debug logs from filter
- Bean exists but may not be in filter chain
- @Component should auto-register but may not in tests

**Investigation:**
- Check ServletContext.filterRegistrations
- Verify FilterRegistrationBean not needed
- Test with explicit FilterRegistrationBean

**Expected Finding:**
Filter is registered as bean but NOT in servlet filter chain.

### 2. SecurityContextHolder Not Populated (HIGH)

**Evidence:**
- authentication !is JwtAuthenticationToken → early return
- No JWT authentication logs
- Request succeeds (200 OK) but no security context

**Investigation:**
- Check if BearerTokenAuthenticationFilter is in chain
- Verify JwtDecoder bean exists and is used
- Test SecurityContextHolder directly

**Expected Finding:**
SecurityContextHolder.authentication is NULL or AnonymousAuthenticationToken.

### 3. Profile/Configuration Mismatch (MEDIUM)

**Evidence:**
- @Profile removed but still fails
- "test" profile may still be implicitly active
- SecurityConfiguration may not load

**Investigation:**
- Log active profiles during test
- Check @Conditional* annotations
- Verify SecurityConfiguration bean creation

**Expected Finding:**
SecurityConfiguration bean not created despite @Profile removal.

### 4. Bean Definition Override (MEDIUM)

**Evidence:**
- SimpleMeterRegistry may shadow other beans
- Multiple beans of same type
- ComponentScan order effects

**Investigation:**
- Remove SimpleMeterRegistry, use Actuator
- Check for @Primary conflicts
- Verify bean autowiring resolution

**Expected Finding:**
Bean conflict preventing proper initialization.

### 5. Kotlin/Spring CGLIB Issue (LOW)

**Evidence:**
- All classes and methods are `open`
- Follows Kotlin best practices
- Unit tests work fine

**Investigation:**
- Verify no final methods blocking proxies
- Check for Kotlin data classes as beans

**Expected Finding:**
Unlikely - CGLIB proxies seem fine.

---

## Testing Checklist

Before concluding investigation, verify:

- [ ] Filter bean exists (`@Autowired` succeeds)
- [ ] Filter registered in ServletContext
- [ ] Filter order correct (HIGHEST_PRECEDENCE + 10)
- [ ] SecurityConfiguration bean created
- [ ] SecurityFilterChain bean created
- [ ] JwtDecoder bean created
- [ ] Keycloak container started
- [ ] JWT generated successfully
- [ ] JWT contains tenant_id claim
- [ ] HTTP request reaches controller
- [ ] Authorization header present
- [ ] Spring Security filter chain executes
- [ ] SecurityContextHolder populated
- [ ] Authentication is JwtAuthenticationToken
- [ ] TenantContextFilter executes
- [ ] tenant_id extracted
- [ ] TenantContext populated
- [ ] Test controller retrieves tenant_id

**Failure Point:** Identify EXACT step where flow breaks.

---

## Expected Solution Format

### 1. Root Cause Identification

```markdown
## Root Cause

**Problem:** <exact issue>

**Location:** `<file>:<line>`

**Code:**
```kotlin
<problematic code>
```

**Why it fails:** <explanation>

**Evidence:** <concrete proof>
```

### 2. Complete Fix

```markdown
## Solution

**Changes Required:**

1. **File:** `<path>`
   **Change:** <description>
   ```kotlin
   // Before
   <old code>

   // After
   <new code>
   ```

2. **File:** `<path>`
   ...

**Why this fixes it:** <explanation>
```

### 3. Verification Steps

```markdown
## Verification

**Local Testing:**
```bash
./gradlew :framework:multi-tenancy:integrationTest
```

**Expected Output:**
```
TenantContextFilterIntegrationTest > DIAGNOSTIC: Verify TenantContextFilter bean loaded PASSED
TenantContextFilterIntegrationTest > DIAGNOSTIC: Verify JWT contains tenant_id claim PASSED
TenantContextFilterIntegrationTest > AC2+AC3: Extract tenant_id from JWT and populate TenantContext PASSED
TenantContextFilterIntegrationTest > AC4: Missing tenant_id claim rejects request with 400 Bad Request PASSED
TenantContextFilterIntegrationTest > AC5: ThreadLocal cleanup after request - context cleared PASSED
TenantContextFilterIntegrationTest > AC6: Concurrent requests have isolated tenant contexts PASSED
TenantContextFilterIntegrationTest > AC7: Metrics emitted - tenant_context_extraction_duration timer PASSED

BUILD SUCCESSFUL
```

**Regression Check:**
```bash
./gradlew :framework:security:integrationTest
```

All Epic 3 security tests should still pass.
```

### 4. Prevention Recommendations

```markdown
## Prevention

1. <best practice>
2. <pattern to follow>
3. <anti-pattern to avoid>

## Documentation Updates

- Update CLAUDE.md with integration test patterns
- Document profile usage guidelines
- Add filter testing best practices
```

---

## Success Criteria

**MUST ACHIEVE:**

1. ✅ All 7 integration tests PASS (including diagnostics)
2. ✅ TenantContext populated with correct tenant_id from JWT
3. ✅ No regressions in Epic 3 security tests
4. ✅ Solution follows Spring Boot + Kotlin best practices
5. ✅ Clear explanation of root cause and why fix works

**DELIVERABLES:**

1. Root cause analysis with concrete evidence
2. Complete code changes (all files)
3. Explanation of why previous 19 fixes didn't work
4. Verification that solution addresses actual problem
5. Prevention strategy for similar issues

---

**OBJECTIVE:** Solve this integration test failure with a production-quality solution that follows Spring Boot and Kotlin best practices, ensuring ALL acceptance criteria are met through passing integration tests. The solution should be simple, maintainable, and aligned with established Epic 3 patterns.
