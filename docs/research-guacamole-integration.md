# Apache Guacamole Integration for DVMM

**Author:** Claude (Research Agent)
**Date:** 2025-11-28
**Version:** 1.0
**Status:** Research Complete
**Phase:** Pre-Implementation Research

---

## Executive Summary

This document presents a comprehensive technical research on integrating **Apache Guacamole** into DVMM to provide browser-based terminal/console access to provisioned virtual machines. The integration enables users to access VMs directly from the DVMM web interface without requiring local SSH clients or VPN connections.

**Key Findings:**
- Apache Guacamole provides a mature, well-documented solution for browser-based remote access
- The **embedded tunnel approach** best aligns with DVMM's architecture and security model
- Integration can leverage existing Keycloak authentication and PostgreSQL RLS multi-tenancy
- End-to-end testing is achievable using vcsim's containers-as-VMs feature

**Recommendation:** Proceed with embedded Guacamole tunnel integration, starting with SSH protocol support for MVP, with potential VMware native console (WebMKS) support in future phases.

---

## Table of Contents

1. [DVMM Context](#1-dvmm-context)
2. [Apache Guacamole Overview](#2-apache-guacamole-overview)
3. [Integration Architecture](#3-integration-architecture)
4. [Technical Implementation](#4-technical-implementation)
5. [Security Design](#5-security-design)
6. [Frontend Integration](#6-frontend-integration)
7. [Testing Strategy](#7-testing-strategy)
8. [Deployment Architecture](#8-deployment-architecture)
9. [Implementation Roadmap](#9-implementation-roadmap)
10. [Risk Assessment](#10-risk-assessment)
11. [References](#11-references)

---

## 1. DVMM Context

### 1.1 Product Overview

**DVMM (Dynamic Virtual Machine Manager)** is a multi-tenant self-service portal for VMware VM provisioning with workflow-based approval automation. The core value proposition:

> "Request a VM. Get approval. VM is provisioned. That's it."

### 1.2 Current VM Lifecycle

```text
User Request → Admin Approval → VMware Provisioning → VM Running → User Notified
                                                            │
                                                            ▼
                                                    ┌──────────────┐
                                                    │ User sees:   │
                                                    │ - IP Address │
                                                    │ - Credentials│
                                                    │ - Status     │
                                                    └──────────────┘
```

**Gap:** After provisioning, users must use external tools (SSH clients, RDP) to access their VMs. This creates friction and requires additional network configuration (VPN, firewall rules).

### 1.3 Proposed Enhancement

Add browser-based console access directly within DVMM:

```text
User Request → Admin Approval → VMware Provisioning → VM Running → User Notified
                                                            │
                                                            ▼
                                                    ┌──────────────┐
                                                    │ User sees:   │
                                                    │ - IP Address │
                                                    │ - Credentials│
                                                    │ - Status     │
                                                    │ - [Open Console] ◄── NEW
                                                    └──────────────┘
```

### 1.4 Architectural Constraints

Any integration must comply with DVMM's architectural principles:

| Constraint | Requirement |
|------------|-------------|
| **Multi-tenancy** | PostgreSQL RLS enforcement, tenant isolation |
| **Authentication** | Keycloak OIDC, JWT tokens |
| **Hexagonal Architecture** | Guacamole as infrastructure adapter |
| **Domain Purity** | No Spring dependencies in `dvmm-domain` |
| **Quality Gates** | 80% test coverage, 70% mutation score |
| **Technology Stack** | Kotlin 2.2, Spring Boot 3.5 WebFlux |

---

## 2. Apache Guacamole Overview

### 2.1 What is Apache Guacamole?

[Apache Guacamole](https://guacamole.apache.org/) is a clientless remote desktop gateway supporting VNC, RDP, and SSH protocols. Users access remote desktops through a web browser without plugins or client software.

### 2.2 Core Architecture

```text
┌─────────────┐    HTTP/WS     ┌─────────────────┐     TCP      ┌──────────┐
│   Browser   │ ──────────────▶│  Web Application │ ────────────▶│  guacd   │
│  (JS Client)│                │  (Java Servlet)  │   (4822)     │ (daemon) │
└─────────────┘                └─────────────────┘               └────┬─────┘
                                                                      │
                                                    VNC/RDP/SSH       │
                                                    ───────────────────┘
                                                           ▼
                                                    ┌──────────────┐
                                                    │  Target VM   │
                                                    └──────────────┘
```

**Components:**

| Component | Technology | Purpose |
|-----------|------------|---------|
| **guacd** | C daemon | Protocol translator (SSH/VNC/RDP → Guacamole protocol) |
| **Web Application** | Java Servlet | HTTP/WebSocket tunneling, authentication |
| **JavaScript Client** | guacamole-common-js | Display rendering, input capture |

### 2.3 The Guacamole Protocol

The Guacamole protocol is a remote display protocol that:
- Transmits drawing instructions (not pixels)
- Handles keyboard/mouse input
- Is protocol-agnostic (same protocol for SSH, VNC, RDP)
- Works over HTTP or WebSocket

### 2.4 Available APIs

Guacamole provides three APIs for integration:

| API | Language | Purpose | Maven Artifact |
|-----|----------|---------|----------------|
| **guacamole-common** | Java | Servlet tunnel, protocol handling | `org.apache.guacamole:guacamole-common` |
| **guacamole-common-js** | JavaScript | Client rendering, tunnels | NPM or CDN |
| **guacamole-ext** | Java | Extension API for custom auth | `org.apache.guacamole:guacamole-ext` |

### 2.5 Supported Protocols

| Protocol | Use Case | DVMM Relevance |
|----------|----------|----------------|
| **SSH** | Linux terminal access | Primary for MVP |
| **RDP** | Windows desktop | Future enhancement |
| **VNC** | Generic remote desktop | Backup option |
| **Kubernetes** | Pod exec | Out of scope |

---

## 3. Integration Architecture

### 3.1 Architecture Options Evaluated

#### Option A: Embedded Tunnel (Recommended)

Embed Guacamole's Java API directly into DVMM's Spring Boot application.

```text
┌─────────────────────────────────────────────────────────────────────┐
│                         DVMM Application                            │
├─────────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐   ┌────────────────┐   ┌────────────────────────┐ │
│  │  React UI    │   │  DVMM API      │   │  Guacamole Tunnel     │ │
│  │  + guac-js   │──▶│  (WebFlux)     │──▶│  (WebSocket Endpoint) │ │
│  └──────────────┘   └────────────────┘   └──────────┬─────────────┘ │
│                                                      │               │
│  ┌──────────────────────────────────────────────────┼───────────────┤
│  │              dvmm-infrastructure                  │               │
│  │  ┌────────────────┐    ┌─────────────────────────▼──────────┐   │
│  │  │ VMware Adapter │    │ GuacamoleConnectionFactory         │   │
│  │  │ (provision)    │    │ - Resolves VM IP/credentials       │   │
│  │  │                │    │ - Enforces tenant isolation        │   │
│  │  └────────────────┘    └────────────────────────────────────┘   │
│  └──────────────────────────────────────────────────────────────────┤
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ TCP (4822)
                            ┌───────────────┐
                            │    guacd      │ (Docker sidecar)
                            └───────┬───────┘
                                    │ SSH
                                    ▼
                            ┌───────────────┐
                            │   Target VM   │
                            └───────────────┘
```

**Advantages:**
- Single authentication layer (reuse Keycloak JWT)
- Full control over tenant isolation
- No separate Guacamole deployment
- Follows hexagonal architecture (Guacamole as infrastructure adapter)

**Disadvantages:**
- More code to maintain
- Must handle Servlet/WebFlux integration

#### Option B: Standalone Guacamole with External Auth

Deploy Guacamole as a separate service with HTTP header authentication.

**Disadvantages:**
- Separate deployment complexity
- Authentication synchronization challenges
- Harder to enforce tenant isolation at DB level

#### Option C: Proxy with Encrypted JSON

Use Guacamole's encrypted JSON extension to pass connection details.

**Disadvantages:**
- Key management complexity
- Connection details visible in URL (even if encrypted)
- Additional latency for token generation

### 3.2 Recommendation: Option A (Embedded Tunnel)

Option A is recommended because:

1. **Multi-tenancy**: RLS enforcement via existing `TenantContext`
2. **Security**: Single auth layer, JWT token reuse
3. **Architecture**: Clean hexagonal design (Guacamole = infrastructure adapter)
4. **Operational**: No separate service to manage
5. **Consistency**: Same patterns as other DVMM infrastructure adapters

### 3.3 Module Structure

```text
dvmm/
├── dvmm-domain/
│   └── src/main/kotlin/de/acci/dvmm/domain/
│       └── console/
│           ├── ConsoleSession.kt           # Domain model
│           ├── ConsoleAccessPolicy.kt      # Access rules
│           └── ConsoleSessionRequested.kt  # Domain event
│
├── dvmm-application/
│   └── src/main/kotlin/de/acci/dvmm/application/
│       └── console/
│           ├── RequestConsoleAccessCommand.kt
│           ├── ConsoleAccessCommandHandler.kt
│           └── ConsoleAccessPort.kt        # Port interface
│
├── dvmm-api/
│   └── src/main/kotlin/de/acci/dvmm/api/
│       └── console/
│           ├── ConsoleController.kt        # REST endpoint
│           └── GuacamoleTunnelEndpoint.kt  # WebSocket tunnel
│
└── dvmm-infrastructure/
    └── src/main/kotlin/de/acci/dvmm/infrastructure/
        └── console/
            ├── GuacamoleConnectionFactory.kt   # Creates configs
            ├── SshConsoleAdapter.kt            # SSH implementation
            ├── GuacamoleConsoleAdapter.kt      # Port implementation
            └── VmCredentialResolver.kt         # Credential retrieval
```

---

## 4. Technical Implementation

### 4.1 Dependencies

Add to `gradle/libs.versions.toml`:

```toml
[versions]
guacamole = "1.6.0"

[libraries]
guacamole-common = { module = "org.apache.guacamole:guacamole-common", version.ref = "guacamole" }
```

Add to `dvmm/dvmm-infrastructure/build.gradle.kts`:

```kotlin
dependencies {
    implementation(libs.guacamole.common)
}
```

Add to frontend `package.json`:

```json
{
  "dependencies": {
    "guacamole-common-js": "^1.6.0"
  }
}
```

### 4.2 Domain Model

```kotlin
// dvmm-domain/src/main/kotlin/de/acci/dvmm/domain/console/ConsoleSession.kt

package de.acci.dvmm.domain.console

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import java.time.Instant
import java.util.UUID

/**
 * Represents an active console session to a VM.
 * Immutable value object tracking session metadata.
 */
public data class ConsoleSession(
    val id: ConsoleSessionId,
    val vmId: VmId,
    val userId: UserId,
    val tenantId: TenantId,
    val protocol: ConsoleProtocol,
    val createdAt: Instant,
    val expiresAt: Instant,
) {
    public fun isExpired(now: Instant = Instant.now()): Boolean =
        now.isAfter(expiresAt)
}

@JvmInline
public value class ConsoleSessionId(public val value: UUID) {
    public companion object {
        public fun generate(): ConsoleSessionId = ConsoleSessionId(UUID.randomUUID())
    }
}

public enum class ConsoleProtocol {
    SSH,
    VNC,
    RDP
}
```

### 4.3 Application Layer

```kotlin
// dvmm-application/src/main/kotlin/de/acci/dvmm/application/console/RequestConsoleAccessCommand.kt

package de.acci.dvmm.application.console

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId

public data class RequestConsoleAccessCommand(
    val vmId: VmId,
    val userId: UserId,
    val tenantId: TenantId,
    val protocol: ConsoleProtocol = ConsoleProtocol.SSH,
)
```

```kotlin
// dvmm-application/src/main/kotlin/de/acci/dvmm/application/console/ConsoleAccessCommandHandler.kt

package de.acci.dvmm.application.console

import de.acci.dvmm.domain.console.ConsoleSession
import de.acci.dvmm.domain.console.ConsoleSessionId
import de.acci.dvmm.domain.vm.VmRepository
import java.time.Clock
import java.time.Duration

public class ConsoleAccessCommandHandler(
    private val vmRepository: VmRepository,
    private val consoleAccessPort: ConsoleAccessPort,
    private val authorizationService: AuthorizationService,
    private val clock: Clock,
) {
    public suspend fun handle(command: RequestConsoleAccessCommand): ConsoleSession {
        // 1. Verify VM exists and belongs to tenant (RLS enforced)
        val vm = vmRepository.findById(command.vmId)
            ?: throw VmNotFoundException(command.vmId)

        // 2. Verify VM is powered on
        if (vm.powerState != PowerState.POWERED_ON) {
            throw VmNotRunningException(command.vmId, vm.powerState)
        }

        // 3. Verify user has access (owner or admin)
        if (!canAccessConsole(command.userId, vm)) {
            throw ConsoleAccessDeniedException(command.vmId, command.userId)
        }

        // 4. Create session
        val now = clock.instant()
        val session = ConsoleSession(
            id = ConsoleSessionId.generate(),
            vmId = command.vmId,
            userId = command.userId,
            tenantId = command.tenantId,
            protocol = command.protocol,
            createdAt = now,
            expiresAt = now.plus(SESSION_DURATION),
        )

        // 5. Register session for tunnel access
        consoleAccessPort.registerSession(session)

        return session
    }

    private suspend fun canAccessConsole(userId: UserId, vm: Vm): Boolean {
        return vm.ownerId == userId || authorizationService.hasRole(userId, Role.ADMIN)
    }

    private companion object {
        val SESSION_DURATION: Duration = Duration.ofMinutes(5)
    }
}
```

### 4.4 Infrastructure Layer - Connection Factory

```kotlin
// dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/console/GuacamoleConnectionFactory.kt

package de.acci.dvmm.infrastructure.console

import de.acci.dvmm.application.console.ConsoleAccessPort
import de.acci.dvmm.domain.console.ConsoleProtocol
import de.acci.dvmm.domain.console.ConsoleSession
import de.acci.dvmm.infrastructure.vault.CredentialVault
import de.acci.eaf.tenant.TenantContext
import de.acci.eaf.tenant.TenantContextElement
import kotlinx.coroutines.withContext
import org.apache.guacamole.net.GuacamoleTunnel
import org.apache.guacamole.net.InetGuacamoleSocket
import org.apache.guacamole.net.SimpleGuacamoleTunnel
import org.apache.guacamole.protocol.ConfiguredGuacamoleSocket
import org.apache.guacamole.protocol.GuacamoleConfiguration
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
public class GuacamoleConnectionFactory(
    private val vmProjectionRepository: VmProjectionRepository,
    private val credentialVault: CredentialVault,
    private val guacdProperties: GuacdProperties,
) : ConsoleAccessPort {

    private val activeSessions = ConcurrentHashMap<ConsoleSessionId, ConsoleSession>()

    override suspend fun registerSession(session: ConsoleSession) {
        activeSessions[session.id] = session
    }

    override suspend fun revokeSession(sessionId: ConsoleSessionId) {
        activeSessions.remove(sessionId)
    }

    public suspend fun createTunnel(sessionId: ConsoleSessionId): GuacamoleTunnel {
        val session = activeSessions[sessionId]
            ?: throw InvalidSessionException(sessionId)

        if (session.isExpired()) {
            activeSessions.remove(sessionId)
            throw SessionExpiredException(sessionId)
        }

        // Enforce tenant context
        return withContext(TenantContextElement(session.tenantId)) {
            val config = buildConfiguration(session)
            val socket = ConfiguredGuacamoleSocket(
                InetGuacamoleSocket(guacdProperties.host, guacdProperties.port),
                config
            )
            SimpleGuacamoleTunnel(socket)
        }
    }

    @VisibleForTesting
    internal suspend fun buildConfiguration(session: ConsoleSession): GuacamoleConfiguration {
        // RLS ensures we only see VMs for this tenant
        val vm = vmProjectionRepository.findById(session.vmId)
            ?: throw VmNotFoundException(session.vmId)

        val credentials = credentialVault.getSshCredentials(session.vmId)

        return GuacamoleConfiguration().apply {
            protocol = session.protocol.toGuacamoleProtocol()

            when (session.protocol) {
                ConsoleProtocol.SSH -> configureSsh(vm, credentials)
                ConsoleProtocol.VNC -> configureVnc(vm, credentials)
                ConsoleProtocol.RDP -> configureRdp(vm, credentials)
            }
        }
    }

    private fun GuacamoleConfiguration.configureSsh(
        vm: VmProjection,
        credentials: SshCredentials,
    ) {
        setParameter("hostname", vm.ipAddress)
        setParameter("port", credentials.port.toString())
        setParameter("username", credentials.username)
        setParameter("password", credentials.password)
        setParameter("font-size", "12")
        setParameter("color-scheme", "gray-black")
        setParameter("terminal-type", "xterm-256color")
        setParameter("scrollback", "1000")
        setParameter("disable-copy", "false")
        setParameter("disable-paste", "false")
    }

    private fun GuacamoleConfiguration.configureVnc(
        vm: VmProjection,
        credentials: VncCredentials,
    ) {
        setParameter("hostname", vm.ipAddress)
        setParameter("port", credentials.port.toString())
        setParameter("password", credentials.password)
    }

    private fun GuacamoleConfiguration.configureRdp(
        vm: VmProjection,
        credentials: RdpCredentials,
    ) {
        setParameter("hostname", vm.ipAddress)
        setParameter("port", credentials.port.toString())
        setParameter("username", credentials.username)
        setParameter("password", credentials.password)
        setParameter("domain", credentials.domain)
        setParameter("security", "nla")
        setParameter("ignore-cert", "true")
    }
}

private fun ConsoleProtocol.toGuacamoleProtocol(): String = when (this) {
    ConsoleProtocol.SSH -> "ssh"
    ConsoleProtocol.VNC -> "vnc"
    ConsoleProtocol.RDP -> "rdp"
}
```

### 4.5 API Layer - WebSocket Tunnel Endpoint

```kotlin
// dvmm-api/src/main/kotlin/de/acci/dvmm/api/console/GuacamoleTunnelEndpoint.kt

package de.acci.dvmm.api.console

import de.acci.dvmm.domain.console.ConsoleSessionId
import de.acci.dvmm.infrastructure.console.GuacamoleConnectionFactory
import de.acci.eaf.auth.JwtValidator
import jakarta.websocket.CloseReason
import jakarta.websocket.EndpointConfig
import jakarta.websocket.OnClose
import jakarta.websocket.OnError
import jakarta.websocket.OnMessage
import jakarta.websocket.OnOpen
import jakarta.websocket.Session
import jakarta.websocket.server.ServerEndpoint
import kotlinx.coroutines.runBlocking
import org.apache.guacamole.GuacamoleClientException
import org.apache.guacamole.GuacamoleException
import org.apache.guacamole.io.GuacamoleReader
import org.apache.guacamole.io.GuacamoleWriter
import org.apache.guacamole.net.GuacamoleTunnel
import org.slf4j.LoggerFactory
import java.util.UUID

@ServerEndpoint("/api/console/tunnel")
public class GuacamoleTunnelEndpoint(
    private val connectionFactory: GuacamoleConnectionFactory,
    private val jwtValidator: JwtValidator,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private var tunnel: GuacamoleTunnel? = null
    private var readerThread: Thread? = null

    @OnOpen
    public fun onOpen(session: Session, config: EndpointConfig) {
        try {
            // 1. Extract and validate JWT from query parameter
            val token = session.requestParameterMap["token"]?.firstOrNull()
                ?: throw GuacamoleClientException("Missing authentication token")

            val claims = jwtValidator.validate(token)

            // 2. Extract session ID
            val sessionIdParam = session.requestParameterMap["sessionId"]?.firstOrNull()
                ?: throw GuacamoleClientException("Missing session ID")

            val sessionId = ConsoleSessionId(UUID.fromString(sessionIdParam))

            // 3. Create tunnel (coroutine bridge)
            tunnel = runBlocking {
                connectionFactory.createTunnel(sessionId)
            }

            // 4. Start reader thread for guacd → browser
            readerThread = Thread({
                val reader: GuacamoleReader = tunnel!!.acquireReader()
                try {
                    while (true) {
                        val instruction = reader.read() ?: break
                        session.basicRemote.sendText(instruction.toString())
                    }
                } catch (e: Exception) {
                    logger.debug("Reader thread terminated: ${e.message}")
                } finally {
                    tunnel?.releaseReader()
                    closeSession(session, CloseReason.CloseCodes.NORMAL_CLOSURE, "Tunnel closed")
                }
            }, "guac-reader-${session.id}")

            readerThread?.start()

            logger.info("Console tunnel opened for session $sessionId")

        } catch (e: GuacamoleException) {
            logger.warn("Failed to open tunnel: ${e.message}")
            closeSession(session, CloseReason.CloseCodes.CANNOT_ACCEPT, e.message ?: "Unknown error")
        }
    }

    @OnMessage
    public fun onMessage(message: String, session: Session) {
        try {
            val writer: GuacamoleWriter = tunnel?.acquireWriter()
                ?: throw GuacamoleException("No tunnel available")

            writer.write(message.toCharArray())
            tunnel?.releaseWriter()

        } catch (e: GuacamoleException) {
            logger.warn("Failed to send message: ${e.message}")
            closeSession(session, CloseReason.CloseCodes.UNEXPECTED_CONDITION, e.message)
        }
    }

    @OnClose
    public fun onClose(session: Session, reason: CloseReason) {
        logger.info("Console tunnel closed: ${reason.reasonPhrase}")
        cleanup()
    }

    @OnError
    public fun onError(session: Session, error: Throwable) {
        logger.error("Console tunnel error", error)
        cleanup()
    }

    private fun cleanup() {
        tunnel?.close()
        tunnel = null
        readerThread?.interrupt()
        readerThread = null
    }

    private fun closeSession(session: Session, code: CloseReason.CloseCodes, reason: String?) {
        try {
            session.close(CloseReason(code, reason ?: ""))
        } catch (e: Exception) {
            logger.debug("Error closing session: ${e.message}")
        }
    }
}
```

### 4.6 API Layer - REST Controller

```kotlin
// dvmm-api/src/main/kotlin/de/acci/dvmm/api/console/ConsoleController.kt

package de.acci.dvmm.api.console

import de.acci.dvmm.application.console.ConsoleAccessCommandHandler
import de.acci.dvmm.application.console.RequestConsoleAccessCommand
import de.acci.dvmm.domain.console.ConsoleProtocol
import de.acci.dvmm.domain.console.ConsoleSession
import de.acci.eaf.auth.TokenClaims
import de.acci.eaf.tenant.TenantContext
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/vms/{vmId}/console")
public class ConsoleController(
    private val commandHandler: ConsoleAccessCommandHandler,
) {
    @PostMapping("/access")
    @ResponseStatus(HttpStatus.CREATED)
    public suspend fun requestConsoleAccess(
        @PathVariable vmId: UUID,
        @RequestBody request: ConsoleAccessRequest,
        @AuthenticationPrincipal claims: TokenClaims,
    ): ConsoleAccessResponse {
        val command = RequestConsoleAccessCommand(
            vmId = VmId(vmId),
            userId = claims.subject,
            tenantId = TenantContext.current(),
            protocol = request.protocol,
        )

        val session = commandHandler.handle(command)

        return ConsoleAccessResponse(
            sessionId = session.id.value,
            protocol = session.protocol,
            expiresAt = session.expiresAt,
            tunnelUrl = "/api/console/tunnel?sessionId=${session.id.value}",
        )
    }
}

public data class ConsoleAccessRequest(
    val protocol: ConsoleProtocol = ConsoleProtocol.SSH,
)

public data class ConsoleAccessResponse(
    val sessionId: UUID,
    val protocol: ConsoleProtocol,
    val expiresAt: java.time.Instant,
    val tunnelUrl: String,
)
```

---

## 5. Security Design

### 5.1 Authentication Flow

```text
1. User clicks "Open Console" on VM card
           │
           ▼
2. React calls POST /api/v1/vms/{vmId}/console/access
   - JWT token in Authorization header
   - Returns: { sessionId, tunnelUrl, expiresAt }
           │
           ▼
3. React opens WebSocket to tunnelUrl with token
   ws://.../api/console/tunnel?sessionId=...&token=...
           │
           ▼
4. GuacamoleTunnelEndpoint validates:
   - Token signature (JWT from Keycloak)
   - Token not expired
   - Session exists and not expired
   - User authorized for this VM
           │
           ▼
5. GuacamoleConnectionFactory creates tunnel:
   - Enforces tenant context (RLS)
   - Retrieves VM IP from projection
   - Gets credentials from vault
           │
           ▼
6. Connection established: Browser ↔ guacd ↔ VM
```

### 5.2 Multi-Tenant Isolation

| Layer | Mechanism | Enforcement |
|-------|-----------|-------------|
| **API** | JWT `tenant_id` claim | `TenantContextWebFilter` |
| **Application** | Permission check | `ConsoleAccessCommandHandler` |
| **Database** | PostgreSQL RLS | `SET app.tenant_id` |
| **Session Store** | Tenant-scoped sessions | `ConsoleSession.tenantId` |
| **guacd** | Per-connection config | No cross-tenant data |

### 5.3 Authorization Matrix

| Actor | Own VM | Other User's VM | Admin Override |
|-------|--------|-----------------|----------------|
| **User** | Allowed | Denied | N/A |
| **Admin** | Allowed | Allowed | Yes |
| **Cross-Tenant** | Denied | Denied | Denied |

### 5.4 Credential Security

| Concern | Mitigation |
|---------|------------|
| **Storage** | HashiCorp Vault or encrypted PostgreSQL |
| **Transit** | TLS to guacd, credentials never in browser |
| **Session Hijacking** | Short-lived sessions (5 min default) |
| **Token Theft** | httpOnly cookies, token in WS handshake only |
| **Audit Trail** | Log all console access events |

### 5.5 Rate Limiting

```kotlin
// Protect against console abuse
// Note: @RateLimiter annotation requires resilience4j-kotlin for suspend function support.
// Alternative: Use RateLimiterRegistry programmatically with coroutine wrappers.
public suspend fun requestConsoleAccess(...): ConsoleAccessResponse {
    return rateLimiterRegistry.rateLimiter("console-access")
        .executeSuspendFunction {
            // Actual implementation
            commandHandler.handle(command)
        }
}
```

```yaml
# resilience4j config (application.yml)
resilience4j:
  ratelimiter:
    instances:
      console-access:
        limit-for-period: 10
        limit-refresh-period: 1m
        timeout-duration: 0s
```

---

## 6. Frontend Integration

### 6.1 React Component

```typescript
// src/features/vm/components/VmConsole.tsx

import React, { useEffect, useRef, useState } from 'react';
import Guacamole from 'guacamole-common-js';
import { useConsoleAccess } from '../hooks/useConsoleAccess';
import { Alert, Spinner } from '@/components/ui';

interface VmConsoleProps {
  vmId: string;
  onClose: () => void;
}

export function VmConsole({ vmId, onClose }: VmConsoleProps) {
  const displayRef = useRef<HTMLDivElement>(null);
  const clientRef = useRef<Guacamole.Client | null>(null);
  const [status, setStatus] = useState<'connecting' | 'connected' | 'error'>('connecting');
  const [error, setError] = useState<string | null>(null);

  const { data: consoleAccess, isLoading } = useConsoleAccess(vmId);

  useEffect(() => {
    if (!consoleAccess || !displayRef.current) return;

    // Create WebSocket tunnel with auth token
    const tunnel = new Guacamole.WebSocketTunnel(
      `${window.location.origin}${consoleAccess.tunnelUrl}&token=${getAccessToken()}`
    );

    const client = new Guacamole.Client(tunnel);
    clientRef.current = client;

    // Attach display
    const display = client.getDisplay();
    displayRef.current.appendChild(display.getElement());

    // Handle state changes
    client.onstatechange = (state: number) => {
      switch (state) {
        case Guacamole.Client.State.CONNECTED:
          setStatus('connected');
          break;
        case Guacamole.Client.State.DISCONNECTED:
          setStatus('error');
          setError('Connection closed');
          break;
      }
    };

    // Handle errors
    client.onerror = (error: Guacamole.Status) => {
      setStatus('error');
      setError(error.message || 'Connection failed');
    };

    // Setup input handlers
    const mouse = new Guacamole.Mouse(display.getElement());
    mouse.onmousedown =
      mouse.onmouseup =
      mouse.onmousemove =
        (state: Guacamole.Mouse.State) => {
          client.sendMouseState(state);
        };

    const keyboard = new Guacamole.Keyboard(document);
    keyboard.onkeydown = (keysym: number) => {
      client.sendKeyEvent(1, keysym);
      return true;
    };
    keyboard.onkeyup = (keysym: number) => {
      client.sendKeyEvent(0, keysym);
      return true;
    };

    // Connect
    client.connect();

    // Cleanup
    return () => {
      // Remove keyboard handlers before disconnecting
      keyboard.onkeydown = () => true;
      keyboard.onkeyup = () => true;
      client.disconnect();
    };
  }, [consoleAccess]);

  // Handle window resize
  useEffect(() => {
    const handleResize = () => {
      const client = clientRef.current;
      if (client && displayRef.current) {
        const width = displayRef.current.clientWidth;
        const height = displayRef.current.clientHeight;
        client.sendSize(width, height);
      }
    };

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-96">
        <Spinner size="lg" />
        <span className="ml-2">Initializing console...</span>
      </div>
    );
  }

  if (status === 'error') {
    return (
      <Alert variant="destructive">
        <Alert.Title>Console Error</Alert.Title>
        <Alert.Description>{error}</Alert.Description>
      </Alert>
    );
  }

  return (
    <div className="relative">
      {status === 'connecting' && (
        <div className="absolute inset-0 flex items-center justify-center bg-black/50 z-10">
          <Spinner size="lg" className="text-white" />
        </div>
      )}
      <div
        ref={displayRef}
        className="w-full h-[600px] bg-black rounded-lg overflow-hidden"
        data-testid="vm-console-display"
      />
      <div className="mt-2 flex justify-end">
        <button
          onClick={onClose}
          className="px-4 py-2 text-sm text-gray-500 hover:text-gray-700"
        >
          Close Console
        </button>
      </div>
    </div>
  );
}
```

### 6.2 Console Access Hook

```typescript
// src/features/vm/hooks/useConsoleAccess.ts

import { useMutation } from '@tanstack/react-query';
import { api } from '@/lib/api';

interface ConsoleAccessResponse {
  sessionId: string;
  protocol: 'SSH' | 'VNC' | 'RDP';
  expiresAt: string;
  tunnelUrl: string;
}

export function useConsoleAccess(vmId: string) {
  return useMutation({
    mutationFn: async (): Promise<ConsoleAccessResponse> => {
      const response = await api.post(`/api/v1/vms/${vmId}/console/access`, {
        protocol: 'SSH',
      });
      return response.data;
    },
  });
}
```

### 6.3 VM Detail Page Integration

```typescript
// src/features/vm/pages/VmDetailPage.tsx

import { useState } from 'react';
import { VmConsole } from '../components/VmConsole';
import { Button } from '@/components/ui';
import { Terminal } from 'lucide-react';

export function VmDetailPage({ vmId }: { vmId: string }) {
  const [showConsole, setShowConsole] = useState(false);
  const { data: vm } = useVmDetail(vmId);

  const canOpenConsole = vm?.powerState === 'POWERED_ON';

  return (
    <div>
      {/* VM details */}
      <div className="mb-4">
        <h1>{vm?.name}</h1>
        <p>IP: {vm?.ipAddress}</p>
        <p>Status: {vm?.powerState}</p>
      </div>

      {/* Console button */}
      <Button
        onClick={() => setShowConsole(true)}
        disabled={!canOpenConsole}
        data-testid="open-console-button"
      >
        <Terminal className="mr-2 h-4 w-4" />
        Open Console
      </Button>

      {!canOpenConsole && (
        <p className="text-sm text-muted-foreground mt-2">
          VM must be powered on to access console
        </p>
      )}

      {/* Console modal/drawer */}
      {showConsole && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-4 w-full max-w-4xl">
            <h2 className="text-lg font-semibold mb-4">Console: {vm?.name}</h2>
            <VmConsole vmId={vmId} onClose={() => setShowConsole(false)} />
          </div>
        </div>
      )}
    </div>
  );
}
```

---

## 7. Testing Strategy

### 7.1 Test Pyramid

```text
                    ┌─────────────────────┐
                    │      E2E Tests      │  5%
                    │  - Playwright       │
                    │  - Full user flow   │
                    ├─────────────────────┤
                    │  Integration Tests  │  35%
                    │  - vcsim + guacd    │
                    │  - Container SSH    │
                    │  - WebSocket tunnel │
                    ├─────────────────────┤
                    │    Unit Tests       │  60%
                    │  - ConnectionFactory│
                    │  - Permission check │
                    │  - Session mgmt     │
                    └─────────────────────┘
```

### 7.2 Unit Tests

```kotlin
// GuacamoleConnectionFactoryTest.kt

class GuacamoleConnectionFactoryTest {

    private val vmRepository = mockk<VmProjectionRepository>()
    private val credentialVault = mockk<CredentialVault>()
    private val guacdProperties = GuacdProperties(host = "localhost", port = 4822)

    private val factory = GuacamoleConnectionFactory(
        vmProjectionRepository = vmRepository,
        credentialVault = credentialVault,
        guacdProperties = guacdProperties,
    )

    // Test helper to create ConsoleSession with default values
    private fun createSession(
        id: ConsoleSessionId = ConsoleSessionId.generate(),
        vmId: VmId = VmId(UUID.randomUUID()),
        userId: UserId = UserId(UUID.randomUUID()),
        tenantId: TenantId = TenantId(UUID.randomUUID()),
        protocol: ConsoleProtocol = ConsoleProtocol.SSH,
        createdAt: Instant = Instant.now(),
        expiresAt: Instant = Instant.now().plusMinutes(5),
    ) = ConsoleSession(id, vmId, userId, tenantId, protocol, createdAt, expiresAt)

    @Test
    fun `createTunnel fails for expired session`() = runTest {
        // Given: Expired session
        val session = createSession(expiresAt = Instant.now().minusSeconds(60))
        factory.registerSession(session)

        // When/Then: Should throw
        assertThrows<SessionExpiredException> {
            factory.createTunnel(session.id)
        }
    }

    @Test
    fun `createTunnel fails for unknown session`() = runTest {
        val unknownSessionId = ConsoleSessionId.generate()

        assertThrows<InvalidSessionException> {
            factory.createTunnel(unknownSessionId)
        }
    }

    @Test
    fun `createTunnel enforces tenant isolation`() = runTest {
        // Given: Session for tenant A
        val tenantA = TenantId.random()
        val session = createSession(tenantId = tenantA)
        factory.registerSession(session)

        // And: VM lookup scoped to tenant (RLS simulation)
        coEvery {
            vmRepository.findById(session.vmId)
        } returns null // RLS hides cross-tenant data

        // When/Then: Should throw VmNotFound (not security exception)
        assertThrows<VmNotFoundException> {
            factory.createTunnel(session.id)
        }
    }

    @Test
    fun `buildConfiguration creates correct SSH config`() = runTest {
        val session = createSession(protocol = ConsoleProtocol.SSH)
        factory.registerSession(session)

        val vm = VmProjection(
            id = session.vmId,
            ipAddress = "192.168.1.100",
            // ...
        )
        coEvery { vmRepository.findById(session.vmId) } returns vm

        val credentials = SshCredentials(
            username = "vmuser",
            password = "secret",
            port = 22,
        )
        coEvery { credentialVault.getSshCredentials(session.vmId) } returns credentials

        // Can't easily test tunnel creation without guacd
        // Instead, test the configuration building logic directly
        // Note: buildConfiguration is marked internal with @VisibleForTesting
        val config = factory.buildConfiguration(session)

        assertThat(config.protocol).isEqualTo("ssh")
        assertThat(config.getParameter("hostname")).isEqualTo("192.168.1.100")
        assertThat(config.getParameter("username")).isEqualTo("vmuser")
    }
}
```

### 7.3 Integration Tests with vcsim Containers-as-VMs

The vcsim containers-as-VMs feature enables realistic end-to-end testing. See [research-vcsim-containers-as-vms.md](research-vcsim-containers-as-vms.md) for detailed documentation.

```kotlin
// GuacamoleVcsimIntegrationTest.kt

@Testcontainers
@IntegrationTest
class GuacamoleVcsimIntegrationTest : VcsimIntegrationTest() {

    companion object {
        @Container
        @JvmStatic
        val guacd = GenericContainer("guacamole/guacd:1.6.0")
            .withExposedPorts(4822)
            .waitingFor(Wait.forListeningPort())
    }

    @Autowired
    private lateinit var connectionFactory: GuacamoleConnectionFactory

    @Test
    fun `console connects to vcsim container-vm via SSH`() = runTest {
        // 1. Configure vcsim VM with SSH container
        val vmPath = "/DC0/vm/DC0_H0_VM0"
        vcsim.attachContainer(vmPath, "dvmm-test-ssh:latest")
        vcsim.powerOn(vmPath)

        // 2. Wait for VM to get IP
        val vmIp = await().atMost(Duration.ofSeconds(30)).until({
            vcsim.getGuestIp(vmPath)
        }) { it != null }!!

        // 3. Create console session
        val session = ConsoleSession(
            id = ConsoleSessionId.generate(),
            vmId = VmId(vmPath),
            userId = testUserId,
            tenantId = testTenantId,
            protocol = ConsoleProtocol.SSH,
            createdAt = Instant.now(),
            expiresAt = Instant.now().plusMinutes(5),
        )
        connectionFactory.registerSession(session)

        // 4. Create tunnel
        val tunnel = connectionFactory.createTunnel(session.id)

        // 5. Verify connection
        assertThat(tunnel.isOpen).isTrue()

        // 6. Cleanup
        tunnel.close()
        vcsim.powerOff(vmPath)
    }

    @Test
    fun `console fails when VM is powered off`() = runTest {
        val vmPath = "/DC0/vm/DC0_H0_VM1"
        vcsim.attachContainer(vmPath, "dvmm-test-ssh:latest")

        // VM stays powered off - container not started

        val session = createSession(vmId = VmId(vmPath))
        connectionFactory.registerSession(session)

        // Connection should fail (container not running)
        assertThrows<GuacamoleServerException> {
            connectionFactory.createTunnel(session.id)
        }
    }

    @Test
    fun `vm power state changes affect ssh availability`() = runTest {
        val vmPath = "/DC0/vm/DC0_H0_VM2"
        vcsim.attachContainer(vmPath, "dvmm-test-ssh:latest")

        // Power on - SSH should work
        vcsim.powerOn(vmPath)
        await().until { canConnectSsh(vmPath) }

        // Suspend - SSH should hang
        vcsim.suspend(vmPath)
        assertThat(canConnectSsh(vmPath, timeoutMs = 2000)).isFalse()

        // Resume - SSH should work again
        vcsim.powerOn(vmPath)
        await().until { canConnectSsh(vmPath) }
    }

    // Helper to test SSH connectivity
    private fun canConnectSsh(vmPath: String, timeoutMs: Long = 5000): Boolean {
        val ip = vcsim.getGuestIp(vmPath) ?: return false
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, 22), timeoutMs.toInt())
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
```

### 7.4 E2E Tests with Playwright

```typescript
// tests/e2e/console-access.spec.ts

import { test, expect } from '@playwright/test';
import { loginAsUser, setupVcsimVm } from './helpers';

test.describe('VM Console Access', () => {
  test.beforeAll(async () => {
    // Setup vcsim VM with SSH container
    await setupVcsimVm('DC0_H0_VM0', 'dvmm-test-ssh:latest');
  });

  test('user can open SSH console for running VM', async ({ page }) => {
    await loginAsUser(page, 'testuser@acme.com', 'testpass');

    // Navigate to VM details
    await page.goto('/vms/DC0_H0_VM0');

    // Verify VM is powered on
    await expect(page.getByText('Status: Running')).toBeVisible();

    // Click Open Console
    await page.click('[data-testid="open-console-button"]');

    // Wait for console to connect
    const consoleDisplay = page.locator('[data-testid="vm-console-display"]');
    await expect(consoleDisplay).toBeVisible({ timeout: 15000 });

    // Verify terminal is interactive
    await consoleDisplay.click();
    await page.keyboard.type('whoami');
    await page.keyboard.press('Enter');

    // Should see command output (testuser from SSH container)
    await expect(consoleDisplay).toContainText('testuser');
  });

  test('console button disabled when VM powered off', async ({ page }) => {
    await loginAsUser(page, 'testuser@acme.com', 'testpass');

    // Power off VM
    await page.goto('/vms/DC0_H0_VM1');
    await page.click('[data-testid="power-off-button"]');

    // Console button should be disabled
    const consoleButton = page.locator('[data-testid="open-console-button"]');
    await expect(consoleButton).toBeDisabled();
    await expect(page.getByText('VM must be powered on')).toBeVisible();
  });

  test('cross-tenant console access denied', async ({ page }) => {
    // Login as user from tenant B
    await loginAsUser(page, 'otheruser@other-tenant.com', 'testpass');

    // Try to access tenant A's VM directly
    await page.goto('/vms/DC0_H0_VM0');

    // Should see 404 or access denied
    await expect(page.getByText('Not Found')).toBeVisible();
  });
});
```

### 7.5 Test Docker Compose

```yaml
# docker-compose.console-test.yml
services:
  vcsim:
    image: vmware/vcsim:latest
    ports:
      - "8989:8989"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    networks:
      - test-net

  guacd:
    image: guacamole/guacd:1.6.0
    networks:
      - test-net

  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: dvmm_test
      POSTGRES_USER: test
      POSTGRES_PASSWORD: test
    volumes:
      - ./init-rls.sql:/docker-entrypoint-initdb.d/init.sql
    networks:
      - test-net

  keycloak:
    image: quay.io/keycloak/keycloak:26.0.0
    command: start-dev --import-realm
    volumes:
      - ./test-realm.json:/opt/keycloak/data/import/realm.json
    networks:
      - test-net

  dvmm-app:
    build: .
    depends_on:
      - vcsim
      - guacd
      - postgres
      - keycloak
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/dvmm_test
      VMWARE_URL: https://vcsim:8989/sdk
      GUACD_HOST: guacd
      GUACD_PORT: 4822
    networks:
      - test-net

networks:
  test-net:
    driver: bridge
```

---

## 8. Deployment Architecture

### 8.1 Docker Sidecar (Recommended for MVP)

```yaml
# docker-compose.yml
services:
  dvmm-app:
    image: dvmm:latest
    depends_on:
      - guacd
    environment:
      - GUACD_HOST=guacd
      - GUACD_PORT=4822
    networks:
      - internal

  guacd:
    image: guacamole/guacd:1.6.0
    restart: unless-stopped
    # No exposed ports - only accessible from dvmm-app
    networks:
      - internal

networks:
  internal:
    driver: bridge
```

### 8.2 Kubernetes Deployment

```yaml
# kubernetes/dvmm-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: dvmm
spec:
  replicas: 3
  template:
    spec:
      containers:
        - name: dvmm-app
          image: dvmm:latest
          env:
            - name: GUACD_HOST
              value: "localhost"  # Sidecar
            - name: GUACD_PORT
              value: "4822"
          ports:
            - containerPort: 8080

        - name: guacd
          image: guacamole/guacd:1.6.0
          ports:
            - containerPort: 4822
          resources:
            requests:
              memory: "256Mi"
              cpu: "100m"
            limits:
              memory: "512Mi"
              cpu: "500m"
```

### 8.3 NGINX WebSocket Configuration

```nginx
# nginx.conf
upstream dvmm {
    server dvmm-app:8080;
}

server {
    listen 443 ssl;

    # WebSocket tunnel endpoint
    location /api/console/tunnel {
        proxy_pass http://dvmm;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }

    # Regular API endpoints
    location /api/ {
        proxy_pass http://dvmm;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

---

## 9. Implementation Roadmap

### Phase 1: Foundation (1 Story)

**Objective:** Set up Guacamole infrastructure and basic connectivity.

**Tasks:**
1. Add `guacamole-common` dependency to `dvmm-infrastructure`
2. Add `guacamole-common-js` to frontend
3. Add guacd to Docker Compose
4. Implement `GuacdProperties` configuration class
5. Create basic connectivity test

**Acceptance Criteria:**
- guacd container starts with DVMM
- Basic health check verifies guacd connectivity
- Unit tests for configuration

### Phase 2: Backend Implementation (1-2 Stories)

**Objective:** Implement console access domain, application, and infrastructure layers.

**Tasks:**
1. Domain model: `ConsoleSession`, `ConsoleProtocol`, events
2. Application: `ConsoleAccessCommandHandler`, permission checks
3. Infrastructure: `GuacamoleConnectionFactory` with SSH support
4. Port interface and adapter pattern
5. Unit tests (80% coverage)
6. Mutation tests (70% threshold)

**Acceptance Criteria:**
- Console session can be created for authorized users
- Tenant isolation enforced via RLS
- Cross-tenant access denied
- All tests passing

### Phase 3: API Layer (1 Story)

**Objective:** Expose console access via REST and WebSocket APIs.

**Tasks:**
1. REST endpoint: `POST /api/v1/vms/{vmId}/console/access`
2. WebSocket endpoint: `/api/console/tunnel`
3. JWT validation in WebSocket handshake
4. Rate limiting configuration
5. Integration tests with vcsim + containers-as-VMs

**Acceptance Criteria:**
- API creates console sessions
- WebSocket tunnel authenticates properly
- Integration test demonstrates full flow
- Rate limiting prevents abuse

### Phase 4: Frontend (1 Story)

**Objective:** Build React console component and integrate into VM detail page.

**Tasks:**
1. `VmConsole` React component with guacamole-common-js
2. `useConsoleAccess` hook for API integration
3. Console button on VM detail page
4. Error handling and loading states
5. E2E tests with Playwright

**Acceptance Criteria:**
- User can open console from VM detail page
- Console renders SSH terminal
- Keyboard and mouse input works
- Errors displayed appropriately

### Phase 5: Production Readiness (1 Story)

**Objective:** Prepare for production deployment.

**Tasks:**
1. Credential vault integration (HashiCorp Vault or encrypted DB)
2. Audit logging for console access events
3. Monitoring: console session metrics
4. Documentation: operations runbook
5. Performance testing under load

**Acceptance Criteria:**
- Credentials never logged or exposed
- All console access events audited
- Metrics exported to Prometheus
- Runbook reviewed by ops team

---

## 10. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| **guacd stability** | Low | High | Docker health checks, auto-restart, sidecar pattern |
| **WebSocket proxy issues** | Medium | High | Thorough NGINX testing, WebSocket-specific config |
| **Performance under load** | Medium | Medium | Load testing in Phase 5, connection pooling |
| **Cross-tenant access** | Low | Critical | RLS + app-layer checks + integration tests |
| **SSH credential exposure** | Low | Critical | Vault integration, no credential logging |
| **Session hijacking** | Low | High | Short-lived sessions, token validation |
| **Browser compatibility** | Low | Medium | Test on major browsers, graceful degradation |

---

## 11. References

### Apache Guacamole Documentation

- [Official Manual](https://guacamole.apache.org/doc/gug/)
- [Architecture Overview](https://guacamole.apache.org/doc/gug/guacamole-architecture.html)
- [Writing Custom Applications](https://guacamole.apache.org/doc/gug/writing-you-own-guacamole-app.html)
- [Custom Authentication](https://guacamole.apache.org/doc/gug/custom-auth.html)
- [External Authentication](https://guacamole.apache.org/doc/gug/external-auth.html)
- [API Documentation](https://guacamole.apache.org/api-documentation/)
- [guacamole-common-js JSDoc](https://guacamole.apache.org/doc/guacamole-common-js/)

### Integration Resources

- [Spring Boot Guacamole Integration (Stack Overflow)](https://stackoverflow.com/questions/46345777/integrating-apache-guacamole-in-spring-boot-application)
- [Guacamole REST API (Unofficial)](https://github.com/ridvanaltun/guacamole-rest-api-documentation)
- [WebSocket Tunnel Documentation](https://guacamole.apache.org/doc/guacamole-common-js/Guacamole.WebSocketTunnel.html)

### VMware Integration

- [GUACAMOLE-1641: vSphere WebMKS Support](https://issues.apache.org/jira/browse/GUACAMOLE-1641)
- [vcsim Features Wiki](https://github.com/vmware/govmomi/wiki/vcsim-features)
- [Containers-as-VMs](https://github.com/vmware/govmomi/wiki/vcsim-features#containers-as-vms)

### Related DVMM Documentation

- [Architecture](architecture.md)
- [Security Architecture](security-architecture.md)
- [Test Design System](test-design-system.md)
- [vcsim Containers-as-VMs Research](research-vcsim-containers-as-vms.md)

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-11-28 | Claude (Research Agent) | Initial comprehensive research |

---

## Appendix A: SSH Container Dockerfile

```dockerfile
# test-containers/ssh-vm/Dockerfile
FROM alpine:3.19

RUN apk add --no-cache \
    openssh-server \
    bash

# Generate host keys
RUN ssh-keygen -A

# Create test user
RUN adduser -D -s /bin/bash testuser && \
    echo "testuser:testpass" | chpasswd

# Configure SSH
RUN sed -i 's/#PasswordAuthentication yes/PasswordAuthentication yes/' /etc/ssh/sshd_config && \
    sed -i 's/#PermitRootLogin prohibit-password/PermitRootLogin no/' /etc/ssh/sshd_config && \
    echo "AllowUsers testuser" >> /etc/ssh/sshd_config

EXPOSE 22

CMD ["/usr/sbin/sshd", "-D", "-e"]
```

## Appendix B: guacd Configuration Properties

```kotlin
// GuacdProperties.kt
@ConfigurationProperties(prefix = "dvmm.guacd")
data class GuacdProperties(
    val host: String = "localhost",
    val port: Int = 4822,
    val connectionTimeoutMs: Long = 15000,
    val readTimeoutMs: Long = 15000,
)
```

```yaml
# application.yml
dvmm:
  guacd:
    host: ${GUACD_HOST:localhost}
    port: ${GUACD_PORT:4822}
    connection-timeout-ms: 15000
    read-timeout-ms: 15000
```

---

*This document provides comprehensive guidance for integrating Apache Guacamole into DVMM. Implementation should proceed according to the phased roadmap, with each phase producing working, tested software.*
