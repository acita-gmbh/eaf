# Recommendation: Re-enable Disabled Integration Tests (Future Story)

## Current Status
The 5 disabled integration tests in `products/widget-demo/src/integration-test/kotlin-disabled/` were successfully migrated with package updates but remain disabled due to infrastructure requirements.

## Why Still Disabled
1. **Infrastructure Dependencies**: Tests require PostgreSQL, Keycloak, Redis Testcontainers
2. **Application Reference**: Originally referenced LicensingServerApplication (Epic 8 - doesn't exist yet)
3. **Testcontainers Configuration**: Tests use shared TestContainers pattern that may need updates
4. **Complexity**: Walking Skeleton E2E tests with full CQRS flow validation

## Recommendation for Future Story

**Story Title**: "Re-enable Widget Integration Tests in widget-demo"

**Approach**:
1. Update all test references from LicensingServerApplication to WidgetDemoApplication
2. Verify TestContainers configuration in shared/testing
3. Add integration test dependencies to widget-demo if missing
4. Create test-specific application.yml profile
5. Validate each test individually with infrastructure running
6. Add to CI pipeline with infrastructure setup

**Estimated Effort**: 4-6 hours (one story)

**Priority**: Medium (improves test coverage but not blocking)

**Benefits**:
- Early regression detection for Widget domain
- Validates CQRS flow end-to-end
- Reference pattern for Epic 8 integration tests
- Improved confidence in widget-demo as framework validation tool

## Tests to Re-enable (5 total)
1. WidgetIntegrationTest.kt - Aggregate lifecycle tests
2. WidgetApiIntegrationTest.kt - REST API integration
3. WidgetWalkingSkeletonIntegrationTest.kt - Epic 2 E2E validation
4. persistence/WidgetEventStoreIntegrationTest.kt - Event store tests
5. projections/WidgetEventProcessingIntegrationTest.kt - Projection handler tests

All tests have been migrated with correct package declarations (products.widgetdemo.*) and are ready for re-enablement work.
