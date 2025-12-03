# TUI Admin Interface - Glossary & Terminology

**Version:** 1.0
**Date:** 2025-11-29
**Audience:** Developers, Operators, Stakeholders
**Related:** [TUI Tech Spec](./tui-admin-interface.md)

---

## Purpose

This glossary defines technical terms used in the DVMM TUI Admin Interface documentation. It serves developers unfamiliar with gRPC/Protobuf, operators configuring the system, and stakeholders reviewing technical specifications.

---

## Core Concepts

### TUI (Terminal User Interface)

A text-based user interface that runs in a terminal/console. Unlike a GUI (Graphical User Interface), a TUI uses characters, colors, and keyboard input instead of windows and mouse clicks.

**Example:** The DVMM TUI displays approval queues as text tables, uses arrow keys for navigation, and keyboard shortcuts like `A` for approve.

**Why TUI?** SSH administrators can use the interface remotely without X11 forwarding or web browser access.

---

### gRPC (gRPC Remote Procedure Calls)

A high-performance framework for client-server communication. gRPC allows the TUI client to call functions on the backend server as if they were local functions.

```
┌────────────┐                      ┌────────────┐
│ TUI Client │  ──ApproveRequest──► │   Server   │
│            │  ◄──CommandResult──  │            │
└────────────┘                      └────────────┘
```

**Key Features:**
- **Binary protocol** - More efficient than JSON
- **Type-safe** - Both sides know exactly what data to expect
- **Streaming** - Server can push updates to client in real-time

**Analogy:** If REST is like sending letters (request, wait, response), gRPC streaming is like a phone call (continuous conversation).

---

### Protobuf (Protocol Buffers)

A language for defining data structures and service contracts. Protobuf definitions are compiled into code that both client and server use.

**Example Definition:**
```protobuf
message ApproveCommand {
  string request_id = 1;
  optional string comment = 2;
}
```

**Benefits:**
- Single source of truth for API contract
- Auto-generated code in Kotlin/Java
- Backward-compatible evolution

---

### Unix Domain Socket

A communication channel that exists as a file on the filesystem. Unlike TCP sockets (which use IP addresses and ports), Unix sockets use file paths.

**Path Example:** `/var/run/dvmm/tui.sock`

**Advantages over TCP:**
- **Faster** - No network stack overhead (~0.1ms vs ~1-3ms)
- **More secure** - Protected by file permissions, no network exposure
- **Simpler auth** - Kernel provides Unix user identity (SO_PEERCRED)

**Limitation:** Only works on the same machine (Linux/macOS, not Windows).

---

### Server Streaming

A gRPC pattern where the server sends multiple messages over a single connection. The client makes one request, then receives a stream of responses.

```
Client                           Server
  │                                │
  │───SubscribeApprovals()────────►│
  │                                │
  │◄──────ApprovalEvent (new)──────│
  │                                │
  │◄──────ApprovalEvent (approved)─│
  │                                │
  │◄──────ApprovalEvent (new)──────│
  │           ...                  │
```

**Use Cases in TUI:**
- Real-time approval queue updates
- Live health dashboard status

---

### Unary RPC

A simple gRPC pattern: one request, one response. Like a traditional HTTP request.

```
Client                        Server
  │                             │
  │───ApproveRequest()─────────►│
  │                             │
  │◄──────CommandResult─────────│
```

**Use Cases in TUI:**
- Approve/Reject actions
- Get request details
- Authentication

---

## Authentication Terms

### SO_PEERCRED

A Unix socket feature that allows the server to query the credentials (UID, GID, PID) of the connected client process.

**How it works:**
1. TUI connects to Unix socket
2. Server calls `getsockopt(SO_PEERCRED)`
3. Kernel returns Unix UID of TUI process
4. Server maps UID to DVMM user

**Security benefit:** Cannot be spoofed - the kernel guarantees the identity.

---

### Unix User Mapping

Configuration that maps Unix usernames to DVMM users. When an SSH admin runs the TUI, they're automatically authenticated.

**Example Configuration:**
```yaml
unix-user-mappings:
  admin:           # Unix username
    user-id: admin@example.com
    tenant-id: default
    roles: [ADMIN]
```

**Result:** User `admin` logs into SSH, runs TUI, and is automatically `admin@example.com` in DVMM.

---

### Session Token

A credential issued after authentication that proves identity for subsequent requests. Included as gRPC metadata on every call.

**Lifecycle:**
1. Authenticate (Unix user or login)
2. Receive session token
3. Include token in all gRPC calls
4. Token expires after timeout (default: 4 hours)

---

## Infrastructure Terms

### Lanterna

A Java library for building terminal user interfaces. Lanterna handles:
- Drawing text and boxes to the terminal
- Reading keyboard input
- Managing screen state
- Terminal compatibility (xterm, iTerm2, etc.)

**Analogy:** Lanterna is to TUI what React is to web UI - a framework for building interfaces.

---

### Circuit Breaker

A resilience pattern that stops calling a failing service to let it recover.

```
CLOSED ──(failures)──► OPEN ──(timeout)──► HALF-OPEN
   ▲                                           │
   └───────────(success)───────────────────────┘
```

**States:**
- **Closed** - Normal operation, requests pass through
- **Open** - Failures exceeded threshold, requests rejected immediately
- **Half-Open** - After timeout, allow one request to test if service recovered

**Used for:** VMware API calls, preventing cascade failures

---

### Exponential Backoff

A retry strategy that increases wait time between attempts.

**Example Sequence:**
| Attempt | Wait |
|---------|------|
| 1 | 0s (immediate) |
| 2 | 1s |
| 3 | 2s |
| 4 | 4s |
| 5 | 8s |

**Purpose:** Avoids overwhelming a recovering service with retry storms.

---

### Connection Pooling

Reusing established connections instead of creating new ones for each request.

**For gRPC:** A single `ManagedChannel` handles multiple concurrent requests.

**For vSphere:** Session is kept alive and reused for multiple API calls.

---

## UI/UX Terms

### Focus

The currently active UI element that receives keyboard input. Only one element has focus at a time.

**Visual Indicator:** Usually highlighted with inverted colors or a border.

---

### Modal Dialog

A popup that blocks interaction with the main screen until dismissed.

**Example:** Rejection reason dialog - must enter reason or cancel before returning to queue.

---

### Status Badge

A small colored indicator showing state.

| Symbol | Color | Meaning |
|--------|-------|---------|
| ● | Green | Healthy / Connected |
| ○ | Yellow | Degraded / Warning |
| ✖ | Red | Unhealthy / Error |

---

### Toast

A temporary notification message that appears briefly then fades.

**Example:** "Request approved!" appears for 3 seconds after successful approval.

---

## Protocol Terms

### RPC (Remote Procedure Call)

Calling a function on a remote server as if it were local. gRPC is one implementation of RPC.

---

### Metadata

Header-like key-value pairs attached to gRPC calls. Used for authentication tokens, correlation IDs, tenant context.

**Example:**
```
authorization: Bearer eyJ...
x-correlation-id: abc123
x-tenant-id: acme-corp
```

---

### Keepalive

Periodic messages sent to maintain an idle connection. Prevents network equipment from closing inactive connections.

**gRPC Default:** Ping every 2 minutes if no activity.

---

### Deadline

Maximum time to wait for an RPC to complete. After deadline, the call is cancelled.

**Example:** 30-second deadline for ApproveCommand - if server doesn't respond in 30s, client shows timeout error.

---

## Event Sourcing Terms

### Event

An immutable record of something that happened. Events are never modified or deleted.

**Examples:**
- `VmRequestCreated`
- `VmRequestApproved`
- `VmProvisioned`

---

### Aggregate

A cluster of domain objects treated as a unit. In DVMM, `VmRequest` is an aggregate.

---

### Projection

A read-optimized view built from events. The approval queue is a projection of `VmRequest` events filtered by status.

---

## Abbreviations

| Abbreviation | Full Form |
|--------------|-----------|
| TUI | Terminal User Interface |
| gRPC | gRPC Remote Procedure Calls |
| RPC | Remote Procedure Call |
| Protobuf | Protocol Buffers |
| UDS | Unix Domain Socket |
| SSE | Server-Sent Events |
| JWT | JSON Web Token |
| UID | Unix User ID |
| GID | Unix Group ID |
| PID | Process ID |
| RTT | Round-Trip Time |
| IPC | Inter-Process Communication |

---

## Further Reading

- [gRPC Documentation](https://grpc.io/docs/)
- [Protocol Buffers Language Guide](https://developers.google.com/protocol-buffers/docs/proto3)
- [Lanterna GitHub](https://github.com/mabe02/lanterna)
- [Unix Domain Sockets (man page)](https://man7.org/linux/man-pages/man7/unix.7.html)
- [Circuit Breaker Pattern](https://martinfowler.com/bliki/CircuitBreaker.html)

---

_Last Updated: 2025-11-29_
