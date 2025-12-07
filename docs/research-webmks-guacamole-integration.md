# WebMKS and Guacamole Integration for DVMM

**Author:** Claude (Research Agent)
**Date:** 2025-12-07
**Version:** 1.0
**Status:** Research Complete
**Phase:** Pre-Implementation Research

---

## Executive Summary

This document provides deep technical research on integrating **VMware WebMKS** (native vSphere console) with **Apache Guacamole** for DVMM. WebMKS enables browser-based VM console access without SSH/RDP, providing true console access including BIOS, boot screens, and pre-OS interaction.

**Key Findings:**

1. **Guacamole does NOT natively support WebMKS** - GUACAMOLE-1641 is a work-in-progress improvement that has not been merged
2. **WebMKS is VNC-over-WebSocket** - The protocol is essentially VNC tunneled through WebSocket with custom authentication
3. **vSphere 7.0+ removed direct VNC** - WebMKS via `AcquireTicket("webmks")` is now the only supported method
4. **Three viable integration approaches exist** - Direct WebMKS SDK, Guacamole custom protocol, or WebSocket proxy

**Recommendation:** Implement a **dual-approach strategy**:
- **Phase 1 (MVP):** Direct VMware HTML Console SDK integration for native WebMKS
- **Phase 2 (Future):** Unified Guacamole gateway when GUACAMOLE-1641 matures or via custom protocol plugin

---

## Table of Contents

1. [Understanding WebMKS](#1-understanding-webmks)
2. [Guacamole WebMKS Support Status](#2-guacamole-webmks-support-status)
3. [vCenter API for WebMKS Tickets](#3-vcenter-api-for-webmks-tickets)
4. [Integration Architecture Options](#4-integration-architecture-options)
5. [Security Considerations](#5-security-considerations)
6. [Recommended Architecture for DVMM](#6-recommended-architecture-for-dvmm)
7. [Implementation Details](#7-implementation-details)
8. [Testing Strategy](#8-testing-strategy)
9. [Risk Assessment](#9-risk-assessment)
10. [References](#10-references)

---

## 1. Understanding WebMKS

### 1.1 What is MKS/WebMKS?

**MKS (Mouse-Keyboard-Screen)** is VMware's protocol for accessing virtual machine consoles. It provides:

- Full console access (not just SSH/RDP)
- Pre-boot visibility (BIOS, boot loader, kernel panic)
- Works regardless of guest OS state
- No agent required in the VM

**WebMKS** is the HTML5 implementation of MKS, enabling browser-based console access via WebSocket.

### 1.2 Protocol Architecture

```text
┌─────────────┐      HTTPS/WSS      ┌─────────────┐      SOAP      ┌─────────────┐
│   Browser   │ ─────────────────── │   vCenter   │ ─────────────── │    ESXi     │
│  (wmks.js)  │                     │   Server    │                 │    Host     │
└──────┬──────┘                     └──────┬──────┘                 └──────┬──────┘
       │                                   │                               │
       │ 1. POST /vcenter/vm/{id}/         │                               │
       │    console/tickets                │                               │
       │ ─────────────────────────────────>│                               │
       │                                   │                               │
       │ 2. Returns ticket + ESXi host     │                               │
       │ <─────────────────────────────────│                               │
       │                                   │                               │
       │ 3. wss://{esxi}:443/ticket/{token}                                │
       │ ─────────────────────────────────────────────────────────────────>│
       │                                   │                               │
       │ 4. VNC protocol over WebSocket                                    │
       │ <─────────────────────────────────────────────────────────────────│
       │                                   │                               │
```

### 1.3 Key Protocol Characteristics

| Aspect | Details |
|--------|---------|
| **Transport** | WebSocket Secure (wss://) |
| **Port** | 443 (ESXi host) |
| **Path Format** | `/ticket/{ticket-token}` |
| **Underlying Protocol** | VNC (RFB) with VMware extensions |
| **Authentication** | One-time ticket (30-minute validity) |
| **Encryption** | TLS (WebSocket Secure) |
| **Client Library** | VMware HTML Console SDK (wmks.js) |

### 1.4 vSphere Version Compatibility

| Version | VNC Support | WebMKS Support | Notes |
|---------|-------------|----------------|-------|
| vSphere 5.x | Direct VNC | Limited | Requires proxy (mksproxy) |
| vSphere 6.0+ | VNC (deprecated) | Native | Both methods work |
| vSphere 7.0+ | **REMOVED** | Native | WebMKS only option |
| vSphere 8.0+ | None | Native | WebMKS mandatory |

> **Critical:** In vSphere 7.0+, the ESXi built-in VNC server was removed. The only supported console access method is WebMKS via `AcquireTicket("webmks")`.

### 1.5 WebMKS vs Traditional Guacamole Protocols

| Feature | WebMKS | Guacamole SSH | Guacamole VNC |
|---------|--------|---------------|---------------|
| **Pre-boot access** | Yes | No | Yes (if VNC available) |
| **Guest agent required** | No | Yes (sshd) | Yes (VNC server) |
| **Works when OS crashed** | Yes | No | Depends |
| **Authentication** | vCenter ticket | SSH keys/password | VNC password |
| **Port requirements** | 443 to ESXi | 22 to VM | 5900+ to VM |
| **Browser-native** | Yes (WebSocket) | Via guacd | Via guacd |

---

## 2. Guacamole WebMKS Support Status

### 2.1 GUACAMOLE-1641: Current Status

**Issue:** [GUACAMOLE-1641 - Add vSphere support to VNC protocol](https://issues.apache.org/jira/browse/GUACAMOLE-1641)

| Field | Value |
|-------|-------|
| **Type** | Improvement |
| **Component** | guacamole (guacd) |
| **Priority** | Minor |
| **Status** | Open (as of research date) |
| **Reporter** | Mike Beynon |
| **Assignee** | Unassigned |

### 2.2 Implementation Approach in GUACAMOLE-1641

The proposed implementation involves:

1. **Extend guacd VNC protocol** to accept VM object ID
2. **Use vSphere API** to establish session with vCenter
3. **Request WebMKS ticket** via API
4. **Connect to ESXi host** via WebSocket
5. **Relay VNC protocol** through guacd to browser

```text
Browser ──Guac Protocol──> guacd ──WebSocket/VNC──> ESXi
                             │
                             └── vCenter API (ticket acquisition)
```

### 2.3 Technical Challenges Identified

From the GUACAMOLE-1641 discussion:

1. **Username/password prompting** - Race condition with `guac_client_owner_supports_required()`
2. **Protocol version synchronization** - Memory barrier issues between threads
3. **WebSocket-to-guacd bridging** - VNC typically runs over TCP, not WebSocket

### 2.4 Work-in-Progress Code

The developer's fork contains experimental code at branch `vm-server-consoles`:
- Not merged to mainline Apache Guacamole
- Described as "work in progress"
- No official release timeline

### 2.5 Assessment: Can We Use GUACAMOLE-1641?

**Current Answer: No**

| Consideration | Assessment |
|---------------|------------|
| **Maturity** | Experimental, not production-ready |
| **Support** | No official Apache support |
| **Timeline** | Unknown - open issue since 2020+ |
| **Alternatives** | Direct WebMKS SDK integration is more reliable |

**Recommendation:** Do not depend on GUACAMOLE-1641 for MVP. Monitor progress and consider adoption when/if merged.

---

## 3. vCenter API for WebMKS Tickets

### 3.1 API Methods

There are two API approaches for acquiring WebMKS tickets:

#### 3.1.1 SOAP API (VIM API)

```java
// Using VCF SDK / govmomi / pyvmomi
VirtualMachineTicket ticket = vm.acquireTicket("webmks");
```

**Response Structure:**
```java
public class VirtualMachineTicket {
    String ticket;      // One-time token (cst-xxxxx format)
    String host;        // ESXi host IP/FQDN
    int port;           // Usually 443
    String sslThumbprint; // ESXi certificate thumbprint
}
```

#### 3.1.2 REST API (vSphere Automation API)

```bash
POST /api/vcenter/vm/{vm}/console/tickets
Content-Type: application/json
vmware-api-session-id: {session-id}

{
  "spec": {
    "type": "WEBMKS"
  }
}
```

**Response:**
```json
{
  "ticket": "cst-VCT-52b06c87-3a03-3a99-4e9e-38b770f936f9--tp-18-A4-...",
  "host": "esxi01.example.com",
  "port": 443,
  "ssl_thumbprint": "A4:B2:C3:..."
}
```

### 3.2 Ticket Lifecycle

| Property | Value |
|----------|-------|
| **Validity** | 30 minutes |
| **Usage** | Single-use (consumed on first connection) |
| **Scope** | Specific VM only |
| **Required State** | VM must be powered on |
| **Required Privilege** | `VirtualMachine.Interact.ConsoleInteract` |

### 3.3 VCF SDK 9.0 Implementation

Based on DVMM's existing `VsphereClient.kt` architecture:

```kotlin
// dvmm-infrastructure/src/main/kotlin/.../vmware/VsphereClient.kt

/**
 * Acquires a WebMKS console ticket for the specified VM.
 *
 * @param vmId The vSphere managed object reference ID (e.g., "vm-123")
 * @return WebMksTicket containing connection details
 * @throws VsphereError if VM is not powered on or ticket acquisition fails
 */
public suspend fun acquireWebMksTicket(vmId: VmId): Result<WebMksTicket, VsphereError> =
    executeResilient("acquireWebMksTicket") {
        val tenantId = TenantContext.current()
        val session = ensureSession(tenantId).getOrElse {
            return@executeResilient it.failure()
        }

        withContext(Dispatchers.IO) {
            try {
                val vmRef = moRef("VirtualMachine", vmId.value)

                // Check VM power state first
                val powerState = getProperty(session, vmRef, "runtime.powerState") as? String
                if (powerState != "poweredOn") {
                    return@withContext VsphereError.InvalidState(
                        "VM must be powered on for console access (current: $powerState)"
                    ).failure()
                }

                // Acquire WebMKS ticket
                val ticket = session.vimPort.acquireTicket(vmRef, "webmks")

                WebMksTicket(
                    ticket = ticket.ticket,
                    host = ticket.host,
                    port = ticket.port ?: 443,
                    sslThumbprint = ticket.sslThumbprint
                ).success()

            } catch (e: Exception) {
                VsphereError.ApiError("Failed to acquire WebMKS ticket", e).failure()
            }
        }
    }
```

### 3.4 WebSocket Connection URL Construction

```kotlin
/**
 * Constructs the WebSocket URL for WebMKS connection.
 * Format: wss://{host}:{port}/ticket/{ticket}
 */
fun WebMksTicket.toWebSocketUrl(): String =
    "wss://${host}:${port}/ticket/${ticket}"
```

---

## 4. Integration Architecture Options

### 4.1 Option A: Direct VMware HTML Console SDK (Recommended for MVP)

Embed VMware's official HTML Console SDK directly in the DVMM frontend.

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                           DVMM Architecture                              │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐     ┌─────────────────┐     ┌──────────────────┐   │
│  │  React Frontend │     │   DVMM API      │     │   VsphereClient  │   │
│  │  + wmks.min.js  │────▶│   (WebFlux)     │────▶│   (VCF SDK 9.0)  │   │
│  └────────┬────────┘     └────────┬────────┘     └────────┬─────────┘   │
│           │                       │                       │             │
│           │ 1. Request ticket     │                       │             │
│           │ ─────────────────────▶│                       │             │
│           │                       │ 2. acquireTicket      │             │
│           │                       │ ─────────────────────▶│             │
│           │                       │                       │ 3. vCenter  │
│           │                       │                       │────────────▶│
│           │                       │                       │             │
│           │ 4. Return ticket URL  │◀──────────────────────│             │
│           │◀──────────────────────│                       │             │
│           │                       │                       │             │
└───────────┼───────────────────────┴───────────────────────┴─────────────┘
            │
            │ 5. wss://{esxi}/ticket/{token}
            │    (Direct browser-to-ESXi connection)
            ▼
     ┌──────────────┐
     │  ESXi Host   │
     └──────────────┘
```

**Advantages:**
- Official VMware SDK with support
- Direct browser-to-ESXi connection (lowest latency)
- No additional proxy infrastructure
- Already works with DVMM's multi-tenant model

**Disadvantages:**
- Browser must reach ESXi hosts directly (network topology constraint)
- SSL certificate handling for self-signed ESXi certs
- Separate console technology from Guacamole SSH

### 4.2 Option B: WebSocket Proxy (Backend-Mediated)

DVMM backend acts as WebSocket proxy between browser and ESXi.

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                           DVMM Architecture                              │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐     ┌─────────────────────────────────────────┐    │
│  │  React Frontend │     │              DVMM Backend               │    │
│  │  + wmks.min.js  │     │  ┌────────────┐    ┌────────────────┐  │    │
│  └────────┬────────┘     │  │ REST API   │    │ WS Proxy       │  │    │
│           │              │  │ /ticket    │    │ /console/ws    │  │    │
│           │              │  └─────┬──────┘    └───────┬────────┘  │    │
│           │              │        │                   │           │    │
│           │              │        │                   │ 4. Relay  │    │
│           │ 1. Request   │        │                   │    binary │    │
│           │ ────────────▶│────────▶                   │    frames │    │
│           │              │        │                   │           │    │
│           │ 3. Ticket +  │        │ 2. vCenter API    │           │    │
│           │    proxy URL │◀───────│◀─────────────────▶│           │    │
│           │◀─────────────│        │                   │           │    │
│           │              │        │                   ▼           │    │
│           │ 4. Connect   │        │           ┌──────────────┐   │    │
│           │    to proxy  │        │           │ WS Client to │   │    │
│           │ ────────────▶│────────────────────│ ESXi         │   │    │
│           │              │        │           └──────┬───────┘   │    │
│           │              └────────┴──────────────────┼───────────┘    │
└───────────┴──────────────────────────────────────────┼────────────────┘
                                                       │
                                               ┌───────▼───────┐
                                               │   ESXi Host   │
                                               └───────────────┘
```

**Advantages:**
- Browser only needs to reach DVMM backend (no direct ESXi access)
- Backend handles SSL certificate validation
- Single point for security/audit logging
- Works with restrictive network topologies

**Disadvantages:**
- Additional latency (double hop)
- Higher backend load (binary frame relay)
- More complex implementation
- Must handle WebSocket backpressure

### 4.3 Option C: Custom Guacamole Protocol Plugin

Develop a custom guacd protocol plugin for WebMKS.

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                           DVMM Architecture                              │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐     ┌─────────────────┐     ┌──────────────────┐   │
│  │  React Frontend │     │   DVMM API      │     │     guacd        │   │
│  │  + guac-common  │────▶│   (WebFlux)     │────▶│  + webmks.so     │   │
│  │      -js        │     │                 │     │  (custom plugin) │   │
│  └─────────────────┘     └─────────────────┘     └────────┬─────────┘   │
│                                                           │             │
│                                                           │ WebSocket   │
│                                                           │ to ESXi     │
│                                                           ▼             │
│                                                    ┌──────────────┐     │
│                                                    │  ESXi Host   │     │
│                                                    └──────────────┘     │
└─────────────────────────────────────────────────────────────────────────┘
```

**Advantages:**
- Unified UI for all console types (SSH, RDP, VNC, WebMKS)
- Leverages Guacamole's existing session management
- Single client library in frontend (guacamole-common-js)

**Disadvantages:**
- Requires C development for libguac plugin
- Complex: must implement VNC-over-WebSocket in C
- No official VMware support for this approach
- Maintenance burden for custom plugin

### 4.4 Option Comparison Matrix

| Criterion | Option A: Direct SDK | Option B: WS Proxy | Option C: Guac Plugin |
|-----------|---------------------|--------------------|-----------------------|
| **Development Effort** | Low | Medium | High |
| **Latency** | Lowest | Medium | Medium |
| **Network Requirements** | Browser→ESXi | Browser→Backend only | Browser→guacd→ESXi |
| **Official Support** | VMware SDK | Custom | Custom |
| **UI Consistency** | Separate from SSH | Separate from SSH | Unified with SSH |
| **Testing with VCSIM** | Not possible* | Not possible* | Not possible* |
| **SSL Handling** | Complex (browser) | Simple (backend) | Simple (guacd) |
| **Production Readiness** | High | Medium | Low |

*Note: VCSIM does not implement WebMKS/console functionality. Testing requires real vCenter/ESXi.

---

## 5. Security Considerations

### 5.1 Authentication Flow

```text
┌─────────┐      ┌─────────┐      ┌─────────┐      ┌─────────┐
│ Browser │      │  DVMM   │      │ vCenter │      │  ESXi   │
└────┬────┘      └────┬────┘      └────┬────┘      └────┬────┘
     │                │                │                │
     │ 1. JWT Auth    │                │                │
     │ ──────────────▶│                │                │
     │                │                │                │
     │                │ 2. Validate    │                │
     │                │    tenant/user │                │
     │                │    permissions │                │
     │                │                │                │
     │                │ 3. Session     │                │
     │                │ ──────────────▶│                │
     │                │                │                │
     │                │ 4. acquireTicket("webmks")      │
     │                │ ──────────────▶│                │
     │                │                │                │
     │                │ 5. Ticket      │                │
     │                │◀───────────────│                │
     │                │                │                │
     │ 6. Ticket URL  │                │                │
     │◀───────────────│                │                │
     │                │                │                │
     │ 7. wss://.../ticket/{token}     │                │
     │ ────────────────────────────────────────────────▶│
     │                │                │                │
     │ 8. Console session established  │                │
     │◀────────────────────────────────────────────────│
```

### 5.2 Multi-Tenant Isolation

| Layer | Isolation Mechanism |
|-------|---------------------|
| **API Gateway** | JWT `tenant_id` claim validation |
| **Application** | Verify VM belongs to tenant before ticket request |
| **vCenter** | Use tenant-specific vCenter credentials |
| **Database** | PostgreSQL RLS on VM projections |

**Critical Check in Handler:**

```kotlin
// ConsoleAccessCommandHandler.kt
public suspend fun handle(command: RequestConsoleAccessCommand): Result<ConsoleTicket, Error> {
    // 1. Load VM from projection (RLS enforced)
    val vm = vmProjectionRepository.findById(command.vmId)
        ?: return NotFound("VM not found").failure()

    // 2. Verify ownership
    if (vm.tenantId != command.tenantId) {
        // Should never happen due to RLS, but defense in depth
        logger.warn { "Cross-tenant access attempt: ${command.userId} tried to access VM ${command.vmId}" }
        return NotFound("VM not found").failure() // Opaque error
    }

    // 3. Verify user authorization (owner or admin)
    if (vm.requesterId != command.userId && !authService.isAdmin(command.userId)) {
        return NotFound("VM not found").failure() // Opaque error
    }

    // 4. Acquire ticket
    return vspherePort.acquireWebMksTicket(VmId(vm.vsphereVmId))
}
```

### 5.3 SSL Certificate Handling

ESXi hosts typically use self-signed certificates. Options:

| Approach | Pros | Cons |
|----------|------|------|
| **Trust ESXi CA in browser** | Proper security | Requires client configuration |
| **Pre-accept via HTTPS** | One-time per ESXi host | Manual step |
| **Backend proxy** | Handles SSL server-side | Additional infrastructure |
| **`--ignore-cert-errors`** | Easy for dev | **NEVER in production** |

**Recommended:** For production, import ESXi certificates into a trusted CA or use backend proxy.

### 5.4 Ticket Security

| Concern | Mitigation |
|---------|------------|
| **Ticket interception** | TLS for all communication |
| **Ticket replay** | Single-use + 30-minute expiry |
| **Ticket enumeration** | Tickets are cryptographically random |
| **Session hijacking** | Short validity, immediate use expected |

### 5.5 Audit Logging

Every console access must be logged:

```kotlin
// Log to security audit trail
auditLogger.security(
    event = "CONSOLE_ACCESS_REQUESTED",
    userId = command.userId,
    tenantId = command.tenantId,
    vmId = command.vmId,
    vmName = vm.name,
    clientIp = requestContext.clientIp,
    userAgent = requestContext.userAgent,
    result = if (result.isSuccess) "GRANTED" else "DENIED",
    reason = result.errorOrNull()?.message
)
```

---

## 6. Recommended Architecture for DVMM

### 6.1 Phase 1: Direct VMware SDK (MVP)

For MVP, implement **Option A** with the following architecture:

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                         DVMM Console Integration                         │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Frontend (dvmm-web)                                                     │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │  src/features/console/                                              │ │
│  │  ├── components/                                                    │ │
│  │  │   ├── VmConsoleButton.tsx      # "Open Console" button          │ │
│  │  │   ├── WebMksConsole.tsx        # WMKS wrapper component         │ │
│  │  │   └── ConsoleModal.tsx         # Full-screen console modal      │ │
│  │  ├── hooks/                                                         │ │
│  │  │   └── useWebMksConsole.ts      # Ticket acquisition + connect   │ │
│  │  └── lib/                                                           │ │
│  │      └── wmks-loader.ts           # Dynamic SDK loading             │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  Backend (dvmm-*)                                                        │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │  dvmm-api/                                                          │ │
│  │  └── console/ConsoleController.kt  # POST /api/v1/vms/{id}/console │ │
│  │                                                                     │ │
│  │  dvmm-application/                                                  │ │
│  │  └── console/                                                       │ │
│  │      ├── RequestConsoleTicketCommand.kt                            │ │
│  │      └── RequestConsoleTicketHandler.kt                            │ │
│  │                                                                     │ │
│  │  dvmm-infrastructure/                                               │ │
│  │  └── vmware/VsphereClient.kt       # +acquireWebMksTicket()        │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### 6.2 Phase 2: Backend WebSocket Proxy (Post-MVP)

If network topology prevents direct browser-to-ESXi:

```text
┌─────────────────────────────────────────────────────────────────────────┐
│  dvmm-api/                                                              │
│  └── console/                                                           │
│      ├── ConsoleController.kt         # REST ticket endpoint            │
│      └── WebMksProxyHandler.kt        # WebSocket proxy handler         │
│                                                                          │
│  dvmm-infrastructure/                                                    │
│  └── console/                                                            │
│      └── WebMksWebSocketClient.kt     # Connects to ESXi                │
└─────────────────────────────────────────────────────────────────────────┘
```

### 6.3 Phase 3: Unified Guacamole Gateway (Future)

When/if GUACAMOLE-1641 matures or we develop custom plugin:

```text
┌─────────────────────────────────────────────────────────────────────────┐
│  Unified Console Gateway                                                 │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │  guacd                                                              │ │
│  │  ├── SSH protocol  (existing)                                       │ │
│  │  ├── RDP protocol  (existing)                                       │ │
│  │  ├── VNC protocol  (existing)                                       │ │
│  │  └── WebMKS protocol (custom or GUACAMOLE-1641)                    │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  Frontend: Single guacamole-common-js client for all protocols          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Implementation Details

### 7.1 VMware HTML Console SDK Setup

#### 7.1.1 Obtaining the SDK

The SDK is not available via npm. Download from:
- VMware Developer Portal: https://developer.vmware.com/
- VMware Downloads: Drivers and Tools section

Contents:
```text
html-console-sdk-2.2.x/
├── wmks.min.js         # Minified JavaScript
├── wmks.js             # Unminified (debugging)
├── css/
│   └── wmks-all.css    # Required styles
└── docs/               # API documentation
```

#### 7.1.2 Frontend Integration

```typescript
// src/features/console/lib/wmks-loader.ts

/**
 * Dynamically loads VMware WMKS SDK.
 * SDK files should be placed in public/vendor/wmks/
 */
export async function loadWmksSdk(): Promise<typeof WMKS> {
  // Load CSS
  const link = document.createElement('link');
  link.rel = 'stylesheet';
  link.href = '/vendor/wmks/css/wmks-all.css';
  document.head.appendChild(link);

  // Load JS (requires jQuery which should already be loaded)
  return new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = '/vendor/wmks/wmks.min.js';
    script.onload = () => resolve(window.WMKS);
    script.onerror = reject;
    document.head.appendChild(script);
  });
}
```

```typescript
// src/features/console/hooks/useWebMksConsole.ts

import { useMutation } from '@tanstack/react-query';
import { useAuth } from 'react-oidc-context';

interface ConsoleTicket {
  ticket: string;
  host: string;
  port: number;
  sslThumbprint: string;
  websocketUrl: string;
}

export function useWebMksConsole(vmId: string) {
  const auth = useAuth();

  return useMutation({
    mutationFn: async (): Promise<ConsoleTicket> => {
      const response = await fetch(`/api/v1/vms/${vmId}/console`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${auth.user?.access_token}`,
          'Content-Type': 'application/json',
        },
      });

      if (!response.ok) {
        throw new Error('Failed to acquire console ticket');
      }

      return response.json();
    },
  });
}
```

```tsx
// src/features/console/components/WebMksConsole.tsx

import { useEffect, useRef, useState } from 'react';
import { loadWmksSdk } from '../lib/wmks-loader';

interface WebMksConsoleProps {
  websocketUrl: string;
  onClose: () => void;
  onError: (error: Error) => void;
}

export function WebMksConsole({
  websocketUrl,
  onClose,
  onError
}: Readonly<WebMksConsoleProps>) {
  const containerRef = useRef<HTMLDivElement>(null);
  const wmksRef = useRef<WMKS.WebMKS | null>(null);
  const [status, setStatus] = useState<'loading' | 'connecting' | 'connected' | 'error'>('loading');

  useEffect(() => {
    let mounted = true;

    async function initConsole() {
      try {
        const WMKS = await loadWmksSdk();
        if (!mounted || !containerRef.current) return;

        setStatus('connecting');

        // Create WMKS instance
        const wmks = WMKS.createWMKS(containerRef.current.id, {
          rescale: true,
          changeResolution: true,
          useNativePixels: false,
          retryConnectionInterval: 3000,
        });

        wmksRef.current = wmks;

        // Handle connection events
        wmks.register(WMKS.CONST.Events.CONNECTION_STATE_CHANGE, (event: any, data: any) => {
          if (!mounted) return;

          switch (data.state) {
            case WMKS.CONST.ConnectionState.CONNECTED:
              setStatus('connected');
              break;
            case WMKS.CONST.ConnectionState.DISCONNECTED:
              setStatus('error');
              onError(new Error('Console disconnected'));
              break;
          }
        });

        wmks.register(WMKS.CONST.Events.ERROR, (event: any, data: any) => {
          if (!mounted) return;
          setStatus('error');
          onError(new Error(data.message || 'Console error'));
        });

        // Connect
        wmks.connect(websocketUrl);

      } catch (error) {
        if (!mounted) return;
        setStatus('error');
        onError(error as Error);
      }
    }

    void initConsole();

    return () => {
      mounted = false;
      wmksRef.current?.destroy();
    };
  }, [websocketUrl, onError]);

  return (
    <div className="relative w-full h-full bg-black">
      {status === 'loading' && (
        <div className="absolute inset-0 flex items-center justify-center">
          <span className="text-white">Loading console SDK...</span>
        </div>
      )}
      {status === 'connecting' && (
        <div className="absolute inset-0 flex items-center justify-center">
          <span className="text-white">Connecting to VM console...</span>
        </div>
      )}
      <div
        id="wmks-container"
        ref={containerRef}
        className="w-full h-full"
        data-testid="webmks-console"
      />
    </div>
  );
}
```

### 7.2 Backend API Implementation

```kotlin
// dvmm-api/src/main/kotlin/de/acci/dvmm/api/console/ConsoleController.kt

package de.acci.dvmm.api.console

import de.acci.dvmm.application.console.RequestConsoleTicketCommand
import de.acci.dvmm.application.console.RequestConsoleTicketHandler
import de.acci.eaf.auth.TokenClaims
import de.acci.eaf.tenant.TenantContext
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/vms/{vmId}/console")
public class ConsoleController(
    private val handler: RequestConsoleTicketHandler,
) {
    /**
     * Request a WebMKS console ticket for a VM.
     *
     * Returns connection details for the VMware HTML Console SDK.
     * The ticket is single-use and valid for 30 minutes.
     *
     * **Security:** Returns 404 for VMs that don't exist OR that the user
     * cannot access, to prevent tenant enumeration attacks.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public suspend fun requestConsoleTicket(
        @PathVariable vmId: UUID,
        @AuthenticationPrincipal claims: TokenClaims,
    ): ConsoleTicketResponse {
        val command = RequestConsoleTicketCommand(
            vmId = vmId,
            userId = claims.subject,
            tenantId = TenantContext.current(),
        )

        return handler.handle(command).fold(
            onSuccess = { ticket ->
                ConsoleTicketResponse(
                    ticket = ticket.ticket,
                    host = ticket.host,
                    port = ticket.port,
                    sslThumbprint = ticket.sslThumbprint,
                    websocketUrl = "wss://${ticket.host}:${ticket.port}/ticket/${ticket.ticket}",
                )
            },
            onFailure = { error ->
                // All errors return 404 to prevent enumeration
                throw NotFoundException("VM not found")
            }
        )
    }
}

public data class ConsoleTicketResponse(
    val ticket: String,
    val host: String,
    val port: Int,
    val sslThumbprint: String,
    val websocketUrl: String,
)
```

```kotlin
// dvmm-application/src/main/kotlin/de/acci/dvmm/application/console/RequestConsoleTicketHandler.kt

package de.acci.dvmm.application.console

import de.acci.dvmm.application.vmware.VspherePort
import de.acci.dvmm.application.vmware.WebMksTicket
import de.acci.dvmm.infrastructure.projection.VmProjectionRepository
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.util.UUID

public data class RequestConsoleTicketCommand(
    val vmId: UUID,
    val userId: UserId,
    val tenantId: TenantId,
)

public sealed interface ConsoleTicketError {
    public data object VmNotFound : ConsoleTicketError
    public data object VmNotPoweredOn : ConsoleTicketError
    public data object AccessDenied : ConsoleTicketError
    public data class TicketAcquisitionFailed(val reason: String) : ConsoleTicketError
}

@Component
public class RequestConsoleTicketHandler(
    private val vmProjectionRepository: VmProjectionRepository,
    private val vspherePort: VspherePort,
    private val authorizationService: AuthorizationService,
    private val auditLogger: AuditLogger,
) {
    private val logger = KotlinLogging.logger {}

    public suspend fun handle(
        command: RequestConsoleTicketCommand
    ): Result<WebMksTicket, ConsoleTicketError> {
        // 1. Load VM projection (RLS enforced)
        val vm = vmProjectionRepository.findById(command.vmId)
        if (vm == null) {
            logAudit(command, "DENIED", "VM not found")
            return ConsoleTicketError.VmNotFound.failure()
        }

        // 2. Defense in depth: verify tenant match
        if (vm.tenantId != command.tenantId) {
            logger.warn {
                "Cross-tenant console access attempt: user=${command.userId} " +
                "vm=${command.vmId} userTenant=${command.tenantId} vmTenant=${vm.tenantId}"
            }
            logAudit(command, "DENIED", "Cross-tenant access")
            return ConsoleTicketError.VmNotFound.failure()
        }

        // 3. Check user authorization (owner or admin)
        if (!canAccessConsole(command.userId, vm)) {
            logAudit(command, "DENIED", "Not authorized")
            return ConsoleTicketError.VmNotFound.failure() // Opaque error
        }

        // 4. Verify VM is powered on
        if (vm.powerState != "poweredOn") {
            logAudit(command, "DENIED", "VM not powered on")
            return ConsoleTicketError.VmNotPoweredOn.failure()
        }

        // 5. Acquire WebMKS ticket from vSphere
        return vspherePort.acquireWebMksTicket(vm.vsphereVmId).fold(
            onSuccess = { ticket ->
                logAudit(command, "GRANTED", "Ticket acquired")
                ticket.success()
            },
            onFailure = { error ->
                logger.error { "Failed to acquire WebMKS ticket: $error" }
                logAudit(command, "FAILED", error.toString())
                ConsoleTicketError.TicketAcquisitionFailed(error.toString()).failure()
            }
        )
    }

    private suspend fun canAccessConsole(userId: UserId, vm: VmProjection): Boolean {
        return vm.requesterId == userId || authorizationService.hasRole(userId, Role.ADMIN)
    }

    private suspend fun logAudit(
        command: RequestConsoleTicketCommand,
        result: String,
        reason: String,
    ) {
        auditLogger.security(
            event = "CONSOLE_TICKET_REQUEST",
            userId = command.userId,
            tenantId = command.tenantId,
            resourceId = command.vmId.toString(),
            result = result,
            details = mapOf("reason" to reason)
        )
    }
}
```

### 7.3 VsphereClient Extension

```kotlin
// dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/vmware/VsphereClient.kt
// Add to existing class:

/**
 * Acquires a WebMKS console ticket for the specified VM.
 *
 * The ticket enables browser-based console access via VMware HTML Console SDK.
 * Tickets are single-use and expire after 30 minutes.
 *
 * @param vmId The vSphere managed object reference ID
 * @return WebMksTicket on success, VsphereError on failure
 * @throws VsphereError.InvalidState if VM is not powered on
 */
public suspend fun acquireWebMksTicket(vmId: String): Result<WebMksTicket, VsphereError> =
    executeResilient("acquireWebMksTicket") {
        val tenantId = try {
            TenantContext.current()
        } catch (e: Exception) {
            return@executeResilient VsphereError.ConnectionError("No tenant context", e).failure()
        }

        val sessionResult = ensureSession(tenantId)
        val session = when (sessionResult) {
            is Result.Success -> sessionResult.value
            is Result.Failure -> return@executeResilient sessionResult.error.failure()
        }

        withContext(Dispatchers.IO) {
            try {
                val vmRef = moRef("VirtualMachine", vmId)

                // Verify VM is powered on (required for webmks ticket)
                val powerState = getProperty(session, vmRef, "runtime.powerState")
                    ?.toString()
                    ?.let { com.vmware.vim25.VirtualMachinePowerState.fromValue(it) }

                if (powerState != com.vmware.vim25.VirtualMachinePowerState.POWERED_ON) {
                    return@withContext VsphereError.InvalidState(
                        "VM must be powered on for console access (current: $powerState)"
                    ).failure()
                }

                // Acquire the WebMKS ticket
                val ticket = session.vimPort.acquireTicket(vmRef, "webmks")

                WebMksTicket(
                    ticket = ticket.ticket,
                    host = ticket.host ?: throw RuntimeException("No host in ticket"),
                    port = ticket.port ?: 443,
                    sslThumbprint = ticket.sslThumbprint,
                ).success()

            } catch (e: com.vmware.vim25.InvalidStateFaultMsg) {
                VsphereError.InvalidState("VM is not in valid state for console access").failure()
            } catch (e: com.vmware.vim25.NoPermissionFaultMsg) {
                VsphereError.PermissionDenied("Insufficient privileges for console access").failure()
            } catch (e: Exception) {
                logger.error(e) { "Failed to acquire WebMKS ticket for VM $vmId" }
                VsphereError.ApiError("Failed to acquire WebMKS ticket", e).failure()
            }
        }
    }
```

```kotlin
// dvmm-application/src/main/kotlin/de/acci/dvmm/application/vmware/ConnectionTypes.kt
// Add:

/**
 * WebMKS console ticket returned by vCenter.
 *
 * Used to establish browser-to-ESXi WebSocket connection for VM console.
 */
public data class WebMksTicket(
    /** One-time authentication token (cst-xxx format) */
    val ticket: String,
    /** ESXi host IP or FQDN */
    val host: String,
    /** WebSocket port (typically 443) */
    val port: Int,
    /** ESXi SSL certificate thumbprint for verification */
    val sslThumbprint: String?,
) {
    /** Constructs the WebSocket URL for connection */
    public fun toWebSocketUrl(): String = "wss://$host:$port/ticket/$ticket"
}
```

---

## 8. Testing Strategy

### 8.1 Testing Limitations

**Critical Limitation:** VCSIM does not implement WebMKS/console functionality. The `acquireTicket` method is not simulated.

| Test Type | VCSIM Support | Real vCenter Required |
|-----------|---------------|----------------------|
| Unit tests | N/A (mock) | No |
| Integration tests | No | Yes |
| E2E tests | No | Yes |

### 8.2 Unit Testing Approach

Mock the vSphere API at the handler level:

```kotlin
// RequestConsoleTicketHandlerTest.kt

class RequestConsoleTicketHandlerTest {

    private val vmProjectionRepository = mockk<VmProjectionRepository>()
    private val vspherePort = mockk<VspherePort>()
    private val authorizationService = mockk<AuthorizationService>()
    private val auditLogger = mockk<AuditLogger>(relaxed = true)

    private val handler = RequestConsoleTicketHandler(
        vmProjectionRepository = vmProjectionRepository,
        vspherePort = vspherePort,
        authorizationService = authorizationService,
        auditLogger = auditLogger,
    )

    @Test
    fun `returns ticket when user owns VM`() = runTest {
        // Given
        val tenantId = TenantId.random()
        val userId = UserId.random()
        val vmId = UUID.randomUUID()
        val vsphereVmId = "vm-123"

        val vm = mockk<VmProjection> {
            every { this@mockk.tenantId } returns tenantId
            every { requesterId } returns userId
            every { this@mockk.vsphereVmId } returns vsphereVmId
            every { powerState } returns "poweredOn"
        }

        coEvery { vmProjectionRepository.findById(vmId) } returns vm
        coEvery { vspherePort.acquireWebMksTicket(vsphereVmId) } returns WebMksTicket(
            ticket = "cst-test-ticket",
            host = "esxi01.example.com",
            port = 443,
            sslThumbprint = "AA:BB:CC:DD",
        ).success()

        // When
        val result = handler.handle(RequestConsoleTicketCommand(
            vmId = vmId,
            userId = userId,
            tenantId = tenantId,
        ))

        // Then
        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).value.ticket).isEqualTo("cst-test-ticket")
    }

    @Test
    fun `denies access for VM in different tenant`() = runTest {
        // Given
        val userTenantId = TenantId.random()
        val vmTenantId = TenantId.random() // Different tenant
        val userId = UserId.random()
        val vmId = UUID.randomUUID()

        val vm = mockk<VmProjection> {
            every { tenantId } returns vmTenantId
        }

        coEvery { vmProjectionRepository.findById(vmId) } returns vm

        // When
        val result = handler.handle(RequestConsoleTicketCommand(
            vmId = vmId,
            userId = userId,
            tenantId = userTenantId,
        ))

        // Then
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isEqualTo(ConsoleTicketError.VmNotFound)
    }

    @Test
    fun `denies access when VM not powered on`() = runTest {
        // Given
        val tenantId = TenantId.random()
        val userId = UserId.random()
        val vmId = UUID.randomUUID()

        val vm = mockk<VmProjection> {
            every { this@mockk.tenantId } returns tenantId
            every { requesterId } returns userId
            every { powerState } returns "poweredOff"
        }

        coEvery { vmProjectionRepository.findById(vmId) } returns vm

        // When
        val result = handler.handle(RequestConsoleTicketCommand(
            vmId = vmId,
            userId = userId,
            tenantId = tenantId,
        ))

        // Then
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isEqualTo(ConsoleTicketError.VmNotPoweredOn)
    }
}
```

### 8.3 Manual Integration Testing

For testing with real vCenter:

```kotlin
// VsphereClientWebMksIntegrationTest.kt (manual/on-demand)

@Disabled("Requires real vCenter - run manually")
@IntegrationTest
class VsphereClientWebMksIntegrationTest {

    @Value("\${test.vcenter.url}")
    private lateinit var vcenterUrl: String

    @Value("\${test.vcenter.username}")
    private lateinit var username: String

    @Value("\${test.vcenter.password}")
    private lateinit var password: String

    @Value("\${test.vcenter.vm-id}")
    private lateinit var testVmId: String

    @Autowired
    private lateinit var vsphereClient: VsphereClient

    @Test
    fun `acquireWebMksTicket returns valid ticket for powered-on VM`() = runTest {
        // Given: VM is powered on (pre-condition)

        // When
        val result = vsphereClient.acquireWebMksTicket(testVmId)

        // Then
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val ticket = (result as Result.Success).value

        assertThat(ticket.ticket).startsWith("cst-")
        assertThat(ticket.host).isNotBlank()
        assertThat(ticket.port).isEqualTo(443)

        // Verify WebSocket URL is valid
        val wsUrl = ticket.toWebSocketUrl()
        assertThat(wsUrl).startsWith("wss://")
        assertThat(wsUrl).contains("/ticket/cst-")
    }
}
```

### 8.4 Frontend Testing

```typescript
// WebMksConsole.test.tsx

import { render, screen, waitFor } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { WebMksConsole } from './WebMksConsole';

// Mock WMKS SDK
const mockConnect = vi.fn();
const mockDestroy = vi.fn();
const mockRegister = vi.fn();

const mockWMKS = {
  createWMKS: vi.fn(() => ({
    connect: mockConnect,
    destroy: mockDestroy,
    register: mockRegister,
  })),
  CONST: {
    Events: {
      CONNECTION_STATE_CHANGE: 'connectionStateChange',
      ERROR: 'error',
    },
    ConnectionState: {
      CONNECTED: 'connected',
      DISCONNECTED: 'disconnected',
    },
  },
};

vi.mock('../lib/wmks-loader', () => ({
  loadWmksSdk: vi.fn(() => Promise.resolve(mockWMKS)),
}));

describe('WebMksConsole', () => {
  const defaultProps = {
    websocketUrl: 'wss://esxi.example.com:443/ticket/cst-test',
    onClose: vi.fn(),
    onError: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads SDK and connects to websocket', async () => {
    render(<WebMksConsole {...defaultProps} />);

    // Should show loading initially
    expect(screen.getByText('Loading console SDK...')).toBeInTheDocument();

    await waitFor(() => {
      expect(mockWMKS.createWMKS).toHaveBeenCalledWith('wmks-container', expect.any(Object));
    });

    expect(mockConnect).toHaveBeenCalledWith(defaultProps.websocketUrl);
  });

  it('calls onError when connection fails', async () => {
    render(<WebMksConsole {...defaultProps} />);

    await waitFor(() => {
      expect(mockRegister).toHaveBeenCalled();
    });

    // Simulate error event
    const errorHandler = mockRegister.mock.calls.find(
      call => call[0] === mockWMKS.CONST.Events.ERROR
    )?.[1];

    errorHandler?.({}, { message: 'Connection refused' });

    expect(defaultProps.onError).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'Connection refused' })
    );
  });

  it('destroys WMKS instance on unmount', async () => {
    const { unmount } = render(<WebMksConsole {...defaultProps} />);

    await waitFor(() => {
      expect(mockWMKS.createWMKS).toHaveBeenCalled();
    });

    unmount();

    expect(mockDestroy).toHaveBeenCalled();
  });
});
```

---

## 9. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| **VCSIM doesn't support WebMKS** | Certain | High | Manual testing with real vCenter; extensive unit test mocking |
| **ESXi SSL certificate issues** | High | Medium | Document certificate setup; consider backend proxy |
| **Browser-ESXi network blocked** | Medium | High | Implement Option B (WebSocket proxy) as fallback |
| **VMware SDK API changes** | Low | Medium | Pin SDK version; monitor release notes |
| **WebMKS ticket expiry (30 min)** | Low | Low | UI guidance; re-acquire on expiry |
| **Performance with many consoles** | Medium | Medium | Client-side limit; backend resource protection |
| **Security: ticket interception** | Low | High | TLS everywhere; short ticket validity |

---

## 10. References

### VMware Documentation

- [HTML Console SDK Programming Guide](https://developer.vmware.com/docs/9197/html-console-sdk-programming-guide/)
- [Architecture of the HTML Console](https://docs.vmware.com/en/VMware-vSphere/8.0/html-console-sdk-programming-guide/GUID-635B3A9F-C62E-4A97-9FAB-FBCAA3914FDA.html)
- [Console Tickets API](https://vmware.github.io/vsphere-automation-sdk-java/vsphere/8.0.1.0/vcenter-bindings/com/vmware/vcenter/vm/console/Tickets.html)
- [VCF SDK 9.0 for Java](https://github.com/vmware/vcf-sdk-java)

### Apache Guacamole

- [GUACAMOLE-1641: Add vSphere support](https://issues.apache.org/jira/browse/GUACAMOLE-1641)
- [Custom Protocol Development](https://guacamole.apache.org/doc/gug/custom-protocols.html)
- [Implementation Architecture](https://guacamole.apache.org/doc/gug/guacamole-architecture.html)

### Community Resources

- [rgerganov/mks - VMware Console Client](https://github.com/rgerganov/mks)
- [OpenStack Nova WebMKS Spec](https://github.com/openstack/nova-specs/blob/master/specs/liberty/implemented/vmware-webmks-console.rst)
- [govmomi AcquireTicket Issue](https://github.com/vmware/govmomi/issues/2083)
- [py-wsvnc - Python WebSocket VNC](https://github.com/Cynnovative/py-wsvnc)

### Security References

- [WebSocket SSL Certificate Handling](https://stackoverflow.com/questions/5312311/secure-websockets-with-self-signed-certificate)
- [DVMM Security Architecture](security-architecture.md)

### Related DVMM Documentation

- [Guacamole Integration Research](research-guacamole-integration.md)
- [Architecture](architecture.md)
- [Epics - Story 3.7](epics.md#story-37-provisioned-vm-details)

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-12-07 | Claude (Research Agent) | Initial comprehensive research |

---

## Appendix A: Network Topology Considerations

### A.1 Direct Browser-to-ESXi (Option A)

```text
┌─────────────────────────────────────────────────────────────────┐
│  Corporate Network                                               │
│                                                                  │
│  ┌──────────┐      ┌──────────┐      ┌──────────────────────┐  │
│  │  User    │──────│ Firewall │──────│   vSphere Cluster    │  │
│  │ Browser  │      │          │      │  ┌──────┐  ┌──────┐  │  │
│  └──────────┘      └──────────┘      │  │ESXi-1│  │ESXi-2│  │  │
│                         │            │  └──┬───┘  └──┬───┘  │  │
│                         │            │     │         │      │  │
│                         │            │  ┌──┴─────────┴──┐   │  │
│                         │            │  │   vCenter     │   │  │
│                         │            │  └───────────────┘   │  │
│                         │            └──────────────────────┘  │
│                         │                                       │
│  Required Firewall Rules:                                       │
│  • Browser → vCenter:443 (REST API)                            │
│  • Browser → ESXi-*:443 (WebSocket)  ← CRITICAL                │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### A.2 Backend Proxy (Option B)

```text
┌─────────────────────────────────────────────────────────────────┐
│  Corporate Network                                               │
│                                                                  │
│  ┌──────────┐      ┌──────────┐      ┌──────────────────────┐  │
│  │  User    │──────│   DVMM   │──────│   vSphere Cluster    │  │
│  │ Browser  │      │ Backend  │      │  ┌──────┐  ┌──────┐  │  │
│  └──────────┘      └──────────┘      │  │ESXi-1│  │ESXi-2│  │  │
│       │                  │           │  └──┬───┘  └──┬───┘  │  │
│       │                  │           │     │         │      │  │
│       │                  │           │  ┌──┴─────────┴──┐   │  │
│       │                  │           │  │   vCenter     │   │  │
│       │                  │           │  └───────────────┘   │  │
│       │                  │           └──────────────────────┘  │
│       │                  │                                      │
│  Required Firewall Rules:                                       │
│  • Browser → DVMM:443 (HTTPS + WebSocket)                      │
│  • DVMM → vCenter:443 (REST API)                               │
│  • DVMM → ESXi-*:443 (WebSocket)  ← Backend only               │
│                                                                  │
│  Browser does NOT need direct ESXi access                       │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Appendix B: Spring WebFlux WebSocket Proxy Implementation

If Option B is needed, here's the WebSocket proxy implementation:

```kotlin
// dvmm-api/src/main/kotlin/de/acci/dvmm/api/console/WebMksProxyHandler.kt

package de.acci.dvmm.api.console

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import java.net.URI

@Component
public class WebMksProxyHandler(
    private val sessionRegistry: ConsoleSessionRegistry,
) : WebSocketHandler {

    private val logger = KotlinLogging.logger {}

    override fun handle(session: WebSocketSession): Mono<Void> {
        // Extract session ID from query params
        val sessionId = session.handshakeInfo.uri.query
            ?.split("&")
            ?.associate {
                val (k, v) = it.split("=")
                k to v
            }
            ?.get("sessionId")
            ?: return session.close()

        // Get the target WebSocket URL from registry
        val consoleSession = sessionRegistry.get(sessionId)
            ?: return session.close()

        val targetUrl = consoleSession.webSocketUrl

        // Create client with SSL handling for self-signed ESXi certs
        val httpClient = HttpClient.create()
            .secure { spec ->
                spec.sslContext(
                    SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build()
                )
            }

        val client = ReactorNettyWebSocketClient(httpClient)

        return client.execute(URI.create(targetUrl)) { upstreamSession ->
            // Relay messages bidirectionally
            val inbound = upstreamSession.receive()
                .map { msg ->
                    when (msg.type) {
                        WebSocketMessage.Type.BINARY -> session.binaryMessage { it.wrap(msg.payload.asByteBuffer()) }
                        WebSocketMessage.Type.TEXT -> session.textMessage(msg.payloadAsText)
                        else -> null
                    }
                }
                .filterNotNull()
                .let { session.send(it) }

            val outbound = session.receive()
                .map { msg ->
                    when (msg.type) {
                        WebSocketMessage.Type.BINARY -> upstreamSession.binaryMessage { it.wrap(msg.payload.asByteBuffer()) }
                        WebSocketMessage.Type.TEXT -> upstreamSession.textMessage(msg.payloadAsText)
                        else -> null
                    }
                }
                .filterNotNull()
                .let { upstreamSession.send(it) }

            Mono.zip(inbound, outbound).then()
        }
    }
}
```

```kotlin
// WebSocket configuration
@Configuration
public class WebSocketConfig {

    @Bean
    public fun webSocketHandlerMapping(proxyHandler: WebMksProxyHandler): HandlerMapping {
        val mapping = SimpleUrlHandlerMapping()
        mapping.urlMap = mapOf("/api/console/proxy" to proxyHandler)
        mapping.order = -1
        return mapping
    }

    @Bean
    public fun webSocketHandlerAdapter(): WebSocketHandlerAdapter {
        return WebSocketHandlerAdapter()
    }
}
```

---

*This document provides comprehensive guidance for integrating VMware WebMKS console access into DVMM. The recommended approach (Option A: Direct VMware SDK) provides the best balance of development effort, performance, and official support.*
