# JUnit 6 Migration Guide

**Migration Date**: 2025-11-20
**From**: Kotest 6.0.4
**To**: JUnit 6.0.1 + AssertJ 3.27.3

---

## Executive Summary

This guide documents the migration from Kotest to JUnit 6 across the EAF (Enterprise Application Framework) v1.0 project. The migration includes:

- **Testing Framework**: Kotest 6.0.4 → JUnit 6.0.1
- **Assertion Library**: Kotest Assertions → AssertJ 3.27.3 + AssertJ-Kotlin 0.2.1
- **Mocking Library**: MockK 1.13.14 (added for Kotlin compatibility)
- **Test Execution**: JUnit Platform (unchanged)

---

## Why AssertJ?

AssertJ was chosen as the assertion library for the following reasons:

1. ✅ **Industry Standard**: Most popular assertion library for JUnit testing
2. ✅ **Fluent API**: Similar readability to Kotest assertions
3. ✅ **Kotlin Extensions**: Idiomatic Kotlin support via assertj-kotlin
4. ✅ **Rich Assertions**: Comprehensive assertion library for collections, exceptions, optionals, etc.
5. ✅ **IDE Support**: Excellent autocomplete in IntelliJ IDEA
6. ✅ **Active Maintenance**: Regularly updated by Joel Costigliola

---

## Migration Patterns

### 1. Test Class Structure

**Kotest (Before)**:
```kotlin
class MyTest : FunSpec({
    test("should do something") {
        // test logic
    }

    context("when condition is met") {
        test("should do something else") {
            // test logic
        }
    }
})
```

**JUnit 6 (After)**:
```kotlin
class MyTest {
    @Test
    fun `should do something`() {
        // test logic
    }

    @Nested
    inner class `when condition is met` {
        @Test
        fun `should do something else`() {
            // test logic
        }
    }
}
```

### 2. Lifecycle Hooks

| Kotest | JUnit 6 |
|--------|---------|
| `beforeTest { }` | `@BeforeEach fun setup() { }` |
| `afterTest { }` | `@AfterEach fun teardown() { }` |
| `beforeSpec { }` | `@BeforeAll fun setupAll() { }` (companion object + `@JvmStatic`) |
| `afterSpec { }` | `@AfterAll fun teardownAll() { }` (companion object + `@JvmStatic`) |

**Example**:

```kotlin
// Kotest
class MyTest : FunSpec({
    beforeTest {
        // setup before each test
    }

    afterTest {
        // cleanup after each test
    }

    test("my test") {
        // test logic
    }
})

// JUnit 6
class MyTest {
    @BeforeEach
    fun setup() {
        // setup before each test
    }

    @AfterEach
    fun teardown() {
        // cleanup after each test
    }

    @Test
    fun `my test`() {
        // test logic
    }
}
```

### 3. Assertions

| Kotest | AssertJ |
|--------|---------|
| `x shouldBe y` | `assertThat(x).isEqualTo(y)` |
| `x shouldNotBe y` | `assertThat(x).isNotEqualTo(y)` |
| `x.shouldBeNull()` | `assertThat(x).isNull()` |
| `x.shouldNotBeNull()` | `assertThat(x).isNotNull()` |
| `x.shouldBeTrue()` | `assertThat(x).isTrue()` |
| `x.shouldBeFalse()` | `assertThat(x).isFalse()` |
| `list.shouldBeEmpty()` | `assertThat(list).isEmpty()` |
| `list shouldHaveSize 3` | `assertThat(list).hasSize(3)` |
| `list shouldContain item` | `assertThat(list).contains(item)` |
| `shouldThrow<T> { }` | `assertThrows<T> { }` (or AssertJ's `assertThatThrownBy`) |

**Example**:

```kotlin
// Kotest
test("should validate widget") {
    val widget = Widget("test", 42)

    widget.name shouldBe "test"
    widget.value shouldBe 42
    widget.tags.shouldNotBeEmpty()
    widget.tags shouldContain "important"
    widget.metadata.shouldNotBeNull()
}

// JUnit 6 + AssertJ
@Test
fun `should validate widget`() {
    val widget = Widget("test", 42)

    assertThat(widget.name).isEqualTo("test")
    assertThat(widget.value).isEqualTo(42)
    assertThat(widget.tags).isNotEmpty()
    assertThat(widget.tags).contains("important")
    assertThat(widget.metadata).isNotNull()
}
```

### 4. Exception Testing

**Kotest**:
```kotlin
test("should throw exception") {
    shouldThrow<IllegalArgumentException> {
        service.doSomethingBad()
    }
}

test("should throw exception with message") {
    val exception = shouldThrow<IllegalArgumentException> {
        service.doSomethingBad()
    }
    exception.message shouldBe "Bad input"
}
```

**JUnit 6 + AssertJ**:
```kotlin
@Test
fun `should throw exception`() {
    assertThrows<IllegalArgumentException> {
        service.doSomethingBad()
    }
}

@Test
fun `should throw exception with message`() {
    val exception = assertThrows<IllegalArgumentException> {
        service.doSomethingBad()
    }
    assertThat(exception.message).isEqualTo("Bad input")
}

// Alternative with AssertJ
@Test
fun `should throw exception with AssertJ`() {
    assertThatThrownBy { service.doSomethingBad() }
        .isInstanceOf(IllegalArgumentException::class.java)
        .hasMessage("Bad input")
}
```

### 5. Parameterized Tests

**Kotest**:
```kotlin
class MyTest : FunSpec({
    listOf(
        "input1" to "output1",
        "input2" to "output2",
        "input3" to "output3"
    ).forEach { (input, expected) ->
        test("should transform $input to $expected") {
            val result = transform(input)
            result shouldBe expected
        }
    }
})
```

**JUnit 6**:
```kotlin
class MyTest {
    @ParameterizedTest
    @CsvSource(
        "input1, output1",
        "input2, output2",
        "input3, output3"
    )
    fun `should transform input to output`(input: String, expected: String) {
        val result = transform(input)
        assertThat(result).isEqualTo(expected)
    }
}
```

### 6. Disabled/Skipped Tests

**Kotest**:
```kotlin
xtest("this test is disabled") {
    // won't run
}

test("this test is also disabled").config(enabled = false) {
    // won't run
}
```

**JUnit 6**:
```kotlin
@Disabled("Reason for disabling")
@Test
fun `this test is disabled`() {
    // won't run
}
```

### 7. Test Ordering

**Kotest**:
```kotlin
class MyTest : FunSpec({
    test("z test").config(order = 1) {
        // runs first
    }

    test("a test").config(order = 2) {
        // runs second
    }
})
```

**JUnit 6**:
```kotlin
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MyTest {
    @Test
    @Order(1)
    fun `z test`() {
        // runs first
    }

    @Test
    @Order(2)
    fun `a test`() {
        // runs second
    }
}
```

### 8. Conditional Test Execution

**Kotest**:
```kotlin
test("only on Linux").config(
    enabledIf = { System.getProperty("os.name").contains("Linux") }
) {
    // runs only on Linux
}
```

**JUnit 6**:
```kotlin
@EnabledOnOs(OS.LINUX)
@Test
fun `only on Linux`() {
    // runs only on Linux
}
```

### 9. Timeouts

**Kotest**:
```kotlin
test("should complete quickly").config(timeout = 1.seconds) {
    // must complete within 1 second
}
```

**JUnit 6**:
```kotlin
@Test
@Timeout(value = 1, unit = TimeUnit.SECONDS)
fun `should complete quickly`() {
    // must complete within 1 second
}
```

### 10. Tags/Categories

**Kotest**:
```kotlin
test("integration test").config(tags = setOf(IntegrationTag)) {
    // tagged as integration
}
```

**JUnit 6**:
```kotlin
@Tag("integration")
@Test
fun `integration test`() {
    // tagged as integration
}
```

---

## Spring Boot Integration Tests

### Kotest Pattern

```kotlin
@SpringBootTest
@ActiveProfiles("test")
class WidgetIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    init {
        extension(SpringExtension())

        test("should create widget") {
            mockMvc.perform(post("/api/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Test"}"""))
                .andExpect(status().isCreated())
        }
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            PostgresTestContainer.start()
            registry.add("spring.datasource.url") { PostgresTestContainer.jdbcUrl }
        }
    }
}
```

### JUnit 6 Pattern

```kotlin
@SpringBootTest
@ActiveProfiles("test")
class WidgetIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `should create widget`() {
        mockMvc.perform(post("/api/widgets")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Test"}"""))
            .andExpect(status().isCreated())
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            // Setup testcontainers before all tests
            PostgresTestContainer.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresTestContainer.jdbcUrl }
        }
    }
}
```

---

## Arrow Either Testing

### Kotest Pattern

```kotlin
test("should return Right") {
    val result = service.doSomething()

    result.shouldBeRight()
    result.getOrNull() shouldBe expectedValue
}

test("should return Left with error") {
    val result = service.doSomethingBad()

    result.shouldBeLeft()
    result.leftValue shouldBe expectedError
}
```

### JUnit 6 + AssertJ Pattern

```kotlin
@Test
fun `should return Right`() {
    val result = service.doSomething()

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()).isEqualTo(expectedValue)
}

@Test
fun `should return Left with error`() {
    val result = service.doSomethingBad()

    assertThat(result.isLeft()).isTrue()
    assertThat(result.swap().getOrNull()).isEqualTo(expectedError)
}
```

---

## Axon Test Fixtures

**No changes required** - Axon test fixtures work identically with both Kotest and JUnit 6:

```kotlin
// Works with both Kotest and JUnit 6
class WidgetAggregateTest {
    private lateinit var fixture: FixtureConfiguration<Widget>

    @BeforeEach
    fun setup() {
        fixture = AggregateTestFixture(Widget::class.java)
    }

    @Test
    fun `should create widget`() {
        fixture
            .givenNoPriorActivity()
            .`when`(CreateWidgetCommand(widgetId, "Test"))
            .expectEvents(WidgetCreatedEvent(widgetId, "Test"))
    }
}
```

---

## Testcontainers Integration

**No changes required** - Testcontainers work identically, but use JUnit Jupiter extension:

```kotlin
// Add to dependencies
testImplementation("org.testcontainers:junit-jupiter:1.21.3")

// In tests
@Testcontainers
class MyIntegrationTest {
    @Container
    val postgres = PostgreSQLContainer("postgres:16.10")
        .withDatabaseName("test")

    @Test
    fun `test with container`() {
        // Container is automatically started/stopped
    }
}
```

---

## Common Migration Issues

### 1. Nested Classes Require `inner` Keyword

**Issue**:
```kotlin
class MyTest {
    @Nested
    class `when condition is met` {  // ❌ Won't work
        @Test
        fun `should do something`() { }
    }
}
```

**Solution**:
```kotlin
class MyTest {
    @Nested
    inner class `when condition is met` {  // ✅ Correct
        @Test
        fun `should do something`() { }
    }
}
```

### 2. Companion Object for Static Methods

**Issue**:
```kotlin
class MyTest {
    @BeforeAll
    fun setupAll() { }  // ❌ Must be static
}
```

**Solution**:
```kotlin
class MyTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() { }  // ✅ Correct
    }
}
```

### 3. Import Organization

Replace all Kotest imports with JUnit 6 + AssertJ:

**Remove**:
```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.assertions.throwables.shouldThrow
```

**Add**:
```kotlin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
```

---

## Migration Checklist

### Phase 1: Infrastructure ✅
- [x] Update `gradle/libs.versions.toml` with JUnit 6 and AssertJ
- [x] Update `TestingConventionPlugin.kt` to use JUnit 6 dependencies
- [x] Remove Kotest plugin and dependencies
- [x] Add JUnit 6, AssertJ, and MockK dependencies

### Phase 2: Test Conversion (In Progress)
- [x] framework/core/AggregateRootTest.kt (Example conversion completed)
- [ ] framework/core (remaining tests)
- [ ] framework/multi-tenancy
- [ ] framework/security
- [ ] framework/persistence
- [ ] framework/web
- [ ] products/widget-demo
- [ ] shared/testing

### Phase 3: Documentation
- [ ] Update CLAUDE.md
- [ ] Update docs/architecture/test-strategy.md
- [ ] Update test naming conventions
- [ ] Update code examples in PRD/tech-spec/stories

### Phase 4: CI/CD
- [ ] Update .github/workflows/*.yml
- [ ] Test full pipeline
- [ ] Update quality gates

---

## Automated Conversion Script

For bulk migration, consider this Kotlin script pattern:

```kotlin
fun convertKotestToJUnit(testFile: File) {
    var content = testFile.readText()

    // Replace imports
    content = content
        .replace("import io.kotest.core.spec.style.FunSpec", "import org.junit.jupiter.api.Test")
        .replace("import io.kotest.matchers.shouldBe", "import org.assertj.core.api.Assertions.assertThat")
        .replace("import io.kotest.assertions.throwables.shouldThrow", "import org.junit.jupiter.api.assertThrows")

    // Replace class structure
    content = content.replace(
        Regex("""class (\w+)\s*:\s*FunSpec\(\{"""),
        "class $1 {"
    )

    // Replace test declarations
    content = content.replace(
        Regex("""test\("([^"]+)"\)\s*\{"""),
        "@Test\nfun `$1`() {"
    )

    // Replace assertions
    content = content.replace(
        Regex("""(\w+)\s+shouldBe\s+(.+)"""),
        "assertThat($1).isEqualTo($2)"
    )

    testFile.writeText(content)
}
```

---

## Testing the Migration

After converting tests, verify they work:

```bash
# Test single module
./gradlew :framework:core:test

# Test all modules
./gradlew test

# Test with coverage
./gradlew test koverReport

# Integration tests
./gradlew integrationTest

# Full check (all quality gates)
./gradlew check
```

---

## Performance Comparison

Expected performance characteristics:

| Metric | Kotest | JUnit 6 | Change |
|--------|--------|---------|--------|
| Test execution | ~5ms/test | ~5ms/test | Similar |
| Build time | ~3min | ~3min | Similar |
| Test discovery | Fast | Fast | Similar |
| IDE integration | Excellent | Excellent | Similar |

**Note**: Both frameworks use JUnit Platform for execution, so performance is nearly identical.

---

## References

- [JUnit 6 Release Notes](https://docs.junit.org/6.0.0/release-notes/)
- [AssertJ Documentation](https://assertj.github.io/doc/)
- [AssertJ Kotlin](https://github.com/ErikThomasson/assertj-kotlin)
- [JUnit 6 User Guide](https://junit.org/junit5/docs/6.0.0/user-guide/)
- [Migrating from Kotest to JUnit 5](https://phauer.com/2018/best-practices-unit-testing-kotlin/)

---

**Migration Status**: In Progress (Infrastructure Complete, Test Conversion Started)
**Estimated Completion**: 2025-11-22 (2 days for full migration)
