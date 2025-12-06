# Story 3.2: vSphere API Client

Status: done

## Story

As a **developer**,
I want a robust vSphere API client,
So that I can interact with VMware infrastructure reliably.

## Acceptance Criteria

1.  **Given** multiple tenants have configured VMware connections (from Story 3.1)
    **When** API operations are performed
    **Then** a `VsphereSessionManager` maintains isolated sessions per tenant (using `ConcurrentHashMap<TenantId, VsphereSession>`)
    **And** `VsphereClient` delegates to this manager to get the correct session for the current tenant context
    **And** concurrent requests from different tenants never share or overwrite sessions

2.  **And** `VsphereClient` provides the following operations, implemented using the official VCF SDK:
    -   `listDatacenters(): Result<List<Datacenter>, VsphereError>`
    -   `listClusters(datacenter): Result<List<Cluster>, VsphereError>`
    -   `listDatastores(cluster): Result<List<Datastore>, VsphereError>`
    -   `listNetworks(datacenter): Result<List<Network>, VsphereError>`
    -   `listResourcePools(cluster): Result<List<ResourcePool>, VsphereError>`
    -   `createVm(spec: VmSpec): Result<VmId, ProvisioningError>`
    -   `getVm(vmId): Result<VmInfo, NotFoundError>`
    -   `deleteVm(vmId): Result<Unit, DeletionError>`

3.  **And** all vSphere API operations have a configurable timeout (default: 60s)
4.  **And** connection reuse with **active session keepalive** implemented as a **Coroutine-based background job** (using `delay()` loop, NOT blocking threads) calling `SessionManager.CurrentTime` every 15 mins to prevent session expiry.
5.  **And** all API calls are logged with the `CorrelationId` (from EAF Core, Story 1.2), **strictly redacting** sensitive data (passwords, session IDs) to prevent security leaks.
6.  **And** a circuit breaker pattern (e.g., Resilience4j) trips after 5 consecutive failures to protect vCenter
7.  **And** list operations enforce **server-side pagination** (or client-side if SDK limited) to prevent OOM on large inventories (max 1000 items per page).

**VCSIM Enhancement Required (Story 1.10):**
8.  **Given** `VcsimTestFixture` is being used for integration tests
    **When** `createVm()`, `getVm()`, `deleteVm()` are called
    **Then** `VcsimTestFixture` extends to make actual VCSIM SOAP API calls via the `/sdk` endpoint (e.g., `CloneVM_Task`, `RetrieveProperties`, `Destroy_Task`) for realistic testing.
    *(Note: Must handle non-standard ports for Testcontainers, overriding default SDK port 443 constraints).*

## Tasks / Subtasks

- [x] **Session Management:** Implement `VsphereSessionManager` with `ConcurrentHashMap<TenantId, VsphereSession>` to manage multi-tenant connection state.
- [x] **Core Client:** Implement `VsphereClient` class within `dvmm-infrastructure/vmware` as a **stateless** Singleton Spring bean that delegates to `VsphereSessionManager`.
- [x] **Adapter Pattern:** Implement `VspherePort` interface (Application) and `VcenterAdapter` (Infrastructure) + `VcsimAdapter` (Test).
- [x] **Connection Lifecycle:** Implement `connect(config)` with **coroutine-based active keepalive** and session reuse.
- [x] **Read Operations:** Implement `listDatacenters`, `listClusters`, `listDatastores`, `listNetworks`, `listResourcePools` with **pagination safeguards**.
- [x] **Write Operations:** Implement `createVm`, `getVm`, `deleteVm`.
- [x] **Resilience:** Integrate configurable timeout and **Resilience4j Circuit Breaker** (5 failure threshold).
- [x] **Observability:** Implement logging with `CorrelationId` and **mandatory credential redaction**.
- [x] **Test Infrastructure:** Update `VcsimTestFixture` in `eaf-testing` to support actual SOAP calls.
  - *Critical:* Configure SDK/Stub to accept dynamic Testcontainers ports (overriding default 443).
- [x] **Verification:** Write integration tests for `VsphereClient` using `VcsimAdapter` and the enhanced fixture.

## Dev Notes

### Relevant Architecture Patterns and Constraints
- **Hexagonal Architecture:** `VsphereClient` implements `VspherePort` (Output Port) in `dvmm-infrastructure`.
- **Reactive Model:** Wrap all blocking SDK calls in `withContext(Dispatchers.IO)` (ADR-003).
- **Concurrency:** Use `ConcurrentHashMap` for session storage. Keepalive jobs must use `launch` in a dedicated `CoroutineScope` (e.g., `GlobalScope` or a custom service scope), not blocking threads.
- **Error Handling:** Use `Result<T, E>` exclusively. Do not throw exceptions across port boundaries.
- **Bean Lifecycle:** `VsphereClient` is a **Singleton Facade**. `VsphereSession` objects are **Stateful** and managed by `VsphereSessionManager`.
- **Security:** **NEVER** log `SessionID` headers or `login` arguments. Use a log filter or interceptor to redact these.

### VMware VCF SDK 9.0 Specifics
- **Artifact:** `com.vmware.sdk:vsphere-utils:9.0.0.0`.
- **PropertyCollector:** Use `PropertySpec` + `ObjectSpec` + `FilterSpec` for efficient fetching.
- **SearchIndex:** Use inventory paths ("Datacenter/host/Cluster") for navigation.
- **Port 443 Limitation & Fix:** The default `VcenterClientFactory` often assumes port 443. For `VcsimTestFixture` (which uses random high ports via Testcontainers):
  - You must manually configure the underlying `StubConfiguration` or `BindingProvider.ENDPOINT_ADDRESS_PROPERTY` to use the dynamic mapped port.
  - Do not rely on the default factory if it doesn't support custom ports.

### Source Tree Components to Touch
- `dvmm/dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/vmware/` (Client, Adapters, SessionManager)
- `dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vmware/` (Port Interface)
- `eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/vcsim/VcsimTestFixture.kt` (VCSIM Enhancements)
- `dvmm/dvmm-infrastructure/src/test/kotlin/integration/vmware/` (Tests)

### Testing Standards Summary
- **Tests First:** Write integration tests with `VCSIM` before implementation.
- **Coverage:** ≥70% line coverage, ≥70% mutation score.
- **VCSIM:** The `VcsimTestFixture` must use the *actual* SOAP API for `createVm` (via `CloneVM_Task` mock) to verify the client's SOAP message generation, not just a local mock.

### References
- [Source: epics.md#Story 3.2]
- [Source: project-context.md#VMware VCF SDK 9.0 Patterns]
- [Source: project-context.md#Logging Strategy]

## Dev Agent Record

### Context Reference
- `docs/project-context.md`: Tech stack, SDK patterns, testing rules.
- `docs/architecture.md`: ADRs, module dependencies.
- `docs/security-architecture.md`: Credential protection rules.

### Agent Model Used
gemini-1.5-flash (Optimized Context)

### Implementation Notes
- Implemented `VsphereSessionManager` using `ConcurrentHashMap` for thread-safe session storage per tenant.
- Added `VsphereSession` data class to hold `VimPortType` and `ServiceContent`.
- Implemented `touchSession` to support keep-alive functionality.
- Added unit tests covering registration, retrieval, removal, and activity updates.

#### VCF SDK Port 443 Workaround (VcsimTestFixture & VsphereClient)

**Problem:** VCF SDK's `VcenterClientFactory` only supports port 443, which fails with Testcontainers' dynamic ports.

**Solution:** Bypass `VcenterClientFactory` entirely and configure JAX-WS directly:
1. Create `VimService()` to get `VimPortType`
2. Cast to `BindingProvider` and set `ENDPOINT_ADDRESS_PROPERTY` to the actual VCSIM URL with dynamic port
3. Call `vimPort.retrieveServiceContent()` and `vimPort.login()` manually

**CXF 4.0 SSL Configuration:**
- CXF 4.0 uses `java.net.http.HttpClient` by default, which ignores `HTTPConduit` TLS settings
- Must set `org.apache.cxf.transport.http.forceURLConnection=true` to use legacy `HttpURLConnection` transport
- SSL context must be configured via `SSLContext.setDefault()` BEFORE creating `VimService`

#### VCSIM ARM64 Emulation Limitation

**Problem:** VCSIM only publishes AMD64 Docker images. On ARM64 (Apple Silicon), QEMU emulation causes TLS handshake failures with "Remote host terminated the handshake" errors.

**Solution:** Integration tests skip on ARM64 with a clear message:
```kotlin
init {
    val arch = System.getProperty("os.arch", "")
    if (arch.contains("aarch64") || arch.contains("arm64")) {
        org.junit.jupiter.api.Assumptions.assumeTrue(false,
            "Skipping VCSIM tests on ARM64 - VCSIM AMD64 image fails under QEMU emulation")
    }
}
```

**CI/CD:** Tests run normally on x86_64 GitHub Actions runners. For local ARM64 development, use a remote x86_64 Docker host:
```bash
export DOCKER_HOST=tcp://x86-server:2375
./gradlew :dvmm:dvmm-infrastructure:test --tests "*VsphereClientIntegrationTest*"
```

## File List
- dvmm/dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/vmware/VsphereSession.kt
- dvmm/dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/vmware/VsphereSessionManager.kt
- dvmm/dvmm-infrastructure/src/test/kotlin/de/acci/dvmm/infrastructure/vmware/VsphereSessionManagerTest.kt
- dvmm/dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/vmware/VsphereClient.kt
- dvmm/dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/vmware/VsphereTypes.kt
- dvmm/dvmm-infrastructure/src/test/kotlin/de/acci/dvmm/infrastructure/vmware/VsphereClientTest.kt
- dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vmware/VspherePort.kt
- dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vmware/ConnectionTypes.kt
- dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vmware/VsphereTypes.kt
- dvmm/dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/vmware/VcenterAdapter.kt
- dvmm/dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/vmware/VcsimAdapter.kt
- dvmm/dvmm-infrastructure/src/test/kotlin/de/acci/dvmm/infrastructure/vmware/VcenterAdapterTest.kt
- dvmm/dvmm-infrastructure/src/test/kotlin/integration/vmware/VsphereClientIntegrationTest.kt
- eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/vcsim/VcsimTestFixture.kt (enhanced)
