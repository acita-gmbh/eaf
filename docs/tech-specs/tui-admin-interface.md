# Tech Spec: DVMM TUI Admin Interface

**Author:** Claude (AI Assistant)
**Date:** 2025-11-29
**Status:** Draft
**Epic:** Post-MVP Enhancement
**Related Analysis:** [TUI Analysis](../tui-analysis.md)

---

## Overview

### Problem Statement

Experienced administrators need a fast, keyboard-driven interface for routine operations (approvals, status checks) that works over SSH without requiring a web browser.

### Solution

Implement an optional Terminal User Interface (TUI) module using Lanterna, providing admin-focused workflows with full keyboard navigation.

### Scope

**In Scope:**
- Approval queue management
- System health dashboard
- Audit log viewer
- CSV export functionality
- Request filtering and search

**Out of Scope:**
- VM request creation (web-only)
- User management (web-only)
- Full reporting dashboards (web-only)

---

## Technical Design

### Architecture Overview

The TUI is a **standalone CLI application** that communicates with the main `dvmm-app` via **gRPC streaming over Unix socket**. This enables:

- **Real-time updates** - Server pushes events when requests change state
- **Low latency** - Unix socket (~0.1ms) for local admin access
- **Type safety** - Protobuf contracts shared between client and server
- **Simple deployment** - Single binary, no embedded infrastructure

```
┌──────────────────────┐                    ┌─────────────────────────┐
│  dvmm-tui (CLI)      │  Unix Socket       │  dvmm-app (Spring Boot) │
│                      │  /var/run/dvmm.sock│                         │
│  ┌────────────────┐  │ ←───────────────── │  ┌───────────────────┐  │
│  │ gRPC Client    │  │  Server streaming  │  │ gRPC TUI Service  │  │
│  │ - Commands     │  │  (push events)     │  │                   │  │
│  │ - Subscriptions│  │ ──────────────────→│  │                   │  │
│  └────────────────┘  │  Unary requests    │  └───────────────────┘  │
│                      │                    │           ↓             │
│  ┌────────────────┐  │                    │  ┌───────────────────┐  │
│  │ Lanterna TUI   │  │                    │  │ Application Layer │  │
│  └────────────────┘  │                    │  │ (CQRS/ES)         │  │
└──────────────────────┘                    └─────────────────────────┘
```

### Module Structure

```
dvmm/
├── dvmm-tui-protocol/                      # Shared protobuf definitions
│   ├── build.gradle.kts
│   └── src/main/proto/
│       └── dvmm_tui.proto
│
├── dvmm-tui/                               # TUI client application
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── kotlin/
│       │   │   └── de/acci/dvmm/tui/
│       │   │       ├── DvmmTuiApplication.kt       # Entry point (no Spring)
│       │   │       ├── TuiRunner.kt                # Main loop
│       │   │       │
│       │   │       ├── config/
│       │   │       │   └── TuiConfig.kt            # CLI config loading
│       │   │       │
│       │   │       ├── auth/
│       │   │       │   ├── TuiAuthenticator.kt     # Unix user / token auth
│       │   │       │   └── TuiSecurityContext.kt   # User/Tenant context
│       │   │       │
│       │   │       ├── grpc/
│       │   │       │   ├── TuiGrpcClient.kt        # gRPC channel setup
│       │   │       │   └── EventSubscriber.kt      # Stream handler
│       │   │       │
│       │   │       ├── dsl/                        # Kotlin DSL wrapper
│       │   │       │   ├── DvmmWindow.kt
│       │   │       │   ├── DvmmPanel.kt
│       │   │       │   ├── DvmmTable.kt
│       │   │       │   └── DvmmActionList.kt
│       │   │       │
│       │   │       ├── screens/
│       │   │       │   ├── MainScreen.kt           # Main dashboard
│       │   │       │   ├── ApprovalScreen.kt       # Approval queue
│       │   │       │   ├── RequestDetailScreen.kt  # Single request view
│       │   │       │   ├── HealthScreen.kt         # System health
│       │   │       │   ├── AuditScreen.kt          # Audit log viewer
│       │   │       │   └── LoginScreen.kt          # Authentication
│       │   │       │
│       │   │       └── widgets/
│       │   │           ├── StatusIndicator.kt      # ● OK / ● Error
│       │   │           ├── RequestTable.kt         # VM request list
│       │   │           ├── HealthPanel.kt          # Health metrics
│       │   │           └── KeyHelpBar.kt           # Bottom help bar
│       │   │
│       │   └── resources/
│       │       ├── tui-config.yml                  # Default config
│       │       └── banner-tui.txt                  # ASCII banner
│       │
│       └── test/
│           └── kotlin/
│               └── de/acci/dvmm/tui/
│                   ├── screens/
│                   │   └── ApprovalScreenTest.kt
│                   └── grpc/
│                       └── TuiGrpcClientTest.kt
│
└── dvmm-infrastructure/
    └── src/main/kotlin/de/acci/dvmm/infrastructure/
        └── grpc/
            └── TuiGrpcService.kt                   # Server-side gRPC impl
```

### Dependencies

#### Protocol Module (shared)

```kotlin
// dvmm-tui-protocol/build.gradle.kts
plugins {
    id("eaf.kotlin-conventions")
    id("com.google.protobuf") version "0.9.4"
}

dependencies {
    api("io.grpc:grpc-kotlin-stub:1.4.1")
    api("io.grpc:grpc-protobuf:1.62.2")
    api("com.google.protobuf:protobuf-kotlin:3.25.3")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.62.2"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.1:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
                create("grpckt")
            }
            it.builtins {
                create("kotlin")
            }
        }
    }
}
```

#### TUI Client (standalone, no Spring)

```kotlin
// dvmm-tui/build.gradle.kts
plugins {
    id("eaf.kotlin-conventions")
    application
}

dependencies {
    // Shared protocol definitions
    implementation(project(":dvmm:dvmm-tui-protocol"))

    // gRPC client with Unix socket support
    implementation("io.grpc:grpc-netty-shaded:1.62.2")
    implementation("io.grpc:grpc-kotlin-stub:1.4.1")

    // Netty Unix socket transport
    implementation("io.netty:netty-transport-native-epoll:4.1.107.Final:linux-x86_64")
    implementation("io.netty:netty-transport-native-unix-common:4.1.107.Final")

    // TUI framework
    implementation("com.googlecode.lanterna:lanterna:3.1.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Config loading (lightweight, no Spring)
    implementation("com.sksamuel.hoplite:hoplite-core:2.7.5")
    implementation("com.sksamuel.hoplite:hoplite-yaml:2.7.5")

    // Testing
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.grpc:grpc-testing:1.62.2")
}

application {
    mainClass.set("de.acci.dvmm.tui.DvmmTuiApplicationKt")
}

// Build native executable (optional, for faster startup)
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = "de.acci.dvmm.tui.DvmmTuiApplicationKt" }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
}
```

#### Server-side gRPC (in dvmm-infrastructure)

```kotlin
// dvmm-infrastructure/build.gradle.kts (add to existing)
dependencies {
    // ... existing dependencies ...

    // gRPC server for TUI
    implementation(project(":dvmm:dvmm-tui-protocol"))
    implementation("io.grpc:grpc-netty-shaded:1.62.2")
    implementation("io.grpc:grpc-kotlin-stub:1.4.1")
    implementation("net.devh:grpc-server-spring-boot-starter:3.0.0.RELEASE")
}
```

### Protobuf Service Definition

```protobuf
// dvmm-tui-protocol/src/main/proto/dvmm_tui.proto
syntax = "proto3";
package de.acci.dvmm.tui;

option java_multiple_files = true;
option java_package = "de.acci.dvmm.tui.proto";

// Main TUI service - all operations for the admin interface
service DvmmTuiService {
  // Authentication
  rpc Authenticate(AuthRequest) returns (AuthResponse);

  // Commands (unary)
  rpc ApproveRequest(ApproveCommand) returns (CommandResult);
  rpc RejectRequest(RejectCommand) returns (CommandResult);

  // Queries (unary)
  rpc GetPendingApprovals(GetApprovalsRequest) returns (GetApprovalsResponse);
  rpc GetRequestDetails(GetDetailsRequest) returns (VmRequestDetails);
  rpc GetAuditLog(GetAuditLogRequest) returns (GetAuditLogResponse);

  // Subscriptions (server streaming) - real-time updates
  rpc SubscribeApprovals(SubscribeRequest) returns (stream ApprovalEvent);
  rpc SubscribeHealth(SubscribeRequest) returns (stream HealthUpdate);
}

// === Authentication ===
message AuthRequest {
  oneof method {
    string token = 1;           // JWT token
    string unix_user = 2;       // Local Unix username (server validates)
  }
}

message AuthResponse {
  bool success = 1;
  string user_id = 2;
  string tenant_id = 3;
  repeated string roles = 4;
  string session_token = 5;     // Short-lived session token for subsequent calls
  string error_message = 6;
}

// === Commands ===
message ApproveCommand {
  string request_id = 1;
  optional string comment = 2;
}

message RejectCommand {
  string request_id = 1;
  string reason = 2;            // Required
}

message CommandResult {
  bool success = 1;
  string error_code = 2;
  string error_message = 3;
}

// === Queries ===
message GetApprovalsRequest {
  optional int32 limit = 1;
  optional int32 offset = 2;
}

message GetApprovalsResponse {
  repeated VmRequestSummary requests = 1;
  int32 total_count = 2;
}

message VmRequestSummary {
  string id = 1;
  string vm_name = 2;
  string size = 3;              // S, M, L, XL
  string requester_email = 4;
  int64 submitted_at = 5;       // Unix timestamp
  string project = 6;
}

message GetDetailsRequest {
  string request_id = 1;
}

message VmRequestDetails {
  string id = 1;
  string vm_name = 2;
  string size = 3;
  int32 cpu_cores = 4;
  int32 memory_gb = 5;
  int32 disk_gb = 6;
  string project = 7;
  string requester_email = 8;
  string justification = 9;
  int64 submitted_at = 10;
  string status = 11;
}

message GetAuditLogRequest {
  optional int64 from_timestamp = 1;
  optional int64 to_timestamp = 2;
  optional string event_type = 3;
  optional int32 limit = 4;
}

message GetAuditLogResponse {
  repeated AuditLogEntry entries = 1;
}

message AuditLogEntry {
  int64 timestamp = 1;
  string event_type = 2;
  string actor_id = 3;
  string entity_id = 4;
  string details = 5;
}

// === Subscriptions ===
message SubscribeRequest {
  // Empty - tenant scoped via auth context
}

message ApprovalEvent {
  string request_id = 1;
  ApprovalEventType type = 2;
  string actor_id = 3;
  int64 timestamp = 4;
  optional VmRequestSummary request = 5;  // Included for SUBMITTED events
}

enum ApprovalEventType {
  APPROVAL_EVENT_UNKNOWN = 0;
  SUBMITTED = 1;
  APPROVED = 2;
  REJECTED = 3;
  PROVISIONING_STARTED = 4;
  PROVISIONED = 5;
  FAILED = 6;
}

message HealthUpdate {
  repeated ServiceHealth services = 1;
  int64 timestamp = 2;
}

message ServiceHealth {
  string name = 1;              // e.g., "vmware", "keycloak", "postgres"
  bool healthy = 2;
  int32 response_time_ms = 3;
  optional string error_message = 4;
}
```

### Entry Point (TUI Client)

```kotlin
// DvmmTuiApplication.kt
package de.acci.dvmm.tui

import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.gui2.DefaultWindowManager
import com.googlecode.lanterna.gui2.EmptySpace
import de.acci.dvmm.tui.auth.TuiAuthenticator
import de.acci.dvmm.tui.config.TuiConfig
import de.acci.dvmm.tui.grpc.TuiGrpcClient
import de.acci.dvmm.tui.screens.MainScreen
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    val config = TuiConfig.load()
    val grpcClient = TuiGrpcClient(config.socketPath)

    val terminal = DefaultTerminalFactory().createTerminal()
    val screen = TerminalScreen(terminal).apply { startScreen() }

    try {
        val gui = MultiWindowTextGUI(screen, DefaultWindowManager(), EmptySpace())

        // Authenticate (Unix user or token)
        val authenticator = TuiAuthenticator(grpcClient, config)
        val authContext = authenticator.authenticate(gui, args)

        // Launch main screen with real-time subscriptions
        val mainScreen = MainScreen(grpcClient, authContext)
        mainScreen.show(gui)

    } finally {
        grpcClient.close()
        screen.stopScreen()
        terminal.close()
    }
}
```

### gRPC Client with Unix Socket

```kotlin
// grpc/TuiGrpcClient.kt
package de.acci.dvmm.tui.grpc

import de.acci.dvmm.tui.proto.*
import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.shaded.io.netty.channel.epoll.EpollDomainSocketChannel
import io.grpc.netty.shaded.io.netty.channel.epoll.EpollEventLoopGroup
import io.grpc.netty.shaded.io.netty.channel.unix.DomainSocketAddress
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

class TuiGrpcClient(socketPath: String) : AutoCloseable {

    private val channel: ManagedChannel = NettyChannelBuilder
        .forAddress(DomainSocketAddress(socketPath))
        .eventLoopGroup(EpollEventLoopGroup())
        .channelType(EpollDomainSocketChannel::class.java)
        .usePlaintext()  // Unix socket is already secure (filesystem permissions)
        .build()

    private val stub = DvmmTuiServiceGrpcKt.DvmmTuiServiceCoroutineStub(channel)

    // Session token set after authentication
    private var sessionToken: String? = null

    private fun authenticatedStub() = sessionToken?.let {
        stub.withCallCredentials(SessionTokenCredentials(it))
    } ?: stub

    // === Authentication ===

    suspend fun authenticate(request: AuthRequest): AuthResponse {
        val response = stub.authenticate(request)
        if (response.success) {
            sessionToken = response.sessionToken
        }
        return response
    }

    // === Commands ===

    suspend fun approveRequest(requestId: String, comment: String? = null): CommandResult {
        return authenticatedStub().approveRequest(
            ApproveCommand.newBuilder()
                .setRequestId(requestId)
                .apply { comment?.let { setComment(it) } }
                .build()
        )
    }

    suspend fun rejectRequest(requestId: String, reason: String): CommandResult {
        return authenticatedStub().rejectRequest(
            RejectCommand.newBuilder()
                .setRequestId(requestId)
                .setReason(reason)
                .build()
        )
    }

    // === Queries ===

    suspend fun getPendingApprovals(limit: Int = 50): List<VmRequestSummary> {
        val response = authenticatedStub().getPendingApprovals(
            GetApprovalsRequest.newBuilder().setLimit(limit).build()
        )
        return response.requestsList
    }

    suspend fun getRequestDetails(requestId: String): VmRequestDetails {
        return authenticatedStub().getRequestDetails(
            GetDetailsRequest.newBuilder().setRequestId(requestId).build()
        )
    }

    // === Subscriptions (real-time) ===

    fun subscribeToApprovals(): Flow<ApprovalEvent> {
        return authenticatedStub().subscribeApprovals(SubscribeRequest.getDefaultInstance())
    }

    fun subscribeToHealth(): Flow<HealthUpdate> {
        return authenticatedStub().subscribeHealth(SubscribeRequest.getDefaultInstance())
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}
```

### Server-side gRPC Service

```kotlin
// dvmm-infrastructure/src/main/kotlin/.../grpc/TuiGrpcService.kt
package de.acci.dvmm.infrastructure.grpc

import de.acci.dvmm.application.commands.ApproveVmRequestCommand
import de.acci.dvmm.application.commands.RejectVmRequestCommand
import de.acci.dvmm.application.queries.GetPendingApprovalsQuery
import de.acci.dvmm.tui.proto.*
import de.acci.eaf.cqrs.command.CommandGateway
import de.acci.eaf.cqrs.query.QueryGateway
import de.acci.eaf.eventsourcing.EventStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class TuiGrpcService(
    private val commandGateway: CommandGateway,
    private val queryGateway: QueryGateway,
    private val eventStore: EventStore,
    private val authService: TuiAuthService
) : DvmmTuiServiceGrpcKt.DvmmTuiServiceCoroutineImplBase() {

    override suspend fun authenticate(request: AuthRequest): AuthResponse {
        return authService.authenticate(request)
    }

    override suspend fun approveRequest(request: ApproveCommand): CommandResult {
        val context = currentTenantContext()
        return try {
            commandGateway.send(ApproveVmRequestCommand(
                requestId = VmRequestId(request.requestId),
                tenantId = context.tenantId,
                approverId = context.userId,
                comment = request.comment.takeIf { it.isNotBlank() }
            ))
            CommandResult.newBuilder().setSuccess(true).build()
        } catch (e: Exception) {
            CommandResult.newBuilder()
                .setSuccess(false)
                .setErrorMessage(e.message ?: "Unknown error")
                .build()
        }
    }

    override suspend fun rejectRequest(request: RejectCommand): CommandResult {
        val context = currentTenantContext()
        return try {
            commandGateway.send(RejectVmRequestCommand(
                requestId = VmRequestId(request.requestId),
                tenantId = context.tenantId,
                rejecterId = context.userId,
                reason = request.reason
            ))
            CommandResult.newBuilder().setSuccess(true).build()
        } catch (e: Exception) {
            CommandResult.newBuilder()
                .setSuccess(false)
                .setErrorMessage(e.message ?: "Unknown error")
                .build()
        }
    }

    override suspend fun getPendingApprovals(request: GetApprovalsRequest): GetApprovalsResponse {
        val context = currentTenantContext()
        val approvals = queryGateway.query(
            GetPendingApprovalsQuery(tenantId = context.tenantId)
        )
        return GetApprovalsResponse.newBuilder()
            .addAllRequests(approvals.map { it.toProto() })
            .setTotalCount(approvals.size)
            .build()
    }

    // Real-time subscription - streams events as they occur
    override fun subscribeApprovals(request: SubscribeRequest): Flow<ApprovalEvent> {
        val context = currentTenantContext()
        return eventStore
            .subscribeToCategory("VmRequest", context.tenantId)
            .map { event -> event.toApprovalEvent() }
    }

    override fun subscribeHealth(request: SubscribeRequest): Flow<HealthUpdate> {
        return healthMonitor.healthUpdates()
    }
}
```

---

## Kotlin DSL Wrapper

To provide idiomatic Kotlin syntax over Lanterna's Java API:

```kotlin
// dsl/DvmmWindow.kt
package de.acci.dvmm.tui.dsl

import com.googlecode.lanterna.gui2.*

fun dvmmWindow(
    title: String,
    hints: Set<Window.Hint> = setOf(Window.Hint.CENTERED),
    init: DvmmWindowBuilder.() -> Unit
): BasicWindow {
    return DvmmWindowBuilder(title, hints).apply(init).build()
}

class DvmmWindowBuilder(
    private val title: String,
    private val hints: Set<Window.Hint>
) {
    private val components = mutableListOf<Component>()
    private var layout: LayoutManager = LinearLayout(Direction.VERTICAL)

    fun gridLayout(columns: Int, init: GridPanelBuilder.() -> Unit) {
        layout = GridLayout(columns)
        components.addAll(GridPanelBuilder().apply(init).components)
    }

    fun linearLayout(direction: Direction = Direction.VERTICAL, init: LinearPanelBuilder.() -> Unit) {
        layout = LinearLayout(direction)
        components.addAll(LinearPanelBuilder().apply(init).components)
    }

    fun build(): BasicWindow {
        val panel = Panel(layout)
        components.forEach { panel.addComponent(it) }
        return BasicWindow(title).apply {
            setHints(hints)
            component = panel
        }
    }
}
```

```kotlin
// dsl/DvmmTable.kt
package de.acci.dvmm.tui.dsl

import com.googlecode.lanterna.gui2.table.Table

fun <T> dvmmTable(
    vararg columns: String,
    init: DvmmTableBuilder<T>.() -> Unit
): Table<T> {
    return DvmmTableBuilder<T>(columns.toList()).apply(init).build()
}

class DvmmTableBuilder<T>(private val columns: List<String>) {
    private val rows = mutableListOf<List<T>>()
    var onSelect: ((row: Int, data: List<T>) -> Unit)? = null

    fun row(vararg cells: T) {
        rows.add(cells.toList())
    }

    fun rows(data: List<List<T>>) {
        rows.addAll(data)
    }

    fun build(): Table<T> {
        val table = Table<T>(*columns.toTypedArray())
        rows.forEach { table.tableModel.addRow(it) }
        onSelect?.let { handler ->
            table.setSelectAction {
                handler(table.selectedRow, table.tableModel.getRow(table.selectedRow))
            }
        }
        return table
    }
}
```

### DSL Usage Example

```kotlin
// screens/ApprovalScreen.kt
package de.acci.dvmm.tui.screens

class ApprovalScreen(
    private val queryAdapter: TuiQueryAdapter,
    private val commandAdapter: TuiCommandAdapter
) {
    fun show(gui: MultiWindowTextGUI, context: TuiSecurityContext) {
        val requests = queryAdapter.getPendingApprovals(context.tenantId)

        val window = dvmmWindow("Pending Approvals - ${context.tenantId}") {
            gridLayout(2) {
                // Left panel: Request list
                panel("Requests") {
                    table<String>("ID", "VM Name", "Size", "Requester") {
                        rows(requests.map { listOf(
                            it.id.short(),
                            it.vmName,
                            it.size.label,
                            it.requesterEmail
                        )})
                        onSelect = { row, _ -> showDetails(requests[row]) }
                    }
                }

                // Right panel: Actions
                panel("Actions") {
                    actionList {
                        action("Approve Selected", Key.A) { approveSelected() }
                        action("Reject Selected", Key.R) { rejectSelected() }
                        separator()
                        action("Refresh", Key.F5) { refresh() }
                        action("Export CSV", Key.E) { exportCsv() }
                        separator()
                        action("Back", Key.ESC) { window.close() }
                    }
                }
            }

            // Bottom: Key help
            keyHelpBar("[A]pprove  [R]eject  [Enter]Details  [F5]Refresh  [Esc]Back")
        }

        gui.addWindowAndWait(window)
    }
}
```

---

## Screen Specifications

### Main Dashboard

```
┌──────────────────────────────────────────────────────────────────────┐
│  DVMM Admin Console v1.0                          Tenant: acme-corp  │
│  User: admin@acme.de                              Session: 2h 15m    │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─ Quick Stats ─────────────────────────────────────────────────┐  │
│  │  Pending Approvals: 3     Active VMs: 47     Failed: 2        │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌─ Navigation ──────────────┐  ┌─ System Health ────────────────┐  │
│  │  [1] Approval Queue (3)   │  │  VMware API     ● OK    42ms   │  │
│  │  [2] Request History      │  │  Keycloak       ● OK    18ms   │  │
│  │  [3] Audit Log            │  │  Event Store    ● OK     5ms   │  │
│  │  [4] System Health        │  │  PostgreSQL     ● OK     3ms   │  │
│  │  [5] Export Reports       │  │                                │  │
│  │  ─────────────────────    │  │  Last Check: 5s ago            │  │
│  │  [Q] Quit                 │  └────────────────────────────────┘  │
│  └────────────────────────────┘                                      │
│                                                                      │
├──────────────────────────────────────────────────────────────────────┤
│  [1-5] Navigate  [Q] Quit  [?] Help                                  │
└──────────────────────────────────────────────────────────────────────┘
```

### Approval Queue

```
┌──────────────────────────────────────────────────────────────────────┐
│  Approval Queue                                   3 Pending Requests │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─ Pending Requests ────────────────────────────────────────────┐  │
│  │  [ ] │ ID         │ VM Name          │ Size │ Requester       │  │
│  │  ────┼────────────┼──────────────────┼──────┼─────────────────│  │
│  │  [X] │ REQ-0042   │ web-server-prod  │ L    │ john@acme.de    │  │
│  │  [ ] │ REQ-0043   │ db-staging       │ M    │ jane@acme.de    │  │
│  │  [ ] │ REQ-0044   │ dev-sandbox      │ S    │ dev@acme.de     │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌─ Request Details ─────────────────────────────────────────────┐  │
│  │  Request ID:    REQ-0042                                      │  │
│  │  VM Name:       web-server-prod                               │  │
│  │  Size:          L (8 vCPU, 32 GB RAM, 500 GB Disk)           │  │
│  │  Project:       Production Infrastructure                     │  │
│  │  Requester:     john@acme.de                                  │  │
│  │  Submitted:     2025-11-29 14:23:45                          │  │
│  │  Justification: Production web server for Q4 launch          │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                      │
├──────────────────────────────────────────────────────────────────────┤
│  [Space] Toggle  [A] Approve  [R] Reject  [Enter] Details  [Esc] Back│
└──────────────────────────────────────────────────────────────────────┘
```

### Rejection Dialog

```
┌───────────────────────────────────────┐
│  Reject Request REQ-0042              │
├───────────────────────────────────────┤
│                                       │
│  Reason (required):                   │
│  ┌─────────────────────────────────┐  │
│  │ Exceeds department quota for    │  │
│  │ this quarter.                   │  │
│  │                                 │  │
│  └─────────────────────────────────┘  │
│                                       │
│    [ Confirm ]      [ Cancel ]        │
│                                       │
└───────────────────────────────────────┘
```

---

## Real-Time Updates

The TUI maintains live views by subscribing to server-pushed events via gRPC streaming.

### Approval Screen with Live Updates

```kotlin
// screens/ApprovalScreen.kt
package de.acci.dvmm.tui.screens

import com.googlecode.lanterna.gui2.*
import de.acci.dvmm.tui.auth.TuiSecurityContext
import de.acci.dvmm.tui.grpc.TuiGrpcClient
import de.acci.dvmm.tui.proto.*
import de.acci.dvmm.tui.dsl.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.CopyOnWriteArrayList

class ApprovalScreen(
    private val client: TuiGrpcClient,
    private val context: TuiSecurityContext,
    private val scope: CoroutineScope
) {
    private val requests = CopyOnWriteArrayList<VmRequestSummary>()
    private var table: Table<String>? = null
    private var subscriptionJob: Job? = null

    fun show(gui: MultiWindowTextGUI) {
        // Load initial data
        runBlocking {
            requests.addAll(client.getPendingApprovals())
        }

        // Start real-time subscription
        subscriptionJob = scope.launch {
            client.subscribeToApprovals()
                .catch { e -> showError("Connection lost: ${e.message}") }
                .collect { event -> handleEvent(event) }
        }

        val window = buildWindow()
        gui.addWindowAndWait(window)

        // Cleanup on exit
        subscriptionJob?.cancel()
    }

    private fun handleEvent(event: ApprovalEvent) {
        when (event.type) {
            ApprovalEventType.SUBMITTED -> {
                // New request appeared - add to list
                event.request?.let { requests.add(it) }
                refreshTable()
                showNotification("New request: ${event.request?.vmName}")
            }
            ApprovalEventType.APPROVED,
            ApprovalEventType.REJECTED -> {
                // Request resolved - remove from pending list
                requests.removeIf { it.id == event.requestId }
                refreshTable()
            }
            else -> { /* Ignore provisioning events in approval queue */ }
        }
    }

    private fun refreshTable() {
        table?.let { t ->
            // Clear and repopulate
            while (t.tableModel.rowCount > 0) {
                t.tableModel.removeRow(0)
            }
            requests.forEach { req ->
                t.tableModel.addRow(
                    req.id.takeLast(8),
                    req.vmName,
                    req.size,
                    req.requesterEmail
                )
            }
        }
    }

    private fun buildWindow(): BasicWindow {
        return dvmmWindow("Pending Approvals (${requests.size})") {
            gridLayout(2) {
                // Left: Request table
                panel("Requests") {
                    table = dvmmTable<String>("ID", "VM Name", "Size", "Requester") {
                        rows(requests.map { listOf(
                            it.id.takeLast(8),
                            it.vmName,
                            it.size,
                            it.requesterEmail
                        )})
                        onSelect = { row, _ -> showDetails(requests[row]) }
                    }
                    add(table!!)
                }

                // Right: Actions
                panel("Actions") {
                    actionList {
                        action("Approve", Key.A) { approveSelected() }
                        action("Reject", Key.R) { rejectSelected() }
                        separator()
                        action("Back", Key.ESC) { close() }
                    }
                }
            }
            keyHelpBar("[A]pprove  [R]eject  [Enter]Details  [Esc]Back  ● Live")
        }
    }

    private fun approveSelected() {
        val selected = table?.selectedRow ?: return
        val request = requests.getOrNull(selected) ?: return

        scope.launch {
            val result = client.approveRequest(request.id)
            if (!result.success) {
                showError(result.errorMessage)
            }
            // Note: List updates automatically via subscription
        }
    }

    private fun rejectSelected() {
        val selected = table?.selectedRow ?: return
        val request = requests.getOrNull(selected) ?: return

        // Show rejection dialog
        val reason = showRejectDialog(request.id) ?: return

        scope.launch {
            val result = client.rejectRequest(request.id, reason)
            if (!result.success) {
                showError(result.errorMessage)
            }
        }
    }
}
```

### Health Dashboard with Live Updates

```kotlin
// screens/HealthScreen.kt
package de.acci.dvmm.tui.screens

class HealthScreen(
    private val client: TuiGrpcClient,
    private val scope: CoroutineScope
) {
    private val healthStatus = mutableMapOf<String, ServiceHealth>()
    private var subscriptionJob: Job? = null

    fun show(gui: MultiWindowTextGUI) {
        // Subscribe to health updates
        subscriptionJob = scope.launch {
            client.subscribeToHealth()
                .collect { update ->
                    update.servicesList.forEach { service ->
                        healthStatus[service.name] = service
                    }
                    refreshDisplay()
                }
        }

        val window = buildWindow()
        gui.addWindowAndWait(window)
        subscriptionJob?.cancel()
    }

    private fun buildWindow(): BasicWindow {
        return dvmmWindow("System Health") {
            linearLayout {
                healthStatus.forEach { (name, health) ->
                    val indicator = if (health.healthy) "●" else "●"
                    val color = if (health.healthy) TextColor.ANSI.GREEN else TextColor.ANSI.RED
                    label("$indicator $name: ${health.responseTimeMs}ms", color)
                }
            }
            keyHelpBar("[Esc]Back  ● Live updates")
        }
    }
}
```

---

## Authentication Flow

The TUI supports multiple authentication methods, prioritized for local admin convenience:

### Authentication Methods (Priority Order)

1. **Unix User Mapping** (fastest for local SSH admins)
2. **Token File** (for automation/scripting)
3. **CLI Token Argument** (explicit override)
4. **Interactive Login** (fallback)

### TUI Authenticator

```kotlin
// auth/TuiAuthenticator.kt
package de.acci.dvmm.tui.auth

import de.acci.dvmm.tui.config.TuiConfig
import de.acci.dvmm.tui.grpc.TuiGrpcClient
import de.acci.dvmm.tui.proto.AuthRequest
import com.googlecode.lanterna.gui2.MultiWindowTextGUI

class TuiAuthenticator(
    private val client: TuiGrpcClient,
    private val config: TuiConfig
) {
    suspend fun authenticate(gui: MultiWindowTextGUI, args: Array<String>): TuiSecurityContext {
        // 1. Check for explicit token argument
        args.findToken()?.let { token ->
            return authenticateWithToken(token)
        }

        // 2. Try Unix user (if enabled and running locally)
        if (config.auth.preferUnixUser) {
            val unixUser = System.getProperty("user.name")
            val response = client.authenticate(
                AuthRequest.newBuilder().setUnixUser(unixUser).build()
            )
            if (response.success) {
                return TuiSecurityContext(
                    userId = response.userId,
                    tenantId = response.tenantId,
                    roles = response.rolesList.toSet(),
                    sessionToken = response.sessionToken,
                    authMethod = AuthMethod.UNIX_USER
                )
            }
            // Unix user not mapped - fall through to other methods
        }

        // 3. Try token file
        config.auth.tokenFile?.let { path ->
            val tokenFile = Path.of(path.expandUser())
            if (tokenFile.exists()) {
                return authenticateWithToken(tokenFile.readText().trim())
            }
        }

        // 4. Interactive login (fallback)
        val loginScreen = LoginScreen(client)
        return loginScreen.show(gui)
    }

    private suspend fun authenticateWithToken(token: String): TuiSecurityContext {
        val response = client.authenticate(
            AuthRequest.newBuilder().setToken(token).build()
        )
        if (!response.success) {
            throw AuthenticationException(response.errorMessage)
        }
        return TuiSecurityContext(
            userId = response.userId,
            tenantId = response.tenantId,
            roles = response.rolesList.toSet(),
            sessionToken = response.sessionToken,
            authMethod = AuthMethod.TOKEN
        )
    }
}

data class TuiSecurityContext(
    val userId: String,
    val tenantId: String,
    val roles: Set<String>,
    val sessionToken: String,
    val authMethod: AuthMethod
)

enum class AuthMethod { UNIX_USER, TOKEN, INTERACTIVE }
```

### Server-side Unix User Validation

```kotlin
// infrastructure/grpc/TuiAuthService.kt
package de.acci.dvmm.infrastructure.grpc

import de.acci.dvmm.tui.proto.AuthRequest
import de.acci.dvmm.tui.proto.AuthResponse
import io.grpc.Context
import org.springframework.stereotype.Service

@Service
class TuiAuthService(
    private val tuiProperties: TuiProperties,
    private val tokenValidator: TokenValidator,
    private val sessionManager: TuiSessionManager
) {
    fun authenticate(request: AuthRequest): AuthResponse {
        return when {
            request.hasToken() -> authenticateWithToken(request.token)
            request.hasUnixUser() -> authenticateUnixUser(request.unixUser)
            else -> AuthResponse.newBuilder()
                .setSuccess(false)
                .setErrorMessage("No authentication method provided")
                .build()
        }
    }

    private fun authenticateUnixUser(unixUser: String): AuthResponse {
        // Verify request comes from Unix socket (not TCP)
        val peerCredentials = PEER_CREDENTIALS_KEY.get()
            ?: return errorResponse("Unix auth only available via Unix socket")

        // Verify claimed user matches socket peer
        if (peerCredentials.uid != getUidForUser(unixUser)) {
            return errorResponse("Unix user mismatch")
        }

        // Look up mapping
        val mapping = tuiProperties.unixUserMappings[unixUser]
            ?: return errorResponse("Unix user '$unixUser' not mapped to DVMM identity")

        // Create session
        val session = sessionManager.createSession(
            userId = mapping.userId,
            tenantId = mapping.tenantId,
            roles = mapping.roles
        )

        return AuthResponse.newBuilder()
            .setSuccess(true)
            .setUserId(mapping.userId)
            .setTenantId(mapping.tenantId)
            .addAllRoles(mapping.roles)
            .setSessionToken(session.token)
            .build()
    }
}
```

### CLI Usage

```bash
# Fast path - instant auth for mapped Unix users
$ dvmm-tui
# → Authenticated as admin@example.com via Unix user 'admin'
# → Dashboard appears immediately

# Explicit token (CI/automation)
$ dvmm-tui --token=eyJhbGciOiJSUzI1NiIs...

# Token file (scripting)
$ echo "$DVMM_TOKEN" > ~/.config/dvmm-tui/token
$ dvmm-tui
# → Uses token from file

# Interactive (unmapped users)
$ dvmm-tui
# → Shows login dialog if Unix user not mapped
```

---

## Configuration

### TUI Client Config (standalone)

```yaml
# ~/.config/dvmm-tui/config.yml (or /etc/dvmm-tui/config.yml)

# Connection
socket-path: /var/run/dvmm/tui.sock
connection-timeout: 5s
request-timeout: 30s

# Authentication
auth:
  # Try Unix user mapping first (fastest for local admins)
  prefer-unix-user: true
  # Fallback token file (for automation)
  token-file: ~/.config/dvmm-tui/token

# UI Settings
ui:
  date-format: "yyyy-MM-dd HH:mm:ss"
  colors:
    primary: cyan
    success: green
    warning: yellow
    error: red
  keybindings:
    approve: a
    reject: r
    quit: q
    help: "?"
```

### Server-side Config (dvmm-app)

```yaml
# application.yml (add to existing)

grpc:
  server:
    # TCP port for remote gRPC (optional)
    port: 9090
    # Unix socket for local TUI access (recommended)
    unix-socket:
      enabled: true
      path: /var/run/dvmm/tui.sock
      permissions: 660           # Owner + group read/write
      group: dvmm-admins         # Unix group with access

dvmm:
  tui:
    # Unix user → DVMM identity mapping
    unix-user-mappings:
      admin:
        user-id: admin@example.com
        tenant-id: default
        roles: [ADMIN]
      operator:
        user-id: operator@example.com
        tenant-id: default
        roles: [OPERATOR]

    # Session settings
    session-timeout: 4h
    max-concurrent-sessions: 10

    # Health streaming
    health-push-interval: 10s
```

### systemd Socket Activation (Optional)

For production deployments, systemd can manage the socket:

```ini
# /etc/systemd/system/dvmm-tui.socket
[Unit]
Description=DVMM TUI gRPC Socket

[Socket]
ListenStream=/var/run/dvmm/tui.sock
SocketMode=0660
SocketGroup=dvmm-admins

[Install]
WantedBy=sockets.target
```

```ini
# /etc/systemd/system/dvmm-app.service (add to existing)
[Service]
# ... existing config ...

# Receive socket from systemd
Environment=GRPC_UNIX_SOCKET_FD=3
```

---

## Testing Strategy

### Unit Tests

```kotlin
// screens/ApprovalScreenTest.kt
class ApprovalScreenTest {

    private val queryAdapter = mockk<TuiQueryAdapter>()
    private val commandAdapter = mockk<TuiCommandAdapter>()
    private val screen = ApprovalScreen(queryAdapter, commandAdapter)

    @Test
    fun `approve action sends command with correct parameters`() = runTest {
        // Given
        val request = aVmRequestSummary(id = "REQ-0042")
        coEvery { queryAdapter.getPendingApprovals(any()) } returns listOf(request)
        coEvery { commandAdapter.approveRequest(any(), any(), any(), any()) } returns Result.success(Unit)

        val context = aTuiSecurityContext(tenantId = "acme-corp", userId = "admin")

        // When
        screen.approveRequest(request.id, context)

        // Then
        coVerify {
            commandAdapter.approveRequest(
                requestId = VmRequestId("REQ-0042"),
                tenantId = TenantId("acme-corp"),
                approverId = UserId("admin"),
                comment = null
            )
        }
    }
}
```

### Integration Tests

```kotlin
// TuiIntegrationTest.kt
@SpringBootTest(classes = [DvmmTuiApplication::class])
@ActiveProfiles("tui", "test")
class TuiIntegrationTest {

    @Autowired
    private lateinit var commandAdapter: TuiCommandAdapter

    @Autowired
    private lateinit var queryAdapter: TuiQueryAdapter

    @Test
    fun `approval flow integrates with application layer`() = runTest {
        // Given
        val tenantContext = TenantTestContext("test-tenant")
        val requestId = createPendingRequest(tenantContext)

        // When
        val result = commandAdapter.approveRequest(
            requestId = requestId,
            tenantId = tenantContext.tenantId,
            approverId = UserId("admin"),
            comment = "Approved via TUI"
        )

        // Then
        assertThat(result.isSuccess).isTrue()

        val details = queryAdapter.getRequestDetails(requestId, tenantContext.tenantId)
        assertThat(details?.status).isEqualTo(RequestStatus.APPROVED)
    }
}
```

---

## Keyboard Shortcuts Reference

| Key | Action | Context |
|-----|--------|---------|
| `1-9` | Navigate to menu item | Main screen |
| `Enter` | Select / Confirm | All screens |
| `Space` | Toggle checkbox | Lists |
| `A` | Approve selected | Approval queue |
| `R` | Reject selected | Approval queue |
| `E` | Export to CSV | Lists |
| `F5` | Refresh data | All screens |
| `/` | Search / Filter | Lists |
| `Esc` | Back / Cancel | All screens |
| `Q` | Quit application | Main screen |
| `?` / `F1` | Show help | All screens |
| `Tab` | Next field | Forms |
| `Shift+Tab` | Previous field | Forms |

---

## Acceptance Criteria

### Story: TUI Module Foundation

**Given** the TUI module is configured
**When** I run `./gradlew :dvmm:dvmm-tui:build`
**Then** the build succeeds with zero errors

**And** the module has correct dependencies:
- `dvmm-application` (not `dvmm-api` or `dvmm-infrastructure`)
- `eaf-cqrs-core`
- `lanterna:3.1.2`

**And** architecture tests verify:
- TUI module cannot import from `dvmm-domain` internals
- TUI module cannot bypass application layer

### Story: Approval Queue Screen

**Given** I am authenticated as an admin
**And** there are 3 pending approval requests
**When** I navigate to the Approval Queue
**Then** I see all 3 requests in a table

**And** I can select requests with Space
**And** I can approve with 'A' key
**And** I can reject with 'R' key (prompts for reason)
**And** the list refreshes after each action

### Story: System Health Dashboard

**Given** I am on the main screen
**When** the health check runs (every 30s)
**Then** I see status indicators for:
- VMware API connection
- Keycloak connectivity
- Event Store health
- PostgreSQL connection

**And** each indicator shows:
- Green dot (●) for healthy
- Red dot (●) for unhealthy
- Response time in milliseconds

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Terminal compatibility issues | Test on xterm, iTerm2, Windows Terminal, PuTTY; use Lanterna's Swing fallback for unsupported terminals |
| Concurrent modification | Real-time streaming ensures TUI sees latest state; handle optimistic locking errors gracefully |
| Session timeout during long operations | Show warning before timeout; gRPC keepalive extends active sessions |
| gRPC stream disconnection | Auto-reconnect with exponential backoff; show connection indicator in UI |
| Unix socket permission issues | Use systemd socket activation; validate group membership on startup |
| Event store subscription lag | Initial query + subscription ensures no missed events during startup |

## Architecture Decision Summary

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Communication** | gRPC over Unix socket | Real-time streaming, type-safe, ~0.1ms latency for local access |
| **Auth (local)** | Unix user mapping | Zero-friction for SSH admins, leverages existing OS authentication |
| **Auth (remote)** | JWT via gRPC metadata | Consistent with web API authentication |
| **Real-time updates** | Server streaming | Eliminates polling, instant UI updates when events occur |
| **Client runtime** | Standalone (no Spring) | Fast startup (~1s), minimal dependencies, single fat JAR |
| **Server integration** | grpc-spring-boot-starter | Reuses existing application layer, event store subscriptions |
| **Protocol definition** | Protobuf | Type-safe contract, efficient binary encoding, Kotlin coroutine support |

---

## Future Considerations

### Phase 2 Enhancements

- Batch approval (approve multiple at once)
- Saved filters/views
- Custom keyboard shortcuts
- Notification sound on new requests

### Phase 3 Enhancements

- Split-screen mode (approvals + health)
- SSH key authentication
- Audit log search with regex
- Performance metrics graphs (ASCII)

---

## References

- [Lanterna 3.1.2 JavaDoc](http://mabe02.github.io/lanterna/apidocs/3.1/)
- [Lanterna Examples](https://github.com/mabe02/lanterna/tree/master/docs/examples)
- [DVMM Architecture](../architecture.md)
- [DVMM PRD](../prd.md)
