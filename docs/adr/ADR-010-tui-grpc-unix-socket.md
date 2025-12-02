# ADR-010: TUI Communication via gRPC over Unix Domain Socket

**Status:** Proposed
**Date:** 2025-11-29
**Author:** DVMM Team
**Deciders:** Architecture Team
**Related:** [TUI Admin Interface Tech Spec](../tech-specs/tui-admin-interface.md)

---

## Context

The DVMM TUI Admin Interface requires communication between a standalone terminal client (`dvmm-tui`) and the Spring Boot backend (`dvmm-app`). The primary use case is SSH administrators performing quick approval actions without needing a web browser.

### Requirements

| Requirement | Priority |
|-------------|----------|
| Real-time streaming (live approval queue updates) | Must |
| Low latency for interactive keyboard-driven UI | Must |
| Type-safe contract between client and server | Must |
| No Spring dependencies in TUI client JAR | Must |
| Works over SSH on local server | Must |
| Support concurrent admin sessions | Should |

### Alternatives Considered

#### Option A: HTTP REST + Server-Sent Events (SSE)

```
┌─────────────┐      HTTP/SSE      ┌─────────────┐
│  dvmm-tui   │◄──────────────────►│  dvmm-app   │
└─────────────┘                    └─────────────┘
```

**Pros:**
- Familiar technology, easy debugging
- Works with standard HTTP tooling
- No additional protocol dependencies

**Cons:**
- SSE is unidirectional (server→client only)
- Requires separate REST endpoints for commands
- Higher protocol overhead (~2-5ms per request)
- Connection management complexity for reconnects

#### Option B: WebSocket

```
┌─────────────┐      WebSocket     ┌─────────────┐
│  dvmm-tui   │◄─────────────────►│  dvmm-app   │
└─────────────┘                    └─────────────┘
```

**Pros:**
- Bidirectional communication
- Lower overhead than HTTP
- Well-supported in Java

**Cons:**
- No built-in typing (need manual JSON/Protobuf handling)
- Frame boundary issues with large messages
- More complex error handling
- Overkill for request-response patterns

#### Option C: gRPC over TCP

```
┌─────────────┐    gRPC (TCP:9090)  ┌─────────────┐
│  dvmm-tui   │◄───────────────────►│  dvmm-app   │
└─────────────┘                     └─────────────┘
```

**Pros:**
- Type-safe Protobuf contracts
- Built-in streaming (unary, server, client, bidirectional)
- Efficient binary protocol
- Excellent tooling (code generation, testing)

**Cons:**
- Network exposure (firewall/TLS required)
- Overkill for local-only communication
- Higher latency than IPC (~1-3ms)

#### Option D: gRPC over Unix Domain Socket (Selected)

```
┌─────────────┐   gRPC (/var/run/dvmm/tui.sock)   ┌─────────────┐
│  dvmm-tui   │◄─────────────────────────────────►│  dvmm-app   │
└─────────────┘                                   └─────────────┘
```

**Pros:**
- All benefits of gRPC (typing, streaming, tooling)
- Near-zero latency (~0.1ms)
- No network exposure (socket file permissions)
- Unix user authentication via peer credentials
- No TLS overhead (kernel-level security)

**Cons:**
- Linux/macOS only (no Windows)
- Requires file system permissions setup
- Less familiar to some developers

---

## Decision

**We will use gRPC over Unix Domain Socket** for TUI-to-backend communication.

### Rationale

1. **Latency**: Unix sockets provide ~0.1ms latency vs ~1-3ms for TCP. For interactive TUI with frequent key presses, this matters.

2. **Security**: Socket file permissions (660, group `dvmm-admins`) eliminate network attack surface. Unix user mapping provides zero-friction authentication for SSH admins.

3. **Streaming**: gRPC server streaming naturally fits the real-time approval queue and health dashboard requirements.

4. **Type Safety**: Protobuf service definitions in `dvmm-tui-protocol` module are shared between client and server, eliminating serialization bugs.

5. **Scope Alignment**: TUI is explicitly for local SSH admins. Windows/remote access is out of scope (use web UI instead).

---

## Consequences

### Positive

- Minimal latency for responsive TUI
- Strong security posture (no network exposure)
- Type-safe API evolution via Protobuf
- Clean module separation (protocol shared, no cross-dependencies)

### Negative

- Windows not supported (acceptable per scope)
- Developers need gRPC/Protobuf familiarity
- Socket file permissions require ops documentation

### Neutral

- Two communication channels (gRPC for TUI, HTTP for web)
- Additional infrastructure component to monitor

---

## Implementation Notes

### Unix Socket Configuration

```yaml
# dvmm-app application.yml
grpc:
  server:
    unix-socket:
      enabled: true
      path: /var/run/dvmm/tui.sock
      permissions: 660
      group: dvmm-admins
```

### Authentication Flow

```
1. TUI connects to Unix socket
2. Server extracts Unix UID via SO_PEERCRED
3. UID mapped to DVMM user via unix-user-mappings config
4. Session token issued for subsequent calls
```

### Protobuf Service Definition

```protobuf
service DvmmTuiService {
  // Unary RPCs
  rpc Authenticate(AuthRequest) returns (AuthResponse);
  rpc ApproveRequest(ApproveCommand) returns (CommandResult);
  rpc RejectRequest(RejectCommand) returns (CommandResult);

  // Server Streaming RPCs (real-time updates)
  rpc SubscribeApprovals(SubscribeRequest) returns (stream ApprovalEvent);
  rpc SubscribeHealth(SubscribeRequest) returns (stream HealthUpdate);
}
```

---

## Alternatives Not Selected

| Option | Rejection Reason |
|--------|------------------|
| HTTP/SSE | Higher latency, complex reconnect handling |
| WebSocket | No type safety, manual serialization |
| gRPC/TCP | Network exposure, TLS overhead for local use |
| Named Pipes | Less tooling support, platform-specific APIs |

---

## References

- [gRPC Unix Domain Socket Support](https://grpc.io/docs/guides/custom-name-resolution/)
- [Protobuf Language Guide](https://developers.google.com/protocol-buffers/docs/proto3)
- [SO_PEERCRED for Unix Authentication](https://man7.org/linux/man-pages/man7/unix.7.html)

---

_Last Updated: 2025-11-29_
