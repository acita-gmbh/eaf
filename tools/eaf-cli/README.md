# EAF Scaffolding CLI

Enterprise Application Framework code generation and scaffolding tool.

## Overview

The EAF CLI automates the creation of boilerplate code required by the EAF's complex technology stack (Hexagonal Architecture + CQRS/Event Sourcing + Spring Modulith + Flowable BPMN), enabling new developers to meet the <1 month productivity goal.

**Version:** 0.1.0 (Story 7.1 - Foundation)

## Quick Start

### Development Testing (via Gradle)

```bash
# Run CLI directly with Gradle
./gradlew :tools:eaf-cli:run --args="--version"
./gradlew :tools:eaf-cli:run --args="--help"
./gradlew :tools:eaf-cli:run --args="scaffold --help"
```

### Installation (Local Distribution)

```bash
# Generate distribution with executable scripts
./gradlew :tools:eaf-cli:installDist

# Add to PATH or create alias
export PATH="$PATH:/Users/michael/acci_eaf/tools/eaf-cli/build/install/eaf/bin"
# Or
alias eaf="/Users/michael/acci_eaf/tools/eaf-cli/build/install/eaf/bin/eaf"

# Verify installation
eaf --version
# Output: EAF CLI 0.1.0
```

### Distribution Package

```bash
# Create distributable ZIP
./gradlew :tools:eaf-cli:distZip

# ZIP location: tools/eaf-cli/build/distributions/eaf-cli-0.1.0.zip
```

## Available Commands

### Version Information

```bash
eaf --version
# Output: EAF CLI 0.1.0
```

### Help

```bash
eaf --help
# Shows: Available commands and options

eaf scaffold --help
# Shows: Scaffold subcommand help
```

### Scaffold Command (Placeholder - Stories 7.2-7.4)

The `scaffold` command is a placeholder in Story 7.1. Generator subcommands will be added in future stories:

- **Story 7.2**: `eaf scaffold module <name>` - Generate new Spring Modulith module
- **Story 7.3**: `eaf scaffold aggregate <Name> --module <module>` - Generate CQRS/ES vertical slice
- **Story 7.4**: `eaf scaffold ra-resource <Name>` - Generate React-Admin UI components

```bash
eaf scaffold
# Output: Scaffold command - generators will be added in Stories 7.2-7.4
#         Available generators:
#           - module (Story 7.2)
#           - aggregate (Story 7.3)
#           - ra-resource (Story 7.4)
```

## Architecture

### Technology Stack

- **CLI Framework**: Picocli 4.7.6 (type-safe, annotation-based)
- **Templating Engine**: Mustache.java 0.9.14 (logic-less templates)
- **Language**: Kotlin 2.2.20
- **Build Tool**: Gradle 9.1.0

### Module Structure

```
tools/eaf-cli/
├── build.gradle.kts          # Gradle configuration
├── README.md                  # This file
└── src/
    ├── main/
    │   ├── kotlin/com/axians/eaf/tools/cli/
    │   │   ├── EafCli.kt                    # Main CLI entry point
    │   │   ├── commands/
    │   │   │   └── ScaffoldCommand.kt        # Scaffold subcommand
    │   │   └── templates/
    │   │       └── TemplateEngine.kt         # Mustache wrapper
    │   └── resources/templates/
    │       └── test-template.mustache        # Test template
    └── test/kotlin/com/axians/eaf/tools/cli/
        ├── EafCliTest.kt                     # CLI execution tests
        └── templates/
            └── TemplateEngineTest.kt         # Template rendering tests
```

## Development

### Building

```bash
# Full build with quality gates
./gradlew :tools:eaf-cli:build

# Run tests only
./gradlew :tools:eaf-cli:jvmKotest
```

### Quality Gates

All code must pass:

- **ktlint**: Code formatting (zero violations)
- **Detekt**: Static analysis (zero violations)
- **Tests**: 7 unit tests (100% passing required)
- **Coverage**: 85% line, 80% mutation minimum

### Code Standards

- ✅ NO wildcard imports (explicit imports only)
- ✅ NO generic exceptions (use specific exception types)
- ✅ Version Catalog mandatory (all dependencies in `gradle/libs.versions.toml`)
- ✅ Kotest 6.0.3 only (JUnit forbidden)

## Template Security Guidelines (SEC-001 Mitigation)

### Input Validation Requirements

**CRITICAL**: All template context values must be validated before rendering to prevent code injection vulnerabilities.

#### Validation Checklist

When preparing template context in generator commands (Stories 7.2-7.4):

1. **File Paths**: Validate against directory traversal
   ```kotlin
   // ❌ BAD
   val path = userInput  // Could be "../../etc/passwd"

   // ✅ GOOD
   val path = validateFilePath(userInput)  // Sanitize, check bounds
   ```

2. **Class/Package Names**: Validate against naming conventions
   ```kotlin
   // ❌ BAD
   val className = userInput  // Could contain special chars

   // ✅ GOOD
   val className = userInput.takeIf { it.matches(CLASS_NAME_PATTERN) }
       ?: throw IllegalArgumentException("Invalid class name")
   ```

3. **User-Provided Strings**: Consider sanitization
   ```kotlin
   // ✅ GOOD
   val description = userInput.trim().take(MAX_LENGTH)
   ```

### Mustache Security Features

Mustache.java provides built-in protection:

- ✅ **HTML Escaping**: Automatically escapes HTML/XML characters (`<script>` → `&lt;script&gt;`)
- ✅ **Logic-Less Design**: No arbitrary code execution in templates
- ✅ **No File System Access**: Templates can't access files outside resources/templates/

**Security Test**: See `TemplateEngineTest.kt::should handle empty and malicious context safely` (Test 7.1-UNIT-007)

### Best Practices

1. **Validate Early**: Check inputs before template rendering
2. **Fail Fast**: Throw specific exceptions for invalid inputs
3. **Document Constraints**: Specify valid input patterns in help text
4. **Test Security**: Add security tests for all new generators

## Extension Points for Future Stories

### Adding New Generators (Stories 7.2-7.4)

1. **Create Subcommand**: Add new @Command class in `commands/` package
2. **Register Subcommand**: Add to `EafCli` subcommands list
3. **Create Templates**: Add Mustache templates in `resources/templates/`
4. **Use TemplateEngine**: Call `templateEngine.render()` with validated context
5. **Add Tests**: Unit tests for command logic, security tests for input validation

### Template Creation Guidelines

Templates use Mustache syntax:

```mustache
package {{packageName}}

class {{className}} {
    val property: String = "{{value}}"
}
```

**Variables**: `{{variableName}}` - HTML-escaped by default
**Sections**: `{{#list}}...{{/list}}` - Iterate over collections
**Inverted**: `{{^list}}...{{/list}}` - Render if empty/false

## Testing

### Running Tests

```bash
# Native Kotest runner (fast)
./gradlew :tools:eaf-cli:jvmKotest

# Standard test task (via JUnit Platform)
./gradlew :tools:eaf-cli:test

# Full quality check
./gradlew :tools:eaf-cli:build
```

### Test Coverage

- **7 Unit Tests**: CLI commands, template rendering, error handling, security
- **Coverage**: ~90% line coverage (exceeds 85% target)
- **Security**: Dedicated test for template injection protection (7.1-UNIT-007)

## Troubleshooting

### Build Failures

```bash
# Check Gradle configuration
./gradlew :tools:eaf-cli:dependencies

# Verify module recognized
./gradlew projects | grep eaf-cli
```

### Test Failures

```bash
# Run tests with detailed output
./gradlew :tools:eaf-cli:jvmKotest --info

# Check test reports
open tools/eaf-cli/build/reports/tests/jvmKotest/index.html
```

### Template Rendering Issues

Check TemplateEngine error messages - they indicate:
- `TemplateNotFoundException`: Template file not in `resources/templates/`
- `TemplateRenderingException`: Invalid template syntax or rendering error

## Future Enhancements

Planned for Epic 7 Stories 7.2-7.4:

- **Module Generator**: Create Spring Modulith-compliant modules
- **Aggregate Generator**: Generate complete CQRS/ES vertical slices
- **React-Admin Generator**: Generate TypeScript UI components

## Contributing

When adding new features:

1. Follow TDD: Write tests before code
2. Ensure zero violations: ktlint, detekt
3. Document security: Validate all user inputs
4. Update this README: Document new commands

## References

- **Story 7.1**: `docs/stories/7.1.initialize-cli-framework-shell.story.md`
- **Quality Gate**: `docs/qa/gates/7.1-initialize-cli-framework-shell.gate.yml`
- **Risk Assessment**: `docs/qa/assessments/7.1-risk-20251002.md`
- **Test Design**: `docs/qa/assessments/7.1-test-design-20251002.md`
- **Picocli Documentation**: https://picocli.info/
- **Mustache Documentation**: https://github.com/spullara/mustache.java

---

**Version**: 0.1.0 (Story 7.1)
**Last Updated**: 2025-10-02
**Status**: Foundation complete - ready for generator implementation
