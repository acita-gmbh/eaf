# Story 6.2: Spring Boot Test Context Configuration Blocker

## Executive Summary

**Objective**: Create integration test for Flowable→Axon bridge (DispatchAxonCommandTask JavaDelegate)
**Blocker**: Cannot start Spring Boot test context due to cascading framework module dependencies
**Attempts**: 15+ different Spring configuration approaches over 2 hours
**Status**: BLOCKED - Need external expert guidance

## Technical Context

### Project Architecture
- **Framework**: Spring Boot 3.5.6, Kotlin 2.2.20, Kotest 6.0.3
- **Pattern**: Modular monolith with Hexagonal Architecture + Spring Modulith
- **Modules**:
  - `framework/workflow` - Flowable BPMN integration (Story 6.1 ✅ DONE)
  - `framework/cqrs` - Axon Framework wrapper
  - `framework/security` - 10-layer JWT validation
  - `framework/observability` - Metrics/tracing
  - `framework/persistence` - JPA repositories
  - `products/widget-demo` - Widget domain aggregate (test target)

### Dependency Graph (THE PROBLEM)
```
framework/workflow (test)
  ├─ needs: Widget aggregate (@Aggregate from products/widget-demo)
  ├─ needs: DispatchAxonCommandTask (requires CommandGateway, TenantContext)
  │
  ├─ products/widget-demo.jar on classpath
  │   ├─ depends on: framework/cqrs
  │   │   └─ depends on: framework/security (LINE 12 of cqrs/build.gradle.kts)
  │   │       └─ @Configuration classes: SecurityFilterChainConfiguration, SecurityConfiguration
  │   │           └─ creates: jwtDecoder bean
  │   │               └─ TRIES TO CONNECT: http://localhost:8180/realms/eaf (Keycloak)
  │   │                   └─ FAILS: java.lang.IllegalArgumentException at JwtDecoderProviderConfigurationUtils.java:181
  │   └─ depends on: framework/security (LINE 21 of widget-demo/build.gradle.kts)
  │
  └─ framework/cqrs.jar on classpath
      ├─ auto-configured: AxonConfiguration (via META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports)
      │   └─ needs: CommandMetricsInterceptor
      │       └─ needs: CustomMetrics (from framework/observability)
      │           └─ framework/observability depends on framework/security!
      └─ depends on: framework/security
```

### Root Cause Analysis

**Issue**: Spring Boot @SpringBootTest with component scanning triggers cascading dependency loading:
1. Scan `products.widgetdemo` → loads widget-demo JAR
2. widget-demo JAR has `framework/security` as compile dependency
3. Spring loads ALL @Configuration classes from security JAR on classpath
4. SecurityConfiguration creates jwtDecoder bean
5. jwtDecoder tries to connect to Keycloak OIDC issuer (http://localhost:8180/realms/eaf)
6. Connection fails → test context startup fails

**Why Standard Solutions Don't Work**:
- ❌ `exclude = [SecurityAutoConfiguration::class]` → Doesn't prevent @Configuration classes from loading
- ❌ Selective package scanning → Dependencies transitively load security configurations
- ❌ Disabling auto-configuration → Custom @Configuration classes still load from JAR
- ❌ Providing dummy JWT issuer URI → Spring tries to connect, fails
- ❌ @Import(AxonIntegrationTestConfig::class) → Doesn't override security configs from JAR

## What I've Tried (15+ Attempts)

### Configuration Approaches

1. **Multiple @SpringBootTest in same module**
   - Issue: Kotest 6.0.3 `IllegalStateException: Could not find spec TestDescriptor`
   - Solution: Created separate `axonIntegrationTest` source set ✅

2. **Exclude SecurityAutoConfiguration**
   ```kotlin
   @SpringBootApplication(exclude = [SecurityAutoConfiguration::class])
   ```
   - Result: ❌ Custom @Configuration classes still load from security JAR

3. **Exclude all security auto-configs**
   ```kotlin
   exclude = [
       SecurityAutoConfiguration::class,
       OAuth2ResourceServerAutoConfiguration::class,
       OAuth2ClientAutoConfiguration::class,
   ]
   ```
   - Result: ❌ Custom @Configuration classes still load

4. **application.yml autoconfigure exclusions**
   ```yaml
   spring.autoconfigure.exclude:
     - org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
     - com.axians.eaf.framework.security.config.SecurityConfiguration
   ```
   - Result: ❌ Doesn't prevent @Configuration class loading from JAR

5. **Surgical package scanning**
   ```kotlin
   scanBasePackages = [
       "com.axians.eaf.framework.workflow.delegates",
       "com.axians.eaf.framework.persistence.repositories",
       "com.axians.eaf.products.widgetdemo.domain",
       "com.axians.eaf.products.widgetdemo.projections",
   ]
   ```
   - Result: ❌ JARs on classpath still load @Configuration classes

6. **Exclude framework.cqrs from scanning**
   - Rationale: AxonConfiguration auto-configured via META-INF/spring
   - Result: ❌ Still loads because widget-demo depends on it

7. **Exclude framework.observability from scanning**
   - Rationale: observability depends on security
   - Provide MeterRegistry via test config
   - Result: ❌ Still issues with CommandMetricsInterceptor

8. **Disable AxonConfiguration auto-configuration**
   ```yaml
   spring.autoconfigure.exclude:
     - com.axians.eaf.framework.cqrs.config.AxonConfiguration
   ```
   - Result: ❌ Still bean creation failures

9. **Provide TenantContext via @TestConfiguration**
   ```kotlin
   @TestConfiguration
   open class AxonIntegrationTestConfig {
       @Bean
       open fun tenantContext(): TenantContext = TenantContext(SimpleMeterRegistry())
   }
   ```
   - Result: ❌ Doesn't prevent security configs from loading

10. **@ComponentScan.Filter to exclude delegates**
    - Applied to WorkflowTestApplication (Story 6.1)
    - Result: ✅ Story 6.1 tests pass (regression check successful)

### Test Isolation Approaches

11. **Separate test source set** ✅ IMPLEMENTED
    ```kotlin
    val axonIntegrationTest by sourceSets.creating { ... }
    ```
    - Created: `src/axon-integration-test/kotlin`
    - Task: `:axonIntegrationTest`
    - Result: ✅ Isolates from Story 6.1 tests, but doesn't solve Spring Boot config issue

12. **Dedicated test application** ✅ IMPLEMENTED
    ```kotlin
    @SpringBootApplication
    class DispatchAxonCommandTestApplication
    ```
    - Result: ✅ Separate from WorkflowTestApplication, but same config issues

### Current Blocker

**Error**: Cannot start Spring Boot test context for Axon integration test
**Root Cause**: @Configuration classes from framework/security JAR load when scanning packages with transitive security dependencies
**Impact**: Test-first spike cannot validate Flowable-Axon integration

## Research Questions for External AI

### Question 1: Spring Boot Test Isolation from Transitive @Configuration

How do you prevent Spring Boot @SpringBootTest from loading @Configuration classes from transitive dependency JARs when those JARs are on the test classpath but not needed for the test?

**Constraints**:
- MUST have widget-demo JAR on test classpath (contains Widget aggregate)
- widget-demo depends on framework/security at compile time
- framework/security has @Configuration classes (SecurityConfiguration, SecurityFilterChainConfiguration)
- These @Configuration classes MUST NOT load during test (require Keycloak)
- Standard `exclude =` doesn't prevent custom @Configuration loading
- Component scanning excludes don't prevent JAR-based configurations

**Specific**:
- Spring Boot 3.5.6
- Kotlin 2.2.20
- Test framework: Kotest 6.0.3 with @SpringBootTest
- Pattern: Hexagonal architecture with Spring Modulith module boundaries

### Question 2: Axon Framework Test Configuration Without Full Security

How do you test Axon CommandGateway dispatch in isolation without loading full production security configuration?

**Requirements**:
- Need: CommandGateway bean (from Axon Spring Boot Starter)
- Need: Widget aggregate discovery (Axon @Aggregate)
- Need: Widget projection handler (@EventHandler)
- Need: TenantContext bean (custom, provided via @TestConfiguration)
- DO NOT WANT: JwtDecoder, SecurityFilterChain, OAuth2 configurations

**Current Attempt**:
```kotlin
@SpringBootApplication(
    scanBasePackages = [
        "com.axians.eaf.products.widgetdemo.domain",
        "com.axians.eaf.products.widgetdemo.projections",
    ],
    exclude = [SecurityAutoConfiguration::class]
)
@TestConfiguration
class TestApp {
    @Bean
    fun tenantContext() = TenantContext(SimpleMeterRegistry())
}
```

**Result**: Security configurations still load from widget-demo's framework/security dependency JAR

### Question 3: Kotest 6.0.3 Multiple @SpringBootTest Limitation

Is there a known limitation with Kotest 6.0.3 JUnit Platform runner preventing multiple @SpringBootTest specs in the same test source set?

**Error**:
```
java.lang.IllegalStateException: Could not find spec TestDescriptor for DescriptorId(value=com.axians.eaf.framework.workflow.FlowableEngineIntegrationTest)
    at io.kotest.runner.junit.platform.DescriptorsKt.getSpecTestDescriptor(descriptors.kt:18)
```

**Observations**:
- ✅ Single @SpringBootTest spec works fine (Story 6.1: 3/3 tests pass)
- ❌ Two @SpringBootTest specs in same source set → IllegalStateException
- ✅ Workaround: Separate test source sets (but doesn't solve Spring config issue)

## Attempted Solutions Matrix

| Approach | Result | Notes |
|----------|--------|-------|
| Exclude SecurityAutoConfiguration | ❌ Failed | Custom @Configuration still loads |
| Exclude OAuth2 auto-configs | ❌ Failed | Doesn't affect custom configs |
| Surgical package scanning | ❌ Failed | JARs on classpath still processed |
| Disable AxonConfiguration | ❌ Failed | Missing CommandMetricsInterceptor → CustomMetrics |
| Provide TenantContext via @TestConfiguration | ✅ Compiles | But security configs still attempt to load |
| Separate test source set | ✅ Implemented | Solves Kotest issue, not Spring config |
| Dedicated test application | ✅ Implemented | Still loads transitive configs |
| @ComponentScan.Filter excludes | ❌ Failed | Only works for scanned packages, not JARs |
| application.yml autoconfigure.exclude | ❌ Failed | Doesn't prevent custom @Configuration |
| Dummy JWT issuer URI | ❌ Failed | Spring tries to connect |

## Potential Solutions to Explore

### Option A: Mock Security Beans
Provide mock beans for entire security chain:
```kotlin
@TestConfiguration
class SecurityMockConfig {
    @Bean fun jwtDecoder(): JwtDecoder = mock()
    @Bean fun securityFilterChain(): SecurityFilterChain = mock()
    // ... more mocks
}
```
**Risk**: May require mocking 10+ beans, fragile

### Option B: Test-Only Aggregate
Create minimal test-only aggregate instead of using Widget:
```kotlin
@Aggregate
class TestAggregate {
    @CommandHandler
    constructor(command: CreateTestCommand) { ... }
}
```
**Risk**: Doesn't validate real Widget integration

### Option C: @ConditionalOnProperty for Security Configs
Modify framework/security configurations to be conditional:
```kotlin
@Configuration
@ConditionalOnProperty(name = "eaf.security.enabled", havingValue = "true", matchIfMissing = true)
class SecurityConfiguration { ... }
```
**Risk**: Requires modifying production code for test purposes

### Option D: Gradle Test Fixtures Plugin
Use test-fixtures to provide security-free versions of modules:
```kotlin
dependencies {
    testFixturesImplementation(project(":framework:cqrs"))
}
```
**Risk**: Significant build configuration changes

### Option E: Axon AggregateTestFixture
Use Axon's test fixture instead of full @SpringBootTest:
```kotlin
test("should dispatch command") {
    val fixture = AggregateTestFixture(Widget::class.java)
    // Test aggregate directly, no Spring context
}
```
**Risk**: Doesn't validate Flowable→Spring→Axon integration (main goal of spike)

## Files Created/Modified

**New Files**:
- `framework/workflow/src/axon-integration-test/kotlin/.../DispatchAxonCommandTaskIntegrationTest.kt`
- `framework/workflow/src/axon-integration-test/kotlin/.../DispatchAxonCommandTestApplication.kt`
- `framework/workflow/src/axon-integration-test/kotlin/.../AxonIntegrationTestConfig.kt`
- `framework/workflow/src/axon-integration-test/resources/application.yml`
- `framework/workflow/src/axon-integration-test/resources/processes/example-widget-creation.bpmn20.xml`
- `framework/workflow/src/main/kotlin/.../DispatchAxonCommandTask.kt`

**Modified Files**:
- `framework/workflow/build.gradle.kts` - Added axonIntegrationTest source set
- `framework/workflow/src/integration-test/kotlin/.../WorkflowTestApplication.kt` - Added @ComponentScan.Filter

## Recommended Next Steps

1. **External AI Research**: Query 3-4 AI sources (Gemini Pro, Claude, ChatGPT, Perplexity) with Questions 1-3 above
2. **Spring Boot Community**: Search Spring Boot GitHub issues for similar modular monolith test isolation patterns
3. **Axon Forum**: Ask Axon community how to test JavaDelegate→CommandGateway without full Spring context
4. **Pragmatic Fallback**: If no solution found in 2 hours, implement Option E (AggregateTestFixture) and document limitation

## Success Criteria

**Minimum**: Spring Boot test context starts without JWT/Keycloak
**Ideal**: Full integration test validates BPMN→JavaDelegate→CommandGateway→Widget aggregate→Projection

## Debug Logs

- Full debug log: `/tmp/axon-test-debug-v2.log`
- Test reports: `framework/workflow/build/reports/tests/axonIntegrationTest/`

## Story Context

- **Story**: 6.2 - Create Flowable-to-Axon Bridge (Command Dispatch)
- **Epic**: 6 - Core Framework Hooks (Flowable Prep)
- **QA Gate**: ✅ PASS (90/100 quality score)
- **Test-First Spike**: Quinn's recommendation to validate integration before production code
- **Risk Score**: 6/10 (MEDIUM-HIGH) - First Flowable-Axon integration

---

**URGENT**: This blocker prevents validating the core technical risk (Flowable-Axon integration) identified by QA.