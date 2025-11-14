# Deep Research Prompt: Spring Security @PreAuthorize Testing with MockMvc + Testcontainers

## Problem Statement

**Objective:** Configure Spring Boot integration tests (Kotest + MockMvc + Testcontainers) to properly test `@PreAuthorize` role-based access control with full CQRS/jOOQ stack.

## Latest Status (After 2.5h Investigation)

**CRITICAL DISCOVERIES:**
1. ✅ **Root cause identified:** `permitAll()` bypasses `ExceptionTranslationFilter` (Web research + Spring Security docs)
2. ✅ **Solution identified:** Use `authenticated()` + MockMvc `.apply(springSecurity())`
3. ✅ **Profile isolation fixed:** `@Profile("!(test | rbac-test)")` (NOT `@Profile("!test", "!rbac-test")` - wrong syntax!)

**IMPLEMENTED FIXES:**
✅ Changed `permitAll()` → `authenticated()` in RbacTestSecurityConfig
✅ Added `@AutoConfigureMockMvc(addFilters = true)` to enable security filters
✅ Fixed SecurityConfiguration profile: `@Profile("!(test | rbac-test)")`
✅ Created separate "rbac-test" profile with RbacTestSecurityConfig
✅ Extended TestDslConfiguration, TestJpaBypassConfiguration, AxonTestConfiguration to support "rbac-test" profile

**CURRENT BLOCKER:**
❌ **Testcontainers JDBC Connection Timing Issue**
```
io.kotest.engine.extensions.ExtensionException$BeforeAnyException:
  org.springframework.transaction.CannotCreateTransactionException:
    Could not open JDBC Connection for transaction
```

**Analysis:**
- Spring Security filters load correctly (ExceptionTranslationFilter present)
- @Profile isolation works (no more bean conflicts)
- @Sql("/schema.sql") executes BEFORE Testcontainers PostgreSQL is ready
- TestDslConfiguration creates manual DataSource from Testcontainers JDBC URL
- All 7 RBAC tests fail with identical JDBC error
- Existing "test" profile tests (without @EnableMethodSecurity) work fine

## Technology Stack

- **Spring Boot:** 3.5.7
- **Spring Security:** 6.5.5
- **Kotlin:** 2.2.21
- **Test Framework:** Kotest 6.0.4
- **Test Tools:** MockMvc, Spring Security Test Support (spring-security-test 6.5.5)
- **Architecture:** Hexagonal Architecture, CQRS/ES with Axon Framework, jOOQ for projections

## Current Situation

### What Works ✅
1. Production code with @PreAuthorize annotations compiles successfully
2. Keycloak realm configured with WIDGET_ADMIN and WIDGET_VIEWER roles
3. Tests with CORRECT roles pass (e.g., WIDGET_ADMIN can create widgets)
4. Test infrastructure compiles without errors

### What Fails ❌
1. Tests expecting **403 Forbidden** receive **500 Internal Server Error**
2. AccessDeniedException is NOT caught by Spring Security's exception translation
3. @ControllerAdvice with @ExceptionHandler(AccessDeniedException.class) is NOT invoked
4. Tests show: `Status expected:<403> but was:<500>`

## Root Cause (From Web Research)

**Critical Discovery:** When using `authorizeHttpRequests { auth -> auth.anyRequest().permitAll() }` in test security configuration:
- Spring Security's `ExceptionTranslationFilter` is **NOT added** to the filter chain
- Method-level `@PreAuthorize` exceptions bypass Security's exception handling
- AccessDeniedException propagates as uncaught application exception → 500

**Sources:**
- Stack Overflow: "Spring MVC AccessDeniedException 500 error received instead of custom 401 error for @PreAuthorized unauth requests"
- GitHub Issue #15254: "Endpoint returns a 500, instead of 403 status code when the user does not have required permission"

## Test Configuration Details

### Current Test Security Config (rbac-test profile)
```kotlin
@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Profile("rbac-test")
@Import(TestDslConfiguration::class, TestJpaBypassConfiguration::class)
open class RbacTestSecurityConfig {
    @Bean
    @Primary
    fun rbacTestSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth.anyRequest().authenticated()  // Changed from permitAll()
            }.oauth2ResourceServer { oauth2 ->
                oauth2.jwt { }
            }.build()

    @Bean
    @Primary
    open fun rbacTestJwtDecoder(): JwtDecoder {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()
        val publicKey = keyPair.public as RSAPublicKey
        return NimbusJwtDecoder.withPublicKey(publicKey).build()
    }

    @Bean
    @Primary
    open fun tokenRevocationStore(): TokenRevocationStore = /* mock implementation */
}

@ControllerAdvice
@Profile("rbac-test")
class RbacTestAccessDeniedAdvice {
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.message ?: "Access Denied")
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problemDetail)
    }
}
```

### Test Class Setup
```kotlin
@Testcontainers
@SpringBootTest(
    classes = [
        WidgetDemoApplication::class,
        RbacTestSecurityConfig::class,
        ProblemDetailExceptionHandler::class,
    ],
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.mvc.problemdetails.enabled=true",
        TestAutoConfigurationOverrides.DISABLE_MODULITH_JPA,
        "spring.autoconfigure.exclude=..." // Various excludes
    ],
)
@Import(AxonTestConfiguration::class, RbacTestAccessDeniedAdvice::class)
@Sql("/schema.sql")
@ActiveProfiles("rbac-test")
@AutoConfigureMockMvc
class WidgetControllerRbacIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    init {
        extension(SpringExtension())

        test("WIDGET_VIEWER cannot create widget") {
            mockMvc
                .post("/api/v1/widgets") {
                    with(SecurityMockMvcRequestPostProcessors.jwt()
                        .authorities(SimpleGrantedAuthority("ROLE_WIDGET_VIEWER")))
                    contentType = MediaType.APPLICATION_JSON
                    content = requestBody
                }.andExpect {
                    status { isForbidden() }  // Expects 403, gets 500
                }
        }
    }
}
```

### Controller with @PreAuthorize
```kotlin
@RestController
@RequestMapping("/api/v1/widgets")
class WidgetController(
    private val commandGateway: CommandGateway,
    private val queryGateway: QueryGateway,
) {
    @PostMapping
    @PreAuthorize("hasRole('WIDGET_ADMIN')")
    fun createWidget(
        @Valid @RequestBody request: CreateWidgetRequest,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ): WidgetResponse {
        // Implementation...
    }
}
```

## Attempted Solutions (All Failed)

1. **AccessDeniedHandler in SecurityFilterChain** - NOT invoked
2. **@ControllerAdvice with @ExceptionHandler** - NOT invoked
3. **Separate "rbac-test" profile** - Bean conflicts with production SecurityConfiguration
4. **Exclude SecurityConfiguration via spring.autoconfigure.exclude** - Error: "not auto-configuration classes"
5. **Changed permitAll() → authenticated()** - Unknown result (latest attempt)
6. **@Primary on test beans** - Bean conflicts persist
7. **Multiple @Profile combinations** - Configuration conflicts

## Complex Constraints

### Must Support (Already Working)
- **Existing "test" profile tests** - Use `TestSecurityConfig` with `permitAll()`, NO @EnableMethodSecurity
- **jOOQ + Axon + PostgreSQL Testcontainers** - Full CQRS stack with projections
- **Spring Modulith Events disabled** - Using `TestAutoConfigurationOverrides.DISABLE_MODULITH_JPA`
- **Kotest FunSpec** - NOT JUnit (different lifecycle)

### Test Configuration Dependencies
```kotlin
// Required for all integration tests:
@Import(AxonTestConfiguration::class)  // Axon + PropagatingErrorHandler
@Sql("/schema.sql")  // Database schema
TestDslConfiguration  // jOOQ DSLContext bean
TestJpaBypassConfiguration  // Removes Modulith JPA beans
@Profile("test") or @Profile("rbac-test")
```

### Bean Conflicts
- Production `SecurityConfiguration` has `@Profile("!test")` → active for "rbac-test"!
- `TokenRevocationStore`: Test mock vs Production Redis bean conflict
- Multiple `SecurityFilterChain` beans cause conflicts

## Research Questions

### Primary Question
**How do you configure Spring Security for MockMvc integration tests to:**
1. Enable `@EnableMethodSecurity` for `@PreAuthorize` enforcement
2. Use Spring Security Test Support mock JWTs (`.with(jwt().authorities(...))`)
3. Properly translate AccessDeniedException → 403 Forbidden (not 500)
4. NOT break existing tests that use `permitAll()` and no authentication

### Specific Technical Questions

1. **ExceptionTranslationFilter Activation:**
   - Does `authenticated()` guarantee ExceptionTranslationFilter is added?
   - Can we have ExceptionTranslationFilter with `permitAll()`?
   - Is there a hybrid approach (some paths authenticated, others permit)?

2. **@ControllerAdvice vs Filter-Level Exception Handling:**
   - Why doesn't `@ControllerAdvice` with `@ExceptionHandler(AccessDeniedException)` work?
   - Does AccessDeniedException from `@PreAuthorize` bypass @ControllerAdvice?
   - Should AccessDeniedHandler be in SecurityFilterChain or separate?

3. **MockMvc Configuration:**
   - Does MockMvc need `.apply(SecurityMockMvcConfigurers.springSecurity())`?
   - Are there Kotest-specific considerations vs JUnit?
   - Does `@AutoConfigureMockMvc` fully configure security filters?

4. **Profile Isolation:**
   - How to prevent production `SecurityConfiguration` (@Profile("!test")) from activating for "rbac-test"?
   - Should we use `@ConditionalOnMissingBean` instead of @Profile?
   - Can we have multiple SecurityFilterChain beans without conflicts?

5. **Spring Boot 3.5.7 / Spring Security 6.5.5 Specifics:**
   - Are there known bugs or breaking changes?
   - Has the exception handling mechanism changed from earlier versions?
   - Are there deprecations affecting ExceptionTranslationFilter?

## Expected Solution Characteristics

The ideal solution should:
1. ✅ Return 403 Forbidden for AccessDeniedException from @PreAuthorize
2. ✅ Work with Kotest + MockMvc + Spring Security Test Support
3. ✅ NOT require changes to existing "test" profile tests
4. ✅ Support full CQRS stack (Axon + jOOQ + Testcontainers)
5. ✅ Use separate "rbac-test" profile OR extend "test" profile safely
6. ✅ Follow Spring Boot 3.x / Spring Security 6.x best practices

## Reference Implementations Needed

Please provide:
1. Complete working `@TestConfiguration` for RBAC tests with @PreAuthorize
2. Proper MockMvc setup (standalone vs @AutoConfigureMockMvc)
3. Exception handling configuration (filter-level vs @ControllerAdvice)
4. Profile isolation strategy to avoid bean conflicts
5. Any Kotlin-specific or Kotest-specific considerations

## Additional Context

### Production Security Configuration (For Reference)
```kotlin
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Profile("!test")  // NOT active for "test", but IS active for "rbac-test"!
class SecurityConfiguration {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers("/actuator/health").permitAll()
                auth.anyRequest().authenticated()
            }.oauth2ResourceServer { oauth2 ->
                oauth2.jwt { }
            }.build()
}
```

### Error Message
```
java.lang.AssertionError: Status expected:<403> but was:<500>

Response body:
{
  "type":"https://eaf.axians.com/errors/internal-error",
  "title":"Internal Server Error",
  "status":500,
  "detail":"An unexpected error occurred",
  "instance":"/api/v1/widgets",
  "traceId":null,
  "tenantId":null
}
```

## Success Criteria

A solution is successful when:
1. RBAC tests pass with 403 Forbidden for insufficient permissions
2. Existing integration tests (32 tests) continue to pass
3. Configuration is maintainable and follows Spring Boot conventions
4. No bean conflicts or circular dependencies
5. ExceptionTranslationFilter is properly activated

## File Locations (For Reference)

- Test Config: `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/test/config/RbacTestSecurityConfig.kt`
- Test Class: `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/api/WidgetControllerRbacIntegrationTest.kt`
- Controller: `products/widget-demo/src/main/kotlin/com/axians/eaf/products/widget/api/WidgetController.kt`
- Production Security: `framework/security/src/main/kotlin/com/axians/eaf/framework/security/config/SecurityConfiguration.kt`
- Existing Test Config: `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/test/config/TestSecurityConfig.kt`

## Request

Please provide a **complete, working solution** that addresses the 403 vs 500 issue while respecting all constraints. Include:
1. Step-by-step configuration changes
2. Explanation of why each change is necessary
3. How ExceptionTranslationFilter is activated
4. Any Spring Security 6.x-specific considerations
5. Validation that existing tests won't break

**Format:** Provide ready-to-implement Kotlin code with detailed comments explaining the mechanism.
