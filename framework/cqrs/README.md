# EAF CQRS Framework Module

Axon Framework integration for Command Query Responsibility Segregation (CQRS) and Event Sourcing patterns.

## Overview

This module provides the core CQRS infrastructure for the Enterprise Application Framework (EAF), built on Axon Framework 4.12.1. It configures CommandGateway and QueryGateway beans for dispatching commands and executing queries.

## Features

- **CommandGateway**: Dispatch commands to command handlers
- **QueryGateway**: Execute queries against query handlers
- **Auto-Configuration**: Leverages Axon Spring Boot Starter for automatic setup
- **Bus Infrastructure**: CommandBus, EventBus, and QueryBus configured by Axon auto-configuration

## Dependencies

- **Axon Framework 4.12.1**: CQRS/ES framework
- **Spring Boot 3.5.7**: Application framework
- **Spring Modulith 1.4.4**: Module boundary enforcement
- **Kotlin 2.2.21**: Primary language

## Usage

### Injecting Gateways

```kotlin
@Service
class MyService(
    private val commandGateway: CommandGateway,
    private val queryGateway: QueryGateway
) {
    fun createWidget(name: String): UUID {
        val command = CreateWidgetCommand(UUID.randomUUID(), name)
        return commandGateway.sendAndWait(command)
    }

    fun getWidget(id: UUID): Widget {
        val query = GetWidgetQuery(id)
        return queryGateway.query(query, ResponseTypes.instanceOf(Widget::class.java))
            .join()
    }
}
```

### Configuration

No additional configuration is required. The module uses Axon Spring Boot Starter auto-configuration to set up:

- CommandBus (default: AsynchronousCommandBus)
- EventBus (default: SimpleEventBus)
- QueryBus (default: SimpleQueryBus)

## Testing

Run tests:
```bash
./gradlew :framework:cqrs:test
```

Tests execute in < 1 second and verify:
- CommandGateway bean creation
- QueryGateway bean creation
- Gateway functionality

## Module Structure

```
framework/cqrs/
├── src/main/kotlin/com/axians/eaf/framework/cqrs/
│   └── config/
│       └── AxonConfiguration.kt      # Gateway configuration
└── src/test/kotlin/com/axians/eaf/framework/cqrs/
    └── config/
        └── AxonConfigurationTest.kt  # Unit tests
```

## Related Documentation

- [PRD Section FR003](../../docs/PRD.md): Event Store Requirements
- [Architecture Section 7](../../docs/architecture.md): CQRS/Event Sourcing Stack
- [Tech Spec Epic 2](../../docs/tech-spec-epic-2.md): Axon Framework Integration
- [Axon Framework Documentation](https://docs.axoniq.io/reference-guide/)

## Notes

- Part of Epic 2 - Walking Skeleton (CQRS/Event Sourcing Core)
- Story 2.1: Axon Framework Core Configuration
- Axon Framework v5 migration planned for Q3-Q4 2026
