# Deep Research Prompt: Testcontainers + @ServiceConnection Fails for "rbac-test" Profile (Works for "test" Profile)

## 🚀 Quick Start for External Agent

**If you have limited time, start here:**

1. **The Mystery:** IDENTICAL Testcontainers setup works for "test" profile, fails for "rbac-test" profile
2. **The Error:** `Connection to localhost:5432 refused` (NOT Testcontainers dynamic port!)
3. **The Difference:** Only difference is `@EnableMethodSecurity` in security config
4. **The Evidence:** Working test shows `org.testcontainers.jdbc.ConnectionWrapper`, failing test shows `localhost:5432`
5. **The Goal:** Find WHY @ServiceConnection doesn't register properties for "rbac-test" profile

**Jump to:**
- [Side-by-Side Code Comparison](#comparison-working-vs-failing-configuration) - See exact differences
- [Attempted Solutions](#attempted-solutions-all-failed) - What we already tried (all failed!)
- [Debugging Strategy](#debugging-strategy-for-external-agent) - Research roadmap
- [Request](#request) - What we need from you

---

## Problem Statement

**Objective:** Fix Testcontainers PostgreSQL connectivity for Spring Boot integration tests using "rbac-test" profile.

**Critical Discovery:** An IDENTICAL test setup works perfectly for "test" profile but fails with `ConnectException: Connection to localhost:5432 refused` for "rbac-test" profile.

## Context from 5 AI-Agents Analysis

We consulted 5 different AI agents (Perplexity, Claude, ChatGPT, Gemini, Llama) with our original "403 vs 500" problem. **ALL 5 agents recommended:**

1. ✅ `authenticated()` instead of `permitAll()` → Activates ExceptionTranslationFilter
2. ✅ `@Profile("!(test | rbac-test)")` syntax for production config
3. ✅ AccessDeniedHandler in SecurityFilterChain (NOT @ControllerAdvice!)
4. ✅ @DynamicPropertySource + @JvmStatic for Testcontainers
5. ✅ @ServiceConnection (Spring Boot 3.1+) as modern best practice

**We implemented ALL recommendations** - but encountered a NEW blocker: Testcontainers timing issue.

## Technology Stack

- **Spring Boot:** 3.5.7
- **Spring Security:** 6.5.5
- **Kotlin:** 2.2.21
- **Test Framework:** Kotest 6.0.4 (FunSpec)
- **Test Tools:** MockMvc, Spring Security Test Support, Testcontainers 1.20.4
- **Database:** PostgreSQL 16.10-alpine via Testcontainers
- **Architecture:** CQRS/ES with Axon Framework 4.12.1, jOOQ 3.20.8 for projections

## Current Situation

### Working Test: WidgetControllerIntegrationTest (test profile) ✅

```kotlin
@Testcontainers
@SpringBootTest(
    classes = [
        WidgetDemoApplication::class,
        TestSecurityConfig::class,  // @Profile("test"), NO @EnableMethodSecurity
        ProblemDetailExceptionHandler::class,
    ],
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.defer-datasource-initialization=true",
        "spring.mvc.problemdetails.enabled=true",
        TestAutoConfigurationOverrides.DISABLE_MODULITH_JPA,  // Excludes HibernateJpaAutoConfiguration
    ],
)
@Sql("/schema.sql")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class WidgetControllerIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    init {
        extension(SpringExtension())

        test("should create widget") {
            mockMvc.post("/api/v1/widgets") {
                // NO .with(jwt()) - permitAll() security
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"Test"}"""
            }.andExpect {
                status { isCreated() }  // ✅ WORKS!
            }
        }
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgresContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:16.10-alpine"))
                .apply {
                    withDatabaseName("testdb")
                    withUsername("test")
                    withPassword("test")
                    // NO .start() - managed by @Testcontainers + @ServiceConnection
                }
    }
}
```

**Result:** ✅ Tests pass, Testcontainers starts, @Sql executes, database connection works!

### Failing Test: WidgetControllerRbacIntegrationTest (rbac-test profile) ❌

```kotlin
@Testcontainers
@SpringBootTest(
    classes = [
        WidgetDemoApplication::class,
        RbacTestSecurityConfig::class,  // @Profile("rbac-test"), WITH @EnableMethodSecurity!
        ProblemDetailExceptionHandler::class,
    ],
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.defer-datasource-initialization=true",
        "spring.mvc.problemdetails.enabled=true",
        TestAutoConfigurationOverrides.DISABLE_MODULITH_JPA,  // SAME as working test!
    ],
)
@org.springframework.context.annotation.Import(AxonTestConfiguration::class)
@Sql("/schema.sql")
@ActiveProfiles("rbac-test")
@AutoConfigureMockMvc
class WidgetControllerRbacIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    init {
        extension(SpringExtension())

        test("WIDGET_ADMIN can create widget") {
            mockMvc.post("/api/v1/widgets") {
                with(jwt().authorities(SimpleGrantedAuthority("ROLE_WIDGET_ADMIN")))
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"Test"}"""
            }.andExpect {
                status { isCreated() }  // ❌ FAILS with ConnectException!
            }
        }
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgresContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:16.10-alpine"))
                .apply {
                    withDatabaseName("testdb")
                    withUsername("test")
                    withPassword("test")
                    // NO .start() - managed by @Testcontainers + @ServiceConnection
                }
    }
}
```

**Result:** ❌ ALL tests fail with:

```
io.kotest.engine.extensions.ExtensionException$BeforeAnyException
  Caused by: org.springframework.transaction.CannotCreateTransactionException: Could not open JDBC Connection for transaction
    Caused by: org.postgresql.util.PSQLException: Connection to localhost:5432 refused
      Caused by: java.net.ConnectException: Connection refused
```

## Configuration Files

### TestSecurityConfig (test profile - WORKS) ✅

```kotlin
@Configuration
@EnableWebSecurity
@Profile("test")
@Import(TestDslConfiguration::class, TestJpaBypassConfiguration::class)
open class TestSecurityConfig {
    @Bean
    @Primary
    fun testSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .authorizeHttpRequests { auth ->
                auth.anyRequest().permitAll()  // Permissive for existing tests
            }.csrf { csrf ->
                csrf.disable()
            }.build()

    @Bean
    open fun tokenRevocationStore(): TokenRevocationStore = /* mock */
}
```

### RbacTestSecurityConfig (rbac-test profile - FAILS) ❌

```kotlin
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)  // ← Only difference vs TestSecurityConfig!
@Profile("rbac-test")
@Import(TestDslConfiguration::class, TestJpaBypassConfiguration::class)
open class RbacTestSecurityConfig {

    private val objectMapper = ObjectMapper()

    private fun rbacTestAccessDeniedHandler() = AccessDeniedHandler { request, response, ex ->
        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.message ?: "Access Denied")
        problemDetail.type = URI.create("https://eaf.axians.com/errors/access-denied")
        problemDetail.instance = URI.create(request.requestURI)

        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.writer.write(objectMapper.writeValueAsString(problemDetail))
    }

    @Bean
    @Primary
    fun rbacTestSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth.anyRequest().authenticated()  // Enables ExceptionTranslationFilter
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { }
            }
            .exceptionHandling { eh ->
                eh.accessDeniedHandler(rbacTestAccessDeniedHandler())
            }
            .build()

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
    open fun tokenRevocationStore(): TokenRevocationStore = /* mock */
}
```

### TestDslConfiguration (Shared by both profiles)

```kotlin
@TestConfiguration
@Profile("test", "rbac-test")  // ← Story 3.10: Extended for RBAC tests
open class TestDslConfiguration(private val environment: Environment) {

    @Bean
    @Primary
    open fun testDataSource(): DataSource {
        val url = environment.getProperty("spring.datasource.url") ?: DEFAULT_JDBC_URL
        val username = environment.getProperty("spring.datasource.username") ?: DEFAULT_USERNAME
        val password = environment.getProperty("spring.datasource.password") ?: DEFAULT_PASSWORD
        val driver = environment.getProperty("spring.datasource.driver-class-name") ?: DEFAULT_DRIVER

        return DataSourceBuilder
            .create()
            .url(url)
            .username(username)
            .password(password)
            .driverClassName(driver)
            .build()
    }

    @Bean
    @Primary
    open fun dslContext(dataSource: DataSource): DSLContext = DSL.using(dataSource, SQLDialect.POSTGRES)

    @Bean
    @Primary
    open fun transactionManager(dataSource: DataSource): PlatformTransactionManager =
        DataSourceTransactionManager(dataSource)

    companion object {
        private const val DEFAULT_JDBC_URL = "jdbc:tc:postgresql:16.10-alpine:///eaf_test"
        private const val DEFAULT_USERNAME = "test"
        private const val DEFAULT_PASSWORD = "test"
        private const val DEFAULT_DRIVER = "org.testcontainers.jdbc.ContainerDatabaseDriver"
    }
}
```

**NOTE:** `DISABLE_MODULITH_JPA` excludes `HibernateJpaAutoConfiguration`, so TestDslConfiguration **MUST** provide manual DataSource bean (no Spring Boot auto-configuration)!

### AxonTestConfiguration (Shared by both profiles)

```kotlin
@TestConfiguration
@Profile("test", "rbac-test")  // ← Story 3.10: Extended for RBAC tests
@Import(TestDslConfiguration::class, TestJpaBypassConfiguration::class, PostgresEventStoreConfiguration::class)
class AxonTestConfiguration {

    @Autowired
    fun configure(configurer: EventProcessingConfigurer) {
        configurer.registerDefaultListenerInvocationErrorHandler {
            PropagatingErrorHandler.INSTANCE
        }
    }

    @Bean
    fun aggregateCache(): Cache = WeakReferenceCache()
}
```

### TestJpaBypassConfiguration (Shared by both profiles)

```kotlin
@TestConfiguration
@Profile("test", "rbac-test")  // ← Story 3.10: Extended for RBAC tests
open class TestJpaBypassConfiguration {
    @Bean
    open fun disableJpaBeans(): BeanFactoryPostProcessor =
        BeanFactoryPostProcessor { beanFactory: ConfigurableListableBeanFactory ->
            val registry = beanFactory as? BeanDefinitionRegistry

            listOf(
                "entityManagerFactory",
                "jpaSharedEM_entityManagerFactory",
            ).forEach { beanName ->
                if (registry?.containsBeanDefinition(beanName) == true) {
                    registry.removeBeanDefinition(beanName)
                }
            }
        }
}
```

## Error Analysis

### Error Message

```
WidgetControllerRbacIntegrationTest > WIDGET_ADMIN can create widget - returns 201 Created FAILED
    io.kotest.engine.extensions.ExtensionException$BeforeAnyException at WidgetControllerRbacIntegrationTest.kt:82
        Caused by: org.springframework.transaction.CannotCreateTransactionException: Could not open JDBC Connection for transaction
            Caused by: org.postgresql.util.PSQLException: Connection to localhost:5432 refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.
                Caused by: java.net.ConnectException: Connection refused
```

### Root Cause Analysis

1. **Testcontainers Container IS Running:** Container starts successfully (visible in logs)
2. **Dynamic Port NOT Used:** Error shows `localhost:5432` (default PostgreSQL port), NOT Testcontainers dynamic port (e.g., `localhost:55432`)
3. **@ServiceConnection NOT Working:** Spring Boot's @ServiceConnection should auto-configure `spring.datasource.url` with Testcontainers JDBC URL, but this fails for "rbac-test" profile
4. **Fallback URL Used:** TestDslConfiguration's `DEFAULT_JDBC_URL = "jdbc:tc:postgresql:16.10-alpine:///eaf_test"` is NOT being used (would work!)
5. **environment.getProperty("spring.datasource.url")** returns `null` or `localhost:5432` instead of Testcontainers URL

## Attempted Solutions (ALL FAILED)

### Attempt 1: Manual @DynamicPropertySource ❌

```kotlin
companion object {
    @Container
    @JvmStatic
    val postgresContainer = PostgreSQLContainer("postgres:16-alpine").apply { start() }

    @JvmStatic
    @DynamicPropertySource
    fun configureProperties(registry: DynamicPropertyRegistry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl)
        registry.add("spring.datasource.username", postgresContainer::getUsername)
        registry.add("spring.datasource.password", postgresContainer::getPassword)
    }
}
```

**Result:** ❌ Same ConnectException - properties not registered in "rbac-test" profile

### Attempt 2: Explicit .start() Blocking ❌

```kotlin
@Container
@ServiceConnection
@JvmStatic
val postgresContainer = PostgreSQLContainer(...).apply {
    withDatabaseName("testdb")
    withUsername("test")
    withPassword("test")
    start()  // Explicit blocking start
}
```

**Result:** ❌ Same ConnectException

### Attempt 3: Custom DataSource Bean with Dynamic Properties ❌

Created `RbacTestcontainersDataSourceConfig`:

```kotlin
@Configuration
@Profile("rbac-test")
class RbacTestcontainersDataSourceConfig {
    @Container
    private val postgres = PostgreSQLContainer("postgres:16-alpine")

    @Bean
    @Primary
    fun dataSource(): DataSource {
        val ds = DriverManagerDataSource()
        ds.setDriverClassName("org.postgresql.Driver")
        ds.url = postgres.jdbcUrl
        ds.username = postgres.username
        ds.password = postgres.password
        return ds
    }
}
```

**Result:** ❌ Bean conflicts, same timing issue

### Attempt 4: Remove @TestConfiguration, Use @Configuration ❌

Changed `RbacTestSecurityConfig` from `@TestConfiguration` → `@Configuration` to match working `TestSecurityConfig`.

**Result:** ❌ Same ConnectException

### Attempt 5: Disable @EnableMethodSecurity ❌

Temporarily disabled `@EnableMethodSecurity` to isolate the problem.

**Result:** ❌ Same ConnectException - NOT caused by @EnableMethodSecurity!

### Attempt 6: EXACT Template Copy ❌

Created WidgetControllerRbacIntegrationTest as **1:1 copy** of WidgetControllerIntegrationTest, changing ONLY:
- `TestSecurityConfig` → `RbacTestSecurityConfig`
- `@ActiveProfiles("test")` → `@ActiveProfiles("rbac-test")`
- Added `.with(jwt())` to MockMvc requests

**Result:** ❌ Same ConnectException - working test pattern does NOT work for "rbac-test" profile!

## Diagnostic Evidence

### Working Test Log (test profile)

```
HikariPool-1 - Starting...
Connection acquired: HikariProxyConnection@1961803287 wrapping org.testcontainers.jdbc.ConnectionWrapper@1bb7c3b4
@Sql script execution successful
Tests: 11 passed ✅
```

### Failing Test Log (rbac-test profile)

```
HikariPool-1 - Starting...
Connection to localhost:5432 refused ❌
PSQLException: Connection refused
@Sql script execution failed
Tests: 8 failed ❌
```

### Key Difference in Logs

**Working:** `org.testcontainers.jdbc.ConnectionWrapper@1bb7c3b4` (Testcontainers JDBC URL!)
**Failing:** Attempts connection to `localhost:5432` (default PostgreSQL, NOT Testcontainers!)

## Critical Questions

### 1. @ServiceConnection Activation

**Q:** Why does @ServiceConnection work for "test" profile but NOT for "rbac-test" profile?

**Evidence:**
- Both tests use `@Testcontainers` + `@ServiceConnection` + `@JvmStatic`
- Both tests import TestDslConfiguration with manual DataSource bean
- Both tests use IDENTICAL @SpringBootTest properties
- Only difference: Security configuration and profile name

**Hypothesis:** Spring Boot's @ServiceConnection auto-configuration may be profile-aware or dependent on bean ordering?

### 2. TestDslConfiguration Bean Priority

**Q:** Does TestDslConfiguration's manual `testDataSource()` bean prevent @ServiceConnection from registering properties?

**Context:**
- TestDslConfiguration has `@Primary` DataSource bean
- Reads properties via `environment.getProperty("spring.datasource.url")`
- Falls back to `DEFAULT_JDBC_URL = "jdbc:tc:postgresql:16.10-alpine:///eaf_test"`
- Bean is created during ApplicationContext initialization

**Hypothesis:** Bean initialization order: TestDslConfiguration testDataSource() runs BEFORE @ServiceConnection registers properties?

### 3. Spring Security Early Initialization

**Q:** Does RbacTestSecurityConfig trigger earlier ApplicationContext initialization than TestSecurityConfig?

**Differences:**
- RbacTestSecurityConfig has `@EnableMethodSecurity` (working test does NOT)
- RbacTestSecurityConfig has `.oauth2ResourceServer { oauth2.jwt { } }`
- RbacTestSecurityConfig has custom `AccessDeniedHandler` lambda

**Hypothesis:** @EnableMethodSecurity or OAuth2ResourceServer config triggers early database access (e.g., for user details)?

### 4. Kotest Lifecycle with Multiple Profiles

**Q:** Does Kotest's `SpringExtension` handle @ServiceConnection differently for different @ActiveProfiles?

**Evidence:**
- Both tests use identical Kotest setup: `FunSpec()` + `extension(SpringExtension())`
- Both tests use `companion object` with `@JvmStatic`
- Only difference: @ActiveProfiles value

**Hypothesis:** Kotest + Spring TestContext framework may have profile-specific initialization behavior?

### 5. @Import vs @SpringBootTest classes

**Q:** Does `@Import(AxonTestConfiguration::class)` vs including in `@SpringBootTest(classes = [...])` affect bean initialization order?

**Working Test:** TestSecurityConfig in `classes`, NO @Import
**Failing Test:** RbacTestSecurityConfig in `classes`, + `@Import(AxonTestConfiguration::class)`

**Hypothesis:** @Import causes nested configuration loading that bypasses @ServiceConnection timing guarantees?

## Expected Solution Characteristics

The ideal solution should:

1. ✅ Work with @EnableMethodSecurity (required for @PreAuthorize testing)
2. ✅ Use @ServiceConnection or @DynamicPropertySource (Spring Boot 3.x best practices)
3. ✅ Support both "test" (permitAll) and "rbac-test" (authenticated + method security) profiles
4. ✅ NOT break existing "test" profile tests (11 tests must continue passing)
5. ✅ Work with Kotest FunSpec lifecycle (NOT JUnit!)
6. ✅ Support full CQRS stack (Axon + jOOQ + PostgreSQL Testcontainers)
7. ✅ Handle HibernateJpaAutoConfiguration exclusion (DISABLE_MODULITH_JPA)

## Research Questions

1. **@ServiceConnection Profile Behavior:**
   - Is @ServiceConnection profile-aware in Spring Boot 3.5.7?
   - Does @ServiceConnection work differently with @Configuration vs @TestConfiguration?
   - Can @ServiceConnection be disabled or bypassed by certain Spring Boot configurations?

2. **Bean Initialization Order:**
   - When does TestDslConfiguration.testDataSource() initialize relative to @ServiceConnection?
   - Does @Primary affect when beans are created during ApplicationContext refresh?
   - Can @Lazy be used to defer DataSource creation until after @ServiceConnection?

3. **@EnableMethodSecurity Side Effects:**
   - Does @EnableMethodSecurity trigger early database access during context initialization?
   - Are there known issues with @EnableMethodSecurity + Testcontainers in Spring Security 6.5.5?
   - Does method security configuration affect Spring Boot auto-configuration lifecycle?

4. **Testcontainers + Kotest:**
   - Are there known issues with Testcontainers + Kotest + multiple Spring profiles?
   - Does Kotest's SpringExtension handle @ServiceConnection differently than JUnit?
   - Should Testcontainers lifecycle be managed differently in Kotest companion objects?

5. **HibernateJpaAutoConfiguration Exclusion:**
   - Does excluding HibernateJpaAutoConfiguration affect @ServiceConnection behavior?
   - Should DataSourceAutoConfiguration be explicitly enabled when Hibernate is excluded?
   - Is there a better way to exclude Modulith JPA without breaking auto-configuration?

## Workaround That Would Work (But We Want Better)

**Fallback Option:** Use `jdbc:tc:` JDBC URL directly (Testcontainers embedded mode)

```kotlin
@TestConfiguration
@Profile("rbac-test")
open class RbacDslConfiguration {
    @Bean
    @Primary
    fun rbacDataSource(): DataSource =
        DataSourceBuilder.create()
            .url("jdbc:tc:postgresql:16.10-alpine:///rbac_test")  // Embedded Testcontainers
            .username("test")
            .password("test")
            .driverClassName("org.testcontainers.jdbc.ContainerDatabaseDriver")
            .build()

    @Bean
    @Primary
    fun dslContext(dataSource: DataSource): DSLContext = DSL.using(dataSource, SQLDialect.POSTGRES)
}
```

**Why This Would Work:** Testcontainers JDBC Driver handles container lifecycle internally.

**Why We Don't Want This:** Duplicates TestDslConfiguration logic, less maintainable, not using Spring Boot 3.x @ServiceConnection best practice.

## Success Criteria

A solution is successful when:

1. ✅ WidgetControllerRbacIntegrationTest passes with "rbac-test" profile
2. ✅ @ServiceConnection (or @DynamicPropertySource) correctly registers Testcontainers JDBC URL
3. ✅ TestDslConfiguration.testDataSource() receives correct Testcontainers URL (NOT localhost:5432)
4. ✅ @Sql("/schema.sql") executes successfully after Testcontainers is ready
5. ✅ Existing WidgetControllerIntegrationTest with "test" profile continues to pass
6. ✅ @EnableMethodSecurity remains enabled (required for @PreAuthorize enforcement)
7. ✅ No bean conflicts or circular dependencies
8. ✅ Solution follows Spring Boot 3.x / Testcontainers best practices

## Validation Steps

To verify the solution works:

1. **Run RBAC Test:** `./gradlew :products:widget-demo:integrationTest --tests "*RbacIntegrationTest"`
   - Expected: 8 tests pass (3 POST, 2 GET, 3 PUT)
   - Expected: No ConnectException
   - Expected: 403 Forbidden for insufficient permissions
   - Expected: 201 Created for authorized operations

2. **Run Existing Tests:** `./gradlew :products:widget-demo:integrationTest`
   - Expected: 11+ tests pass (including working WidgetControllerIntegrationTest)
   - Expected: No regressions

3. **Verify Testcontainers URL:** Enable debug logging and confirm:
   - `spring.datasource.url` contains Testcontainers dynamic port (e.g., `localhost:55432`)
   - NOT `localhost:5432` or fallback `jdbc:tc:` URL

## Request

We need **TWO deliverables** from your research:

### Deliverable 1: Root Cause Analysis (MANDATORY)

**Goal:** Explain WHY @ServiceConnection works for "test" profile but fails for "rbac-test" profile.

**Required Analysis:**
1. **Profile-Specific Behavior:** Why does the SAME @Testcontainers + @ServiceConnection setup behave differently?
2. **Bean Initialization Order:** When does TestDslConfiguration.testDataSource() initialize relative to @ServiceConnection property registration?
3. **Spring Security Impact:** Does @EnableMethodSecurity or .oauth2ResourceServer() affect ApplicationContext initialization timing?
4. **Spring Boot Auto-Configuration:** How does @ServiceConnection interact with excluded HibernateJpaAutoConfiguration?
5. **Kotest Lifecycle:** Are there Kotest-specific behaviors that differ from JUnit for @ServiceConnection?

**Deliverables:**
- Timeline diagram of bean initialization order for BOTH profiles
- Identification of the EXACT point where "rbac-test" diverges from "test"
- Spring Boot 3.5.7 / Spring Security 6.5.5 documentation references
- Evidence if this is a Spring Boot bug or intended behavior

### Deliverable 2: Working Solution (MANDATORY)

**Approach Preference (in priority order):**

**Option A (PREFERRED):** Fix @ServiceConnection for "rbac-test" profile
- Maintain Spring Boot 3.x best practices
- Keep TestDslConfiguration shared across both profiles
- Use @ServiceConnection or @DynamicPropertySource
- Minimal code changes

**Option B (ACCEPTABLE):** Isolated rbac-test DataSource configuration
- Create separate RbacDslConfiguration with dedicated DataSource
- Use @DynamicPropertySource explicitly for rbac-test profile
- Keep TestDslConfiguration unchanged for "test" profile
- Clear separation of concerns

**Option C (FALLBACK):** Testcontainers JDBC Driver embedded mode
- Use `jdbc:tc:postgresql:16.10-alpine:///rbac_test` URL directly
- Simpler but less optimal for Spring Boot 3.x
- Should only be recommended if Option A/B are not viable

**Required for ANY solution:**
1. ✅ @EnableMethodSecurity MUST remain enabled (required for @PreAuthorize)
2. ✅ Existing "test" profile tests MUST continue passing (zero regressions)
3. ✅ Compatible with Kotest FunSpec + SpringExtension
4. ✅ Works with CQRS stack (Axon Framework event processors need database)
5. ✅ Handles HibernateJpaAutoConfiguration exclusion correctly
6. ✅ Ready-to-implement code with step-by-step instructions

**Format:**
- Step-by-step implementation guide
- Code snippets with detailed inline comments
- Explanation of WHY each change is necessary
- Validation steps to confirm success

### Deliverable 3: Alternative Approaches (OPTIONAL)

If you identify **multiple viable solutions**, provide comparison:

| Approach | Pros | Cons | Spring Boot Best Practice? | Recommendation |
|----------|------|------|---------------------------|----------------|
| Fix @ServiceConnection | ... | ... | ✅ Yes | ⭐⭐⭐⭐⭐ |
| Isolated Config | ... | ... | ✅ Yes | ⭐⭐⭐⭐ |
| jdbc:tc: URL | ... | ... | ⚠️ Legacy | ⭐⭐ |

**Bonus:** If there's a Spring Boot 3.x bug or limitation causing this, provide:
- GitHub issue references
- Spring Boot release notes mentioning the behavior
- Official workarounds from Spring team

---

## Additional Context

### Why This Matters

This is **NOT** a hypothetical problem - we have:
- ✅ Production code ready (@PreAuthorize annotations implemented)
- ✅ Security configuration correct (AccessDeniedHandler, ExceptionTranslationFilter)
- ✅ 5 AI agents consensus on security architecture
- ❌ **BLOCKED** on Test infrastructure for validation

Story cannot be completed without working RBAC tests to validate 403 Forbidden responses.

### What We've Learned

From 2.5h initial investigation + 3h follow-up:
- permitAll() bypasses ExceptionTranslationFilter ✅ (Web research confirmed)
- @ControllerAdvice does NOT work for Security Filter exceptions ✅ (Spring Security architecture)
- AccessDeniedHandler in FilterChain is correct approach ✅ (5 agents consensus)
- @Profile("!(test | rbac-test)") syntax is correct ✅ (Spring SpEL documentation)
- @ServiceConnection + @Testcontainers is Spring Boot 3.x best practice ✅ (Spring Boot docs)

**Remaining Mystery:** Why does this best practice work for one profile but not another?

## Debugging Strategy for External Agent

Since you don't have access to our codebase, here's how to approach this:

### Phase 1: Research Spring Boot @ServiceConnection Behavior

**Focus Areas:**
1. **Spring Boot 3.5.7 @ServiceConnection Implementation:**
   - Search for: "Spring Boot @ServiceConnection profile-specific behavior"
   - Search for: "Spring Boot @ServiceConnection bean initialization order"
   - Search for: "Spring Boot @ServiceConnection TestcontainersPropertySourceAutoConfiguration"
   - Look for: Known issues with @ServiceConnection + custom DataSource beans

2. **Spring Boot Auto-Configuration with Exclusions:**
   - Search for: "Spring Boot exclude HibernateJpaAutoConfiguration DataSource"
   - Search for: "Spring Boot @ServiceConnection with excluded auto-configurations"
   - Check: Does @ServiceConnection work when DataSourceAutoConfiguration is affected?

3. **Testcontainers + Spring Boot 3.x Integration:**
   - Search for: "Testcontainers @ServiceConnection Kotest"
   - Search for: "Testcontainers JDBC URL not registered Spring Boot 3"
   - Look for: GitHub issues in spring-boot-testcontainers module

### Phase 2: Investigate Spring Security @EnableMethodSecurity

**Focus Areas:**
1. **@EnableMethodSecurity Initialization Behavior:**
   - Search for: "Spring Security @EnableMethodSecurity early bean initialization"
   - Search for: "Spring Security 6.5.5 @EnableMethodSecurity ApplicationContext lifecycle"
   - Check: Does @EnableMethodSecurity trigger BeanFactoryPostProcessor execution?

2. **OAuth2ResourceServer Configuration:**
   - Search for: "Spring Security oauth2ResourceServer Testcontainers timing"
   - Check: Does JWT decoder configuration affect database initialization?

### Phase 3: Analyze Bean Initialization Order

**Key Questions to Research:**
1. When are `@Primary` beans created in Spring Boot ApplicationContext?
2. Does `@TestConfiguration` vs `@Configuration` affect initialization order?
3. When does `ServiceConnectionContextCustomizer` run relative to `@TestConfiguration` beans?
4. Does `@Import` annotation affect bean creation timing vs `@SpringBootTest(classes = [...])`?

### Phase 4: Kotest + Spring TestContext Framework

**Focus Areas:**
1. **Kotest SpringExtension Lifecycle:**
   - Search for: "Kotest SpringExtension @ServiceConnection"
   - Search for: "Kotest FunSpec companion object @DynamicPropertySource"
   - Check: Are there known incompatibilities?

2. **JUnit vs Kotest for Testcontainers:**
   - Compare: How does SpringExtension differ from JUnit's Spring test runner?
   - Check: Does Kotest's coroutine-based lifecycle affect Spring Boot test setup?

## Concrete Evidence to Gather

1. **Spring Boot Source Code Analysis:**
   - `ServiceConnectionContextCustomizer` - when does it register properties?
   - `TestcontainersPropertySourceAutoConfiguration` - what conditions must be met?
   - Bean definition order for `@Primary` + `@TestConfiguration`

2. **Spring Security Source Code Analysis:**
   - `@EnableMethodSecurity` - what BeanPostProcessors does it register?
   - `OAuth2ResourceServerConfiguration` - does it require DataSource?

3. **Documentation References:**
   - Spring Boot 3.5.7 reference docs on @ServiceConnection
   - Spring Security 6.5.5 reference docs on @EnableMethodSecurity
   - Testcontainers Spring Boot integration guide

## Expected Response Format

```markdown
# Solution: [Title describing the fix]

## Root Cause Explanation

[Detailed explanation with Spring Boot initialization timeline]

### Why "test" Profile Works
1. Step X: [Bean/Config initialization]
2. Step Y: @ServiceConnection registers properties
3. Step Z: TestDslConfiguration reads properties

### Why "rbac-test" Profile Fails
1. Step X: [Different initialization order]
2. Step Y: TestDslConfiguration created BEFORE @ServiceConnection
3. Step Z: Properties not available → localhost:5432 fallback

## Solution Implementation

### Approach: [Option A/B/C]

**Step 1:** [Description]
```kotlin
// Code with detailed comments
```

**Step 2:** [Description]
```kotlin
// Code with detailed comments
```

### Why This Works
[Explanation of mechanism and timing]

### Validation
[How to verify the fix works]

## Alternative Solutions Considered

[If applicable, comparison table]

## Red Flags to Avoid

**Do NOT suggest solutions that:**
- ❌ Break existing "test" profile tests
- ❌ Require disabling @EnableMethodSecurity
- ❌ Use deprecated Spring Boot patterns
- ❌ Violate Spring Boot 3.x best practices
- ❌ Introduce race conditions or timing hacks
- ❌ Require manual container lifecycle management (unless absolutely necessary)

**DO suggest solutions that:**
- ✅ Use Spring Boot auto-configuration where possible
- ✅ Maintain clear separation of test profiles
- ✅ Follow Spring Security 6.x best practices
- ✅ Are maintainable and well-documented
- ✅ Leverage Spring Boot 3.x features (@ServiceConnection, @DynamicPropertySource)
- ✅ Work with Kotest lifecycle

## Comparison: Working vs Failing Configuration

### Side-by-Side Code Differences

| Aspect | test (WORKS ✅) | rbac-test (FAILS ❌) |
|--------|-----------------|---------------------|
| **Security Config Class** | `TestSecurityConfig` | `RbacTestSecurityConfig` |
| **@EnableMethodSecurity** | ❌ NO | ✅ YES |
| **authorizeHttpRequests** | `.permitAll()` | `.authenticated()` |
| **oauth2ResourceServer** | ❌ NO | ✅ YES (.jwt { }) |
| **exceptionHandling** | ❌ NO | ✅ YES (AccessDeniedHandler) |
| **JwtDecoder Bean** | ❌ NO | ✅ YES (@Primary) |
| **@Import in Test** | ❌ NO | ✅ YES (AxonTestConfiguration) |
| **Testcontainers Setup** | Identical (companion object) | Identical (companion object) |
| **@ServiceConnection** | ✅ Works | ❌ Fails |

### Potential Culprits (Ranked by Likelihood)

1. **@EnableMethodSecurity (High Probability)**
   - Registers `MethodSecurityBeanDefinitionPostProcessor`
   - May trigger early bean creation for security evaluation
   - Could initialize DataSource before @ServiceConnection

2. **.oauth2ResourceServer { jwt { } } (Medium Probability)**
   - Configures JwtAuthenticationProvider
   - May require early ApplicationContext initialization
   - JwtDecoder bean created before @ServiceConnection?

3. **@Import(AxonTestConfiguration::class) (Medium Probability)**
   - Adds extra @TestConfiguration class
   - May change bean initialization order
   - Could process TestDslConfiguration before @ServiceConnection

4. **Custom AccessDeniedHandler (Low Probability)**
   - Lambda function, shouldn't affect initialization
   - No database dependencies

5. **authenticated() vs permitAll() (Low Probability)**
   - Only affects runtime authorization
   - Shouldn't affect bean initialization order

## Investigation Checklist for External Agent

Use this checklist to track your research:

- [ ] Read Spring Boot 3.5.7 @ServiceConnection source code
- [ ] Read Spring Security 6.5.5 @EnableMethodSecurity source code
- [ ] Search GitHub issues for "ServiceConnection profile" or "ServiceConnection custom DataSource"
- [ ] Check Spring Boot release notes 3.1.0 → 3.5.7 for @ServiceConnection behavior changes
- [ ] Research Spring Security 6.x + Testcontainers known issues
- [ ] Analyze BeanFactoryPostProcessor execution order
- [ ] Check Kotest + Spring Boot integration guide
- [ ] Review Testcontainers Spring Boot module documentation
- [ ] Search StackOverflow for similar profile-specific @ServiceConnection issues
- [ ] Consult Spring Boot team blog posts on @ServiceConnection best practices

## Success Indicators

Your solution is ready for implementation when you can confidently answer:

1. ✅ WHY does "test" profile work? (specific initialization timeline)
2. ✅ WHY does "rbac-test" profile fail? (specific root cause)
3. ✅ WHAT is the minimal change needed? (code-ready solution)
4. ✅ HOW to verify it works? (validation steps)
5. ✅ ARE there alternatives? (comparison if multiple options exist)

**Time Budget:** Assume 30-60 minutes of deep research required. Prioritize finding the root cause over quick workarounds.

---

## Investigation Timeline (For Context)

### Session 1: Initial 403 vs 500 Problem (2.5 hours)
- **Problem:** @PreAuthorize returns 500 instead of 403
- **Investigation:** 7 different approaches tried
- **Solution Found:** `authenticated()` + AccessDeniedHandler in FilterChain
- **Result:** Created deep-research-prompt-v1.md, consulted 5 AI agents

### Session 2: Implementing 5-Agent Consensus (3+ hours)
- **Implemented:** All security fixes from 5 agents (100% consensus items)
- **Created:** RbacTestSecurityConfig with AccessDeniedHandler
- **Created:** WidgetControllerRbacIntegrationTest based on working template
- **Discovered:** NEW blocker - Testcontainers ConnectException for "rbac-test" profile
- **Attempted:** 6 different solutions (all failed!)
- **Result:** This deep-research-prompt-v2.md

### Total Investigation Time: 5.5+ hours

**Key Insight:** The Testcontainers issue only emerged AFTER correctly implementing the security architecture. We are NOT debugging the original 403 problem anymore - that's solved! This is a NEW, isolated infrastructure issue.

### What Works Now ✅

1. ✅ Production Code: @PreAuthorize annotations on all Widget endpoints
2. ✅ Security Config: AccessDeniedHandler returns RFC 7807 ProblemDetail
3. ✅ Profile Isolation: `@Profile("!(test | rbac-test)")` prevents bean conflicts
4. ✅ Test Infrastructure: RbacTestSecurityConfig with @EnableMethodSecurity
5. ✅ Existing Tests: WidgetControllerIntegrationTest with "test" profile passes

### What's Blocked ❌

1. ❌ RBAC Integration Tests: All 8 tests fail with ConnectException
2. ❌ Testcontainers URL Registration: @ServiceConnection not working for "rbac-test"
3. ❌ Story Completion: Cannot validate 403 responses without working tests

---

## Final Notes

**This is a HIGH-PRIORITY blocker** that prevents completion of Story 3.10 (RBAC API Endpoints).

We have exhausted local debugging and need external expert analysis to identify:
1. Spring Boot @ServiceConnection behavior with multiple test profiles
2. Potential Spring Security @EnableMethodSecurity initialization side effects
3. Best practice for Testcontainers + Spring Security 6.x + Kotest integration

**Thank you for your expertise!** 🙏
