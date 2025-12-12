# Story 3.3: provisioning-trigger-on-approval

Status: done

## Story

As a **system**,
I want to automatically start VM provisioning when a request is approved,
So that no manual intervention is needed after approval.

## Acceptance Criteria

1. **Given** an admin approves a VM request
   **When** `VmRequestApproved` event is persisted
   **Then** a `ProvisionVmCommand` is automatically dispatched via a Process Manager / Saga

2. **And** the command contains:
   - requestId (correlation)
   - tenantId
   - vmSpec (name, size, project, network)

3. **And** the `ProvisionVmCommand` handler creates a new `VmAggregate`
   **And** the `VmAggregate` emits a `VmProvisioningStarted` event
   **And** this event triggers the actual `VspherePort.createVm()` call

4. **And** provisioning starts within 5 seconds of approval
5. **And** request status changes to "Provisioning"
6. **And** timeline event added: "Provisioning started"
7. **And** if VMware config missing, error event added: "VMware not configured"

## Tasks / Subtasks

- [x] **Domain:** Implement `VmAggregate` in `dcm-domain` (Root of the VM lifecycle).
  - [x] Handle `ProvisionVmCommand` to create the aggregate and emit `VmProvisioningStarted`.
- [x] **Application:** Implement `VmProvisioningSaga` (or Event Listener) in `dcm-application`.
  - [x] Listen for `VmRequestApprovedEvent`.
  - [x] Dispatch `ProvisionVmCommand` to the `VmAggregate`.
- [x] **Infrastructure:** Implement the `VmProvisioningStarted` event handler in `dcm-infrastructure` (or `dcm-application` delegating to Port).
  - [x] Call `VspherePort.createVm()` (Basic call only - complex logic reserved for Story 3.4).
- [x] **Application:** Update `VmRequest` status to "Provisioning" based on `VmProvisioningStarted` event (or Saga coordination).
- [x] **Tests:** Write integration tests in `dcm-app` (Assembly Module).
  - [x] Verify `VmRequestApproved` -> `VmAggregate` created -> `VspherePort` called.
  - [x] Use `VcsimAdapter` configured in `dcm-app` test context.
- [x] Ensure idempotency: Verify that duplicate `VmRequestApprovedEvent` do not create duplicate `VmAggregate`s.

## Dev Notes

### Relevant Architecture Patterns and Constraints
-   **Aggregate Boundaries (CRITICAL):** This story introduces the `VmAggregate`. The `VmRequestAggregate` handles the *request* lifecycle (Draft -> Approved). The `VmAggregate` handles the *resource* lifecycle (Provisioning -> Running).
-   **Saga Pattern:** Use a Saga or Process Manager to coordinate:
    1.  `VmRequestApproved` (Event) -> Dispatch `ProvisionVmCommand`
    2.  `ProvisionVmCommand` -> Create `VmAggregate` -> Emit `VmProvisioningStarted`
    3.  `VmProvisioningStarted` -> Call `VspherePort`
-   **Module Dependency Rules:** `dcm-application` cannot see `dcm-infrastructure`. Integration tests connecting the full flow (Event -> Command -> Adapter) **MUST reside in `dcm-app`**.
-   **Scope Clarity:**
    -   **Story 3.3 (This Story):** Wires the flow, creates the `VmAggregate`, and ensures the "Start Provisioning" signal reaches the Infrastructure.
    -   **Story 3.4 (Next Story):** Implements the complex `VspherePort.createVm` logic (cloning, waiting for IP, customization). For 3.3, a simple stub or basic call is sufficient.
-   **Reactive Model:** Event handling and command dispatch should be non-blocking.
-   **Idempotency:** The Saga/Listener must check if a `VmAggregate` already exists for this Request ID before creating a new one.

### Source Tree Components to Touch
-   `dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vm/` (New `VmAggregate`, `VmProvisioningStarted` event)
-   `dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vm/` (Command Handler, Saga/Listener)
-   `dcm/dcm-app/src/test/kotlin/de/acci/dcm/app/integration/` (Full flow integration tests)

### Testing Standards Summary
-   **Tests First:** Write integration tests for the event handler and command dispatching flow before implementation.
-   **Testcontainers:** Use `VCSIM` via `VcsimAdapter` for integration tests of the provisioning flow.
-   **Coverage:** Maintain ≥70% line coverage and ≥70% mutation score.
-   **MockK:** Use `any()` for all parameters when stubbing functions with default arguments.
-   **Isolation:** Ensure test isolation, particularly for event processing.

### Project Structure Notes

-   **Alignment with unified project structure:** Adhere to module boundaries as defined in `project-context.md`.
-   **Explicit Imports Only:** Avoid wildcard imports.
-   **Named Arguments:** Use named arguments for functions with more than two parameters for clarity.
-   **Domain Exceptions:** Employ domain-specific exceptions with context for error reporting.

## References

-   [Source: epics.md#Story 3.3]
-   [Source: epics.md#Epic 3 Risk Mitigation]
-   [Source: project-context.md#Architecture Rules (Konsist-Enforced)]
-   [Source: project-context.md#Kotlin Style Rules (Zero-Tolerance)]
-   [Source: project-context.md#Security Patterns (Multi-Tenant)]
-   [Source: project-context.md#Testing Rules (CI-Blocking)]
-   [Source: project-context.md#VMware VCF SDK 9.0 Patterns]
-   [Source: project-context.md#Anti-Patterns (Prohibited)]
-   [Source: 3-2-vsphere-api-client.md#Dev Notes]
-   [Source: project-context.md#jOOQ Code Generation (Critical Gotchas)]

### Deferred Enhancements (Post-MVP)

The following code review suggestions were deferred as non-critical for MVP but may be relevant for future iterations:

1. **VmAggregate Placeholder Defaults → `lateinit var`**
   - Current: Uses sentinel values (all-zero UUIDs, "placeholder" name) as initial state
   - Suggested: Use `lateinit var` to enforce that properties are only set after `VmProvisioningStarted` is applied
   - Rationale for deferral: Factory method `startProvisioning()` already enforces the invariant; `reconstitute()` requires stored events
   - Future benefit: Stronger compile-time safety, prevents misuse of uninitialized aggregates

2. **Domain-Specific Exception for State Transitions**
   - Current: `check(status == VmStatus.PROVISIONING)` throws generic `IllegalStateException`
   - Suggested: Create `VmProvisioningStateException` with `vmId` and `currentStatus` context
   - Rationale for deferral: Current error message is descriptive; exception type distinction not yet needed by callers
   - Future benefit: Enables fine-grained error handling in application layer

3. **JWT Test Helper Consolidation**
   - Current: `VmProvisioningIntegrationTest` and `VmRequestIntegrationTest` have similar JWT creation logic
   - Suggested: Extract to shared test utility in testFixtures module
   - Rationale for deferral: Tests work correctly; duplication is contained
   - Future benefit: Single source of truth for test token configuration

4. **WebTestClient `jsonPath()` for ID Extraction**
   - Current: Uses `substringAfter`/`substringBefore` string parsing for extracting response IDs
   - Suggested: Use `jsonPath("$.id")` for type-safe JSON extraction
   - Rationale for deferral: Current approach works; minor robustness improvement
   - Future benefit: More resilient to JSON structure changes

## Dev Agent Record

### Context Reference
-   **Story 3.2 Learnings (`3-2-vsphere-api-client.md`):**
    -   `VsphereSessionManager` for isolated, tenant-scoped vSphere sessions (`ConcurrentHashMap`).
    -   `VsphereClient` is a stateless singleton using `VsphereSessionManager`.
    -   `VspherePort` abstraction is established (`VcenterAdapter`, `VcsimAdapter`).
    -   Coroutine-based session keepalive implemented.
    -   Mandatory credential redaction in logging.
    -   Resilience4j Circuit Breaker implemented in `VsphereClient`.
    -   `VcsimTestFixture` enhanced for realistic SOAP API calls, including dynamic port handling.
    -   VCF SDK Port 443 workaround for dynamic Testcontainer ports (direct JAX-WS config).
    -   ARM64 VCSIM limitation: tests skip on ARM64 with provided workarounds for local dev and CI.

### Agent Model Used
gemini-1.5-flash

### Git Intelligence Summary
-   Recent commits confirm that foundational work for VMware integration (Stories 3.1, 3.1.1, 3.2), including VCF SDK migration and vSphere API client implementation, has been completed and merged. This provides a stable base for Story 3.3.

### Latest Tech Information
-   **Spring Boot 3.5 `ApplicationEventPublisher`:** No significant breaking changes or known issues directly impacting event publishing. The existing approach of using `ApplicationEventPublisher` or a Saga/Process Manager for event handling is still valid.
-   **VMware VCF SDK 9.0 Provisioning:**
    -   Awareness of potential vDS port issues in guest clusters during VM provisioning is noted (relevant for Story 3.4, but good to keep in mind for general robustness).
    -   Successful provisioning heavily relies on correct underlying VCF deployment and network configuration. This reinforces the importance of accurate `vmSpec` details for the `ProvisionVmCommand`.
    -   The SDK supports advanced VM management, and new APIs facilitate VM customization, aligning with our VM template approach.

### Debug Log References

### Completion Notes List
- Implemented automatic VM provisioning trigger on approval via Process Manager/Saga pattern
- Created VmAggregate with VmProvisioningStarted/VmProvisioningFailed events
- Added idempotency check (status == APPROVED) to prevent duplicate provisioning
- Wired Spring ApplicationEventPublisher with coroutine-based async handlers
- Integration test verifies full flow: VmRequestApproved → VmAggregate → VspherePort

### File List
dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vm/VmId.kt
dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vm/VmStatus.kt
dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vm/events/VmProvisioningStarted.kt
dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vm/VmAggregate.kt
dcm/dcm-domain/src/test/kotlin/de/acci/dcm/domain/vm/VmAggregateTest.kt
dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vmrequest/VmRequestAggregate.kt
dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vmrequest/events/VmRequestProvisioningStarted.kt
dcm/dcm-domain/src/test/kotlin/de/acci/dcm/domain/vmrequest/VmRequestAggregateTest.kt
dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vm/ProvisionVmCommand.kt
dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vm/ProvisionVmHandler.kt
dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vm/VmProvisioningListener.kt
dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vm/VmEventDeserializer.kt
dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vm/VmRequestStatusUpdater.kt
dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vm/TriggerProvisioningHandler.kt
dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vmrequest/MarkVmRequestProvisioningCommand.kt
dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vmrequest/MarkVmRequestProvisioningHandler.kt
dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vmrequest/CreateVmRequestHandler.kt
dcm/dcm-application/src/test/kotlin/de/acci/dcm/application/vm/ProvisionVmHandlerTest.kt
dcm/dcm-application/src/test/kotlin/de/acci/dcm/application/vm/VmRequestStatusUpdaterTest.kt
dcm/dcm-application/src/test/kotlin/de/acci/dcm/application/vm/TriggerProvisioningHandlerTest.kt
dcm/dcm-application/src/test/kotlin/de/acci/dcm/application/vm/VmProvisioningListenerTest.kt
dcm/dcm-application/src/test/kotlin/de/acci/dcm/application/vmrequest/MarkVmRequestProvisioningHandlerTest.kt
dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vm/events/VmProvisioningFailed.kt
dcm/dcm-infrastructure/src/main/kotlin/de/acci/dcm/infrastructure/eventsourcing/JacksonVmEventDeserializer.kt
dcm/dcm-infrastructure/src/main/kotlin/de/acci/dcm/infrastructure/eventsourcing/JacksonVmRequestEventDeserializer.kt
dcm/dcm-infrastructure/src/main/kotlin/de/acci/dcm/infrastructure/eventsourcing/PublishingEventStore.kt
dcm/dcm-infrastructure/src/test/kotlin/de/acci/dcm/infrastructure/eventsourcing/JacksonVmEventDeserializerTest.kt
dcm/dcm-infrastructure/src/test/kotlin/de/acci/dcm/infrastructure/eventsourcing/JacksonVmRequestEventDeserializerTest.kt
dcm/dcm-api/src/main/kotlin/de/acci/dcm/api/security/SecurityConfig.kt
dcm/dcm-app/src/main/kotlin/de/acci/dcm/config/ApplicationConfig.kt
dcm/dcm-app/src/main/kotlin/de/acci/dcm/app/listeners/VmProvisioningListeners.kt
dcm/dcm-app/src/test/kotlin/de/acci/dcm/vmrequest/VmProvisioningIntegrationTest.kt
dcm/dcm-app/src/test/kotlin/de/acci/dcm/vmrequest/VmRequestIntegrationTest.kt
