# Tech Spec: DCM TUI Admin Interface

**Author:** DCM Team
**Date:** 2025-11-29
**Version:** 2.0
**Status:** Draft
**Epic:** 6 (Post-MVP Enhancement)
**Related:** [TUI Analysis](tui-analysis.md) | [Architecture](../architecture.md)

---

## Executive Summary

### Problem Statement

Experienced administrators need a fast, keyboard-driven interface for routine operations (approvals, status checks) that works over SSH without requiring a web browser.

### Solution

A standalone Terminal User Interface (TUI) application using Lanterna, communicating with `dcm-app` via gRPC streaming over Unix socket for real-time updates and sub-millisecond latency.

### Key Benefits

| Benefit | Description |
|---------|-------------|
| **Real-time** | Server pushes events instantly when requests change state |
| **Fast auth** | Unix user mapping for zero-friction SSH admin access |
| **Low latency** | Unix socket (~0.1ms) vs HTTP overhead |
| **Type-safe** | Protobuf contracts shared between client and server |
| **Lightweight** | Single fat JAR, ~1s startup, no Spring in client |

---

## Scope

### In Scope

| Feature | Priority | Story |
|---------|----------|-------|
| Approval queue with real-time updates | Must | 6.1 |
| Approve/Reject with keyboard shortcuts | Must | 6.2 |
| System health dashboard (live) | Must | 6.3 |
| Request detail view | Must | 6.4 |
| Audit log viewer | Should | 6.5 |
| CSV export | Should | 6.6 |
| Unix user authentication | Must | 6.7 |
| Token-based authentication (fallback) | Must | 6.8 |

### Out of Scope

- VM request creation (web-only for MVP)
- User management (web-only)
- Full reporting dashboards (web-only)
- Windows support (Linux/macOS only due to Unix sockets)

### Dependencies

| Dependency | Type | Required By |
|------------|------|-------------|
| Epic 2 (Core Workflow) complete | Epic | All stories |
| Epic 3 (VM Provisioning) complete | Epic | Health dashboard |
| gRPC server in dcm-infrastructure | Story | 6.1 |
| Event store subscription API | Story | 6.1, 6.3 |

---

## Functional Requirements

| ID | Requirement | Story | Priority |
|----|-------------|-------|----------|
| TUI-FR1 | Admins can view pending approvals in real-time | 6.1 | Must |
| TUI-FR2 | Admins can approve requests with keyboard shortcut | 6.2 | Must |
| TUI-FR3 | Admins can reject requests with mandatory reason | 6.2 | Must |
| TUI-FR4 | System displays live health status for all services | 6.3 | Must |
| TUI-FR5 | Admins can view full request details | 6.4 | Must |
| TUI-FR6 | Admins can view audit log entries | 6.5 | Should |
| TUI-FR7 | Admins can export data to CSV | 6.6 | Should |
| TUI-FR8 | System authenticates via Unix user mapping | 6.7 | Must |
| TUI-FR9 | System falls back to token authentication | 6.8 | Must |
| TUI-FR10 | UI updates automatically when events occur | 6.1 | Must |

## Non-Functional Requirements

| ID | Requirement | Target | Measurement |
|----|-------------|--------|-------------|
| TUI-NFR1 | Startup time | < 2s | Time from launch to login screen |
| TUI-NFR2 | gRPC latency (local) | < 1ms | Round-trip via Unix socket |
| TUI-NFR3 | Event propagation | < 100ms | Server event to UI update |
| TUI-NFR4 | Memory footprint | < 128MB | JVM heap at steady state |
| TUI-NFR5 | Terminal compatibility | xterm, iTerm2, GNOME Terminal | Manual testing matrix |
| TUI-NFR6 | Concurrent sessions | 10 per server | Load test |

---

## Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              DCM TUI Architecture                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌───────────────────────┐              ┌─────────────────────────────────┐│
│  │                       │              │                                 ││
│  │    dcm-tui (CLI)     │   Unix       │     dcm-app (Spring Boot)      ││
│  │    ════════════════   │   Socket     │     ══════════════════════      ││
│  │                       │              │                                 ││
│  │  ┌─────────────────┐  │  /var/run/   │  ┌───────────────────────────┐  ││
│  │  │  gRPC Client    │◄─┼──dcm.sock──►┼─►│    TuiGrpcService         │  ││
│  │  │  ─────────────  │  │              │  │    ───────────────        │  ││
│  │  │  • Commands     │  │   Streams    │  │    @GrpcService           │  ││
│  │  │  • Subscriptions│  │   ◄────────  │  │                           │  ││
│  │  └────────┬────────┘  │   Push       │  └─────────────┬─────────────┘  ││
│  │           │           │   Events     │                │                ││
│  │  ┌────────▼────────┐  │              │  ┌─────────────▼─────────────┐  ││
│  │  │  Lanterna TUI   │  │              │  │   Application Layer       │  ││
│  │  │  ─────────────  │  │              │  │   ─────────────────       │  ││
│  │  │  Terminal UI    │  │              │  │   CommandGateway          │  ││
│  │  └─────────────────┘  │              │  │   QueryGateway            │  ││
│  │                       │              │  │   EventStore.subscribe()  │  ││
│  └───────────────────────┘              │  └───────────────────────────┘  ││
│                                         │                                 ││
│           Standalone JAR                │         Spring Boot App         ││
│           (no Spring)                   │                                 ││
│                                         └─────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘
```

### Module Structure

```
dcm/
├── dcm-tui-protocol/                    ◄── Shared Protobuf definitions
│   ├── build.gradle.kts
│   └── src/main/proto/
│       └── dcm_tui.proto
│
├── dcm-tui/                             ◄── Standalone TUI client (NO Spring)
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/de/acci/dcm/tui/
│       │   ├── DcmTuiApplication.kt           # Entry point
│       │   ├── TuiRunner.kt                    # Main loop
│       │   ├── config/TuiConfig.kt             # YAML config loading
│       │   ├── auth/
│       │   │   ├── TuiAuthenticator.kt         # Unix user / token auth
│       │   │   └── TuiSecurityContext.kt       # Session state
│       │   ├── grpc/
│       │   │   ├── TuiGrpcClient.kt            # Unix socket channel
│       │   │   └── EventSubscriber.kt          # Stream handler
│       │   ├── dsl/                            # Kotlin DSL for Lanterna
│       │   │   ├── DcmWindow.kt
│       │   │   ├── DcmPanel.kt
│       │   │   └── DcmTable.kt
│       │   ├── screens/
│       │   │   ├── MainScreen.kt
│       │   │   ├── ApprovalScreen.kt
│       │   │   ├── RequestDetailScreen.kt
│       │   │   ├── HealthScreen.kt
│       │   │   ├── AuditScreen.kt
│       │   │   └── LoginScreen.kt
│       │   └── widgets/
│       │       ├── StatusIndicator.kt
│       │       ├── RequestTable.kt
│       │       └── KeyHelpBar.kt
│       └── test/kotlin/de/acci/dcm/tui/
│           ├── grpc/TuiGrpcClientTest.kt       # gRPC mock tests
│           └── screens/ApprovalScreenTest.kt
│
└── dcm-infrastructure/
    └── src/main/kotlin/.../grpc/
        ├── TuiGrpcService.kt             ◄── Server-side gRPC impl
        └── TuiAuthService.kt             ◄── Unix user validation
```

### Dependency Rules

```
┌─────────────────────────────────────────────────────────────────┐
│                    Module Dependency Graph                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   dcm-tui-protocol ◄───────────────────────────────────────┐   │
│         │                                                    │   │
│         │ (Protobuf stubs)                                  │   │
│         │                                                    │   │
│         ▼                                                    │   │
│   ┌───────────┐                                    ┌────────┴─┐ │
│   │ dcm-tui  │                                    │ dcm-    │ │
│   │ (client)  │                                    │ infra    │ │
│   └───────────┘                                    └────────┬─┘ │
│         │                                                   │   │
│         │ NO dependency                                     │   │
│         ╳ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ╳   │
│                                                                  │
│   Client and Server share ONLY the protocol module               │
│   Client has NO Spring dependencies                              │
│   Client has NO access to dcm-application internals             │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Screen Designs

### Main Dashboard

```
╔══════════════════════════════════════════════════════════════════════════════╗
║  DCM Admin Console v1.0                              Tenant: acme-corp      ║
║  User: admin@acme.de                                  Session: 2h 15m   ● ○  ║
╠══════════════════════════════════════════════════════════════════════════════╣
║                                                                              ║
║  ╭─────────────────────────── Quick Stats ───────────────────────────────╮   ║
║  │                                                                       │   ║
║  │    🟡 Pending: 3          🟢 Active VMs: 47         🔴 Failed: 2      │   ║
║  │                                                                       │   ║
║  ╰───────────────────────────────────────────────────────────────────────╯   ║
║                                                                              ║
║  ┌─────────────── Navigation ───────────────┐  ┌─── System Health ────────┐  ║
║  │                                          │  │                          │  ║
║  │   [1]  Approval Queue              (3)   │  │  VMware API    ● 42ms    │  ║
║  │   [2]  Request History                   │  │  Keycloak      ● 18ms    │  ║
║  │   [3]  Audit Log                         │  │  Event Store   ●  5ms    │  ║
║  │   [4]  System Health                     │  │  PostgreSQL    ●  3ms    │  ║
║  │   [5]  Export Reports                    │  │                          │  ║
║  │   ─────────────────────────────────────  │  │  Last: 5s ago   ● Live   │  ║
║  │   [Q]  Quit                              │  │                          │  ║
║  │                                          │  └──────────────────────────┘  ║
║  └──────────────────────────────────────────┘                                ║
║                                                                              ║
╠══════════════════════════════════════════════════════════════════════════════╣
║  [1-5] Navigate    [Q] Quit    [?] Help                          ● Connected ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

### Approval Queue (with Real-Time Updates)

```
╔══════════════════════════════════════════════════════════════════════════════╗
║  Approval Queue                                          3 Pending Requests  ║
╠══════════════════════════════════════════════════════════════════════════════╣
║                                                                              ║
║  ┏━━━━━┳━━━━━━━━━━━━━━┳━━━━━━━━━━━━━━━━━━━━━━┳━━━━━━━━┳━━━━━━━━━━━━━━━━━━━┓  ║
║  ┃     ┃ ID           ┃ VM Name              ┃  Size  ┃ Requester         ┃  ║
║  ┣━━━━━╋━━━━━━━━━━━━━━╋━━━━━━━━━━━━━━━━━━━━━━╋━━━━━━━━╋━━━━━━━━━━━━━━━━━━━┫  ║
║  ┃ ▶▶  ┃ REQ-0042     ┃ web-server-prod      ┃   L    ┃ john@acme.de      ┃  ║
║  ┃     ┃ REQ-0043     ┃ db-staging           ┃   M    ┃ jane@acme.de      ┃  ║
║  ┃     ┃ REQ-0044     ┃ dev-sandbox          ┃   S    ┃ dev@acme.de       ┃  ║
║  ┗━━━━━┻━━━━━━━━━━━━━━┻━━━━━━━━━━━━━━━━━━━━━━┻━━━━━━━━┻━━━━━━━━━━━━━━━━━━━┛  ║
║                                                                              ║
║  ╭──────────────────────────── Request Details ─────────────────────────────╮║
║  │                                                                          │║
║  │  Request ID      REQ-0042                                                │║
║  │  VM Name         web-server-prod                                         │║
║  │  Size            L  (8 vCPU, 32 GB RAM, 500 GB Disk)                     │║
║  │  Project         Production Infrastructure                               │║
║  │  Requester       john@acme.de                                            │║
║  │  Submitted       2025-11-29 14:23:45                                     │║
║  │  ───────────────────────────────────────────────────────────────────     │║
║  │  Justification   Production web server for Q4 product launch.            │║
║  │                  Expected traffic: 10k concurrent users.                 │║
║  │                                                                          │║
║  ╰──────────────────────────────────────────────────────────────────────────╯║
║                                                                              ║
╠══════════════════════════════════════════════════════════════════════════════╣
║  [A]pprove  [R]eject  [↑↓] Navigate  [Enter] Details  [Esc] Back    ● Live  ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

### Rejection Dialog

```
                    ╔═══════════════════════════════════════════╗
                    ║        Reject Request REQ-0042            ║
                    ╠═══════════════════════════════════════════╣
                    ║                                           ║
                    ║  Reason (required):                       ║
                    ║  ┌─────────────────────────────────────┐  ║
                    ║  │ Exceeds department quota for this   │  ║
                    ║  │ quarter. Please resubmit in Q1 or   │  ║
                    ║  │ request budget increase first.      │  ║
                    ║  │                                     │  ║
                    ║  │                                     │  ║
                    ║  └─────────────────────────────────────┘  ║
                    ║                                           ║
                    ║      [ Confirm ]        [ Cancel ]        ║
                    ║                                           ║
                    ╚═══════════════════════════════════════════╝
```

### System Health Dashboard

```
╔══════════════════════════════════════════════════════════════════════════════╗
║  System Health                                              ● Live Updates   ║
╠══════════════════════════════════════════════════════════════════════════════╣
║                                                                              ║
║  ┌─────────────────────────────────────────────────────────────────────────┐ ║
║  │  Service              Status      Latency       Last Check              │ ║
║  ├─────────────────────────────────────────────────────────────────────────┤ ║
║  │  VMware vSphere API   ● Healthy     42ms        2s ago                  │ ║
║  │  Keycloak             ● Healthy     18ms        2s ago                  │ ║
║  │  Event Store          ● Healthy      5ms        2s ago                  │ ║
║  │  PostgreSQL           ● Healthy      3ms        2s ago                  │ ║
║  │  Email Service        ● Healthy     89ms        2s ago                  │ ║
║  │  ─────────────────────────────────────────────────────────────────────  │ ║
║  │  Redis Cache          ○ Degraded   450ms        2s ago                  │ ║
║  │                       └─ High latency detected                          │ ║
║  └─────────────────────────────────────────────────────────────────────────┘ ║
║                                                                              ║
║  ╭─────────────────────────── Uptime Summary ───────────────────────────────╮║
║  │                                                                          │║
║  │   Today: 100%        This Week: 99.8%        This Month: 99.95%         │║
║  │                                                                          │║
║  ╰──────────────────────────────────────────────────────────────────────────╯║
║                                                                              ║
╠══════════════════════════════════════════════════════════════════════════════╣
║  [R] Refresh Now    [Esc] Back                               Last: 2s ago   ║
╚══════════════════════════════════════════════════════════════════════════════╝

Legend:  ● Healthy (< 100ms)   ○ Degraded (100-500ms)   ✖ Unhealthy (> 500ms/error)
```

### Audit Log Viewer

```
╔══════════════════════════════════════════════════════════════════════════════╗
║  Audit Log                                                    Filter: All    ║
╠══════════════════════════════════════════════════════════════════════════════╣
║                                                                              ║
║  ┌─────────────────────────────────────────────────────────────────────────┐ ║
║  │ Timestamp            Event                Actor           Entity        │ ║
║  ├─────────────────────────────────────────────────────────────────────────┤ ║
║  │ 2025-11-29 14:45:02  REQUEST_APPROVED     admin@acme.de   REQ-0041      │ ║
║  │ 2025-11-29 14:44:58  VM_PROVISIONED       system          VM-web-03     │ ║
║  │ 2025-11-29 14:42:11  REQUEST_SUBMITTED    john@acme.de    REQ-0042      │ ║
║  │ 2025-11-29 14:40:33  REQUEST_REJECTED     admin@acme.de   REQ-0040      │ ║
║  │ 2025-11-29 14:38:01  USER_LOGIN           jane@acme.de    session-xyz   │ ║
║  │ 2025-11-29 14:35:22  REQUEST_APPROVED     admin@acme.de   REQ-0039      │ ║
║  │                                                                         │ ║
║  │                           ─ ─ ─ ─ ─ ─ ─ ─                               │ ║
║  │                        Page 1 of 15 (142 entries)                       │ ║
║  └─────────────────────────────────────────────────────────────────────────┘ ║
║                                                                              ║
║  ╭─ Event Detail ───────────────────────────────────────────────────────────╮║
║  │ Event:    REQUEST_APPROVED                                               │║
║  │ Actor:    admin@acme.de (Admin)                                          │║
║  │ Entity:   REQ-0041                                                       │║
║  │ Details:  VM request approved. Comment: "Approved for Q4 launch"         │║
║  ╰──────────────────────────────────────────────────────────────────────────╯║
║                                                                              ║
╠══════════════════════════════════════════════════════════════════════════════╣
║  [/] Filter  [E] Export CSV  [PgUp/Dn] Page  [↑↓] Select  [Esc] Back        ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

---

## Protobuf Service Definition

```protobuf
// dcm-tui-protocol/src/main/proto/dcm_tui.proto
syntax = "proto3";
package de.acci.dcm.tui;

option java_multiple_files = true;
option java_package = "de.acci.dcm.tui.proto";

service DcmTuiService {
  // Authentication
  rpc Authenticate(AuthRequest) returns (AuthResponse);

  // Commands (unary)
  rpc ApproveRequest(ApproveCommand) returns (CommandResult);
  rpc RejectRequest(RejectCommand) returns (CommandResult);

  // Queries (unary)
  rpc GetPendingApprovals(GetApprovalsRequest) returns (GetApprovalsResponse);
  rpc GetRequestDetails(GetDetailsRequest) returns (VmRequestDetails);
  rpc GetAuditLog(GetAuditLogRequest) returns (GetAuditLogResponse);

  // Subscriptions (server streaming)
  rpc SubscribeApprovals(SubscribeRequest) returns (stream ApprovalEvent);
  rpc SubscribeHealth(SubscribeRequest) returns (stream HealthUpdate);
}

// === Authentication ===
message AuthRequest {
  oneof method {
    string token = 1;
    string unix_user = 2;
  }
}

message AuthResponse {
  bool success = 1;
  string user_id = 2;
  string tenant_id = 3;
  repeated string roles = 4;
  string session_token = 5;
  string error_message = 6;
}

// === Commands ===
message ApproveCommand {
  string request_id = 1;
  optional string comment = 2;
}

message RejectCommand {
  string request_id = 1;
  string reason = 2;
}

message CommandResult {
  bool success = 1;
  string error_code = 2;
  string error_message = 3;
}

// === Queries ===
message GetApprovalsRequest {
  int32 limit = 1;
  int32 offset = 2;
}

message GetApprovalsResponse {
  repeated VmRequestSummary requests = 1;
  int32 total_count = 2;
}

message VmRequestSummary {
  string id = 1;
  string vm_name = 2;
  string size = 3;
  string requester_email = 4;
  int64 submitted_at = 5;
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
  int32 limit = 4;
  int32 offset = 5;
}

message GetAuditLogResponse {
  repeated AuditLogEntry entries = 1;
  int32 total_count = 2;
}

message AuditLogEntry {
  int64 timestamp = 1;
  string event_type = 2;
  string actor_id = 3;
  string entity_id = 4;
  string details = 5;
}

// === Subscriptions ===
message SubscribeRequest {}

message ApprovalEvent {
  string request_id = 1;
  ApprovalEventType type = 2;
  string actor_id = 3;
  int64 timestamp = 4;
  optional VmRequestSummary request = 5;
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
  string name = 1;
  HealthStatus status = 2;
  int32 response_time_ms = 3;
  optional string error_message = 4;
}

enum HealthStatus {
  HEALTH_UNKNOWN = 0;
  HEALTHY = 1;
  DEGRADED = 2;
  UNHEALTHY = 3;
}
```

---

## Authentication Flow

### Priority Order

1. **Unix User Mapping** - Fastest for local SSH admins
2. **Token File** - For automation/scripting (`~/.config/dcm-tui/token`)
3. **CLI Token Argument** - Explicit override (`--token=...`)
4. **Interactive Login** - Fallback with username/password

### Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         TUI Authentication Flow                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────┐     ┌──────────────────────────────────────────────────┐ │
│  │ dcm-tui     │     │                   Priority Chain                 │ │
│  │ starts       │────►│                                                  │ │
│  └──────────────┘     │  1. --token=... arg?  ──Yes──► Validate Token    │ │
│                       │          │                           │           │ │
│                       │          No                          │           │ │
│                       │          ▼                           ▼           │ │
│                       │  2. Unix socket +     ──Yes──► Validate Unix     │ │
│                       │     prefer-unix-user?          User Mapping      │ │
│                       │          │                           │           │ │
│                       │          No                          │           │ │
│                       │          ▼                           ▼           │ │
│                       │  3. Token file exists? ──Yes──► Validate Token   │ │
│                       │          │                           │           │ │
│                       │          No                          │           │ │
│                       │          ▼                           ▼           │ │
│                       │  4. Show Login Screen ──────► Validate Creds     │ │
│                       │                                      │           │ │
│                       └──────────────────────────────────────┼───────────┘ │
│                                                              │             │
│                                                              ▼             │
│                                                     ┌────────────────┐     │
│                                                     │ Session Token  │     │
│                                                     │ + User Context │     │
│                                                     └────────────────┘     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Unix User Mapping (Server Config)

```yaml
# application.yml (dcm-app)
dcm:
  tui:
    unix-user-mappings:
      admin:
        user-id: admin@example.com
        tenant-id: default
        roles: [ADMIN]
      operator:
        user-id: operator@example.com
        tenant-id: default
        roles: [OPERATOR]
```

---

## Keyboard Shortcuts

| Key | Action | Context |
|-----|--------|---------|
| `1-9` | Navigate to menu item | Main screen |
| `Enter` | Select / Confirm | All screens |
| `Space` | Toggle selection | Lists with checkboxes |
| `A` | Approve selected request | Approval queue |
| `R` | Reject selected request | Approval queue |
| `E` | Export to CSV | Lists |
| `/` | Open filter/search | Lists |
| `PgUp/PgDn` | Page through results | Lists |
| `↑↓` | Navigate rows | Tables |
| `F5` | Force refresh | All screens |
| `Esc` | Back / Cancel | All screens |
| `Q` | Quit application | Main screen |
| `?` / `F1` | Show help | All screens |

---

## Story Breakdown (Epic 6)

### Story 6.1: TUI Module Foundation

**As an** administrator
**I want** a working TUI that connects to the server
**So that** I can access admin functions from the terminal

**Acceptance Criteria:**

- [ ] `./gradlew :dcm:dcm-tui:build` succeeds
- [ ] `./gradlew :dcm:dcm-tui-protocol:build` succeeds
- [ ] gRPC client connects via Unix socket
- [ ] Main dashboard displays with navigation menu
- [ ] Architecture tests verify module boundaries

**Technical Tasks:**

1. Create `dcm-tui-protocol` module with Protobuf setup
2. Create `dcm-tui` module with Lanterna dependency
3. Implement `TuiGrpcClient` with Unix socket support
4. Implement `MainScreen` with navigation
5. Add `TuiGrpcService` stub in infrastructure

---

### Story 6.2: Approval Queue with Actions

**As an** administrator
**I want** to approve and reject requests via keyboard
**So that** I can process approvals faster than via web

**Acceptance Criteria:**

- [ ] Pending requests display in table
- [ ] `A` key approves selected request
- [ ] `R` key opens rejection dialog with mandatory reason
- [ ] Commands execute via gRPC and update backend
- [ ] Table reflects changes after action

**Technical Tasks:**

1. Implement `ApprovalScreen` with request table
2. Implement approve/reject gRPC calls
3. Create rejection dialog with text input
4. Wire keyboard shortcuts

---

### Story 6.3: Real-Time Updates via Streaming

**As an** administrator
**I want** the approval queue to update automatically
**So that** I see new requests without refreshing

**Acceptance Criteria:**

- [ ] New requests appear in table within 100ms of submission
- [ ] Approved/rejected requests disappear from pending list
- [ ] Visual indicator shows "Live" connection status
- [ ] Graceful reconnection on stream disconnect

**Technical Tasks:**

1. Implement `subscribeApprovals` server streaming
2. Add `EventSubscriber` with reconnection logic
3. Update `ApprovalScreen` to handle events
4. Add connection status indicator

---

### Story 6.4: Request Detail View

**As an** administrator
**I want** to view full request details
**So that** I can make informed approval decisions

**Acceptance Criteria:**

- [ ] `Enter` on a request opens detail view
- [ ] All request fields displayed (name, size, specs, justification)
- [ ] Approve/Reject actions available from detail view
- [ ] `Esc` returns to list

---

### Story 6.5: System Health Dashboard

**As an** administrator
**I want** to see service health in real-time
**So that** I know when infrastructure issues affect operations

**Acceptance Criteria:**

- [ ] Health screen shows all monitored services
- [ ] Status indicators: ● Healthy, ○ Degraded, ✖ Unhealthy
- [ ] Response times displayed per service
- [ ] Updates stream automatically (every 10s)

---

### Story 6.6: Audit Log Viewer

**As an** administrator
**I want** to browse the audit log
**So that** I can investigate actions and compliance

**Acceptance Criteria:**

- [ ] Paginated list of audit entries
- [ ] Filter by event type
- [ ] Detail view for selected entry
- [ ] Export to CSV

---

### Story 6.7: Unix User Authentication

**As an** SSH administrator
**I want** to authenticate automatically via Unix user
**So that** I don't need to enter credentials each time

**Acceptance Criteria:**

- [ ] Unix user detected from environment
- [ ] Server validates user via peer credentials
- [ ] Mapped users get session token automatically
- [ ] Unmapped users fall through to token/interactive auth

---

### Story 6.8: Token Authentication (Fallback)

**As an** administrator
**I want** to authenticate with a token
**So that** I can use TUI in automation scripts

**Acceptance Criteria:**

- [ ] `--token=...` CLI argument accepted
- [ ] Token file at `~/.config/dcm-tui/token` read if present
- [ ] Interactive login screen as final fallback
- [ ] Session token stored for subsequent calls

---

## Risks and Mitigations

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Terminal compatibility issues | Medium | Medium | Test matrix: xterm, iTerm2, GNOME Terminal, PuTTY. Use Lanterna's Swing fallback. |
| gRPC stream disconnection | Medium | Low | Auto-reconnect with exponential backoff. Connection indicator in UI. |
| Unix socket permission issues | Low | High | Use systemd socket activation. Validate group membership on startup. Document setup clearly. |
| Event ordering during subscription | Low | Medium | Initial query + subscription ensures no missed events. Sequence numbers in events. |
| Performance on slow terminals | Low | Low | Minimize redraws. Batch table updates. |

---

## Configuration Reference

### TUI Client (`~/.config/dcm-tui/config.yml`)

```yaml
socket-path: /var/run/dcm/tui.sock
connection-timeout: 5s
request-timeout: 30s

auth:
  prefer-unix-user: true
  token-file: ~/.config/dcm-tui/token

ui:
  date-format: "yyyy-MM-dd HH:mm:ss"
  page-size: 20
```

### Server (`application.yml`)

```yaml
grpc:
  server:
    port: 9090
    unix-socket:
      enabled: true
      path: /var/run/dcm/tui.sock
      permissions: 660
      group: dcm-admins

dcm:
  tui:
    session-timeout: 4h
    max-concurrent-sessions: 10
    health-push-interval: 10s
    unix-user-mappings:
      admin:
        user-id: admin@example.com
        tenant-id: default
        roles: [ADMIN]
```

---

## Definition of Done

For each story in Epic 6:

- [ ] Code compiles with zero warnings
- [ ] Unit tests pass with ≥80% coverage
- [ ] Integration tests verify gRPC contract
- [ ] Keyboard shortcuts work as documented
- [ ] Screen renders correctly in test terminals
- [ ] No architecture violations (Konsist)
- [ ] Code reviewed and approved
- [ ] Documentation updated

---

## References

- [Lanterna 3.1.2 Documentation](https://github.com/mabe02/lanterna/tree/master/docs)
- [gRPC Kotlin Documentation](https://grpc.io/docs/languages/kotlin/)
- [DCM Architecture](../architecture.md)
- [TUI Analysis](tui-analysis.md)

---

_Last Updated: 2025-11-29_
