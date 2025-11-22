# Axon Framework 5.0 Migration Status

**Status**: 🚧 IN PROGRESS
**Started**: 2025-11-22
**Branch**: `claude/spring-7-boot-4-upgrade-01NKnFygygnNLAwDMsVStz4b`

## Migration Checklist

### Phase 1: Core API Changes
- [ ] Update aggregate annotations (`@Aggregate` → `@EventSourced`)
- [ ] Add `@EntityCreator` to creational command handlers
- [ ] Update import statements for Axon 5.0 packages
- [ ] Remove `AggregateLifecycle` static calls (if needed)

### Phase 2: Handler Signatures
- [ ] Update command handlers (add `ProcessingContext`, return `MessageStream`)
- [ ] Update event sourcing handlers (keep as-is or return new instance for immutability)
- [ ] Update event handlers (add `ProcessingContext`, return `MessageStream<Void>`)
- [ ] Update query handlers (add `ProcessingContext`, return `MessageStream`)

### Phase 3: Configuration
- [ ] Update Axon configuration to `ApplicationConfigurer` API
- [ ] Update event store configuration (JPA → AggregateBasedJpa)
- [ ] Update event processing configuration
- [ ] Update interceptor registration

### Phase 4: Testing
- [ ] Update test fixtures (`AggregateTestFixture` → `AxonTestFixture`)
- [ ] Fix compilation errors
- [ ] Run unit tests
- [ ] Run integration tests

## Files Requiring Changes

### Production Code (8 files)

**Aggregates:**
1. `products/widget-demo/src/main/kotlin/com/axians/eaf/products/widget/domain/Widget.kt`
   - [x] Change `@Aggregate` → `@EventSourced`
   - [x] Add `@EntityCreator` to constructor command handler
   - [ ] Add `ProcessingContext` to instance command handlers
   - [ ] Return `MessageStream<Void>` from command handlers

**Event Handlers:**
2. `products/widget-demo/src/main/kotlin/com/axians/eaf/products/widget/query/WidgetProjectionEventHandler.kt`
   - [ ] Add `ProcessingContext` parameter to `@EventHandler` methods
   - [ ] Return `MessageStream<Void>` instead of `Unit`

**Query Handlers:**
3. `products/widget-demo/src/main/kotlin/com/axians/eaf/products/widget/query/WidgetQueryHandler.kt`
   - [ ] Add `ProcessingContext` parameter to `@QueryHandler` methods
   - [ ] Return `MessageStream<T>` instead of direct values

**Configuration:**
4. `framework/multi-tenancy/src/main/kotlin/com/axians/eaf/framework/multitenancy/config/TenantEventProcessingConfiguration.kt`
   - [ ] Update to `ApplicationConfigurer` API

**Interceptors:**
5. `framework/multi-tenancy/src/main/kotlin/com/axians/eaf/framework/multitenancy/TenantContextEventInterceptor.kt`
   - [ ] Update signature to accept `ProcessingContext`
   - [ ] Return `MessageStream` instead of direct result

### Test Code (4 files)

6. `framework/multi-tenancy/src/integration-test/kotlin/com/axians/eaf/framework/multitenancy/test/TestAggregate.kt`
   - [ ] Same aggregate changes as production code

7. `products/widget-demo/src/test/kotlin/com/axians/eaf/products/widget/domain/WidgetAggregateTest.kt`
   - [ ] Replace `AggregateTestFixture` with `AxonTestFixture`
   - [ ] Update fixture configuration

8. `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/test/config/AxonTestConfiguration.kt`
   - [ ] Update Axon configuration beans

9. `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/test/config/RbacTestContainersConfig.kt`
   - [ ] Review for Axon 5 compatibility

## Known Issues

### Network Limitations
- Cannot build locally due to sandbox environment network restrictions
- CI build will validate changes
- Changes made based on Axon 5.0 API documentation

### Import Changes Required
Axon 5.0 has reorganized packages. Expected import changes:
- `org.axonframework.spring.stereotype.Aggregate` → `org.axonframework.spring.stereotype.EventSourced`
- New annotation: `org.axonframework.modelling.command.EntityCreator`
- May need: `org.axonframework.messaging.MessageStream`
- May need: `org.axonframework.messaging.ProcessingContext`

## Migration Notes

### Approach
Given network limitations preventing local builds:
1. Make changes based on documented API changes
2. Commit incrementally with clear descriptions
3. Rely on CI build for validation
4. Fix compilation errors based on CI feedback

### Decision Points
- **Immutable Entities**: Keeping mutable aggregates for now (Kotlin data class migration deferred)
- **MessageStream**: Wrapping results with `MessageStream.just()` and `MessageStream.empty()`
- **ProcessingContext**: Adding as parameter to all handler methods

## Next Steps

1. Update Widget aggregate
2. Update event/query handlers
3. Update configuration
4. Update tests
5. Push and await CI feedback
6. Iterate based on compilation errors

## References

- [Axon 5 API Changes](https://github.com/AxonFramework/AxonFramework/blob/axon-5.0.0/axon-5/api-changes.md)
- [Upgrade Plan](docs/axon-5-spring-boot-4-upgrade-plan.md)
- [Spring Boot 4 Upgrade Plan](docs/spring-boot-4-upgrade-plan.md)
