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

### Module Structure

```
dvmm/
└── dvmm-tui/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── kotlin/
        │   │   └── de/acci/dvmm/tui/
        │   │       ├── DvmmTuiApplication.kt       # Entry point
        │   │       ├── TuiRunner.kt                # CommandLineRunner
        │   │       │
        │   │       ├── config/
        │   │       │   ├── TuiConfiguration.kt     # Spring config
        │   │       │   └── TuiProperties.kt        # Externalized config
        │   │       │
        │   │       ├── auth/
        │   │       │   ├── TuiAuthenticator.kt     # Token validation
        │   │       │   └── TuiSecurityContext.kt   # User/Tenant context
        │   │       │
        │   │       ├── dsl/                        # Kotlin DSL wrapper
        │   │       │   ├── DvmmWindow.kt
        │   │       │   ├── DvmmPanel.kt
        │   │       │   ├── DvmmTable.kt
        │   │       │   └── DvmmActionList.kt
        │   │       │
        │   │       ├── screens/
        │   │       │   ├── MainScreen.kt           # Main dashboard
        │   │       │   ├── ApprovalScreen.kt       # Approval queue
        │   │       │   ├── RequestDetailScreen.kt  # Single request view
        │   │       │   ├── HealthScreen.kt         # System health
        │   │       │   ├── AuditScreen.kt          # Audit log viewer
        │   │       │   └── LoginScreen.kt          # Authentication
        │   │       │
        │   │       ├── widgets/
        │   │       │   ├── StatusIndicator.kt      # ● OK / ● Error
        │   │       │   ├── RequestTable.kt         # VM request list
        │   │       │   ├── HealthPanel.kt          # Health metrics
        │   │       │   └── KeyHelpBar.kt           # Bottom help bar
        │   │       │
        │   │       └── adapters/
        │   │           ├── TuiCommandAdapter.kt    # → CommandGateway
        │   │           └── TuiQueryAdapter.kt      # → QueryGateway
        │   │
        │   └── resources/
        │       ├── application-tui.yml             # TUI-specific config
        │       └── banner-tui.txt                  # ASCII banner
        │
        └── test/
            └── kotlin/
                └── de/acci/dvmm/tui/
                    ├── screens/
                    │   └── ApprovalScreenTest.kt
                    └── adapters/
                        └── TuiCommandAdapterTest.kt
```

### Dependencies

```kotlin
// dvmm-tui/build.gradle.kts
plugins {
    id("eaf.kotlin-conventions")
    id("eaf.spring-conventions")
    id("eaf.test-conventions")
}

dependencies {
    // Internal modules
    implementation(project(":dvmm:dvmm-application"))
    implementation(project(":eaf:eaf-cqrs-core"))
    implementation(project(":eaf:eaf-auth"))
    implementation(project(":eaf:eaf-tenant"))

    // TUI framework
    implementation("com.googlecode.lanterna:lanterna:3.1.2")

    // Spring Boot (CLI mode)
    implementation("org.springframework.boot:spring-boot-starter")

    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Testing
    testImplementation(project(":eaf:eaf-testing"))
    testImplementation("io.mockk:mockk")
}

// Disable web server
springBoot {
    mainClass.set("de.acci.dvmm.tui.DvmmTuiApplicationKt")
}
```

### Entry Point

```kotlin
// DvmmTuiApplication.kt
package de.acci.dvmm.tui

import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(
    scanBasePackages = [
        "de.acci.dvmm.tui",
        "de.acci.dvmm.application",
        "de.acci.eaf"
    ]
)
class DvmmTuiApplication

fun main(args: Array<String>) {
    runApplication<DvmmTuiApplication>(*args) {
        webApplicationType = WebApplicationType.NONE
        setAdditionalProfiles("tui")
    }
}
```

```kotlin
// TuiRunner.kt
package de.acci.dvmm.tui

import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import de.acci.dvmm.tui.screens.MainScreen
import de.acci.dvmm.tui.auth.TuiAuthenticator
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class TuiRunner(
    private val authenticator: TuiAuthenticator,
    private val mainScreen: MainScreen,
    private val tuiProperties: TuiProperties
) : CommandLineRunner {

    override fun run(vararg args: String) {
        val terminal = DefaultTerminalFactory().createTerminal()
        val screen = TerminalScreen(terminal).apply { startScreen() }

        try {
            val gui = MultiWindowTextGUI(screen, DefaultWindowManager(), EmptySpace())

            // Authenticate user
            val authContext = authenticator.authenticate(gui, args)

            // Launch main screen
            mainScreen.show(gui, authContext)

        } finally {
            screen.stopScreen()
            terminal.close()
        }
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

## Adapters

### Command Adapter

```kotlin
// adapters/TuiCommandAdapter.kt
package de.acci.dvmm.tui.adapters

import de.acci.dvmm.application.commands.*
import de.acci.eaf.cqrs.command.CommandGateway
import de.acci.eaf.core.domain.TenantId
import de.acci.eaf.core.domain.UserId
import org.springframework.stereotype.Component

@Component
class TuiCommandAdapter(
    private val commandGateway: CommandGateway
) {
    suspend fun approveRequest(
        requestId: VmRequestId,
        tenantId: TenantId,
        approverId: UserId,
        comment: String? = null
    ): Result<Unit, ApprovalError> {
        val command = ApproveVmRequestCommand(
            requestId = requestId,
            tenantId = tenantId,
            approverId = approverId,
            comment = comment
        )
        return commandGateway.send(command)
    }

    suspend fun rejectRequest(
        requestId: VmRequestId,
        tenantId: TenantId,
        rejecterId: UserId,
        reason: String
    ): Result<Unit, ApprovalError> {
        val command = RejectVmRequestCommand(
            requestId = requestId,
            tenantId = tenantId,
            rejecterId = rejecterId,
            reason = reason
        )
        return commandGateway.send(command)
    }
}
```

### Query Adapter

```kotlin
// adapters/TuiQueryAdapter.kt
package de.acci.dvmm.tui.adapters

import de.acci.dvmm.application.queries.*
import de.acci.eaf.cqrs.query.QueryGateway
import de.acci.eaf.core.domain.TenantId
import org.springframework.stereotype.Component

@Component
class TuiQueryAdapter(
    private val queryGateway: QueryGateway
) {
    suspend fun getPendingApprovals(tenantId: TenantId): List<VmRequestSummary> {
        val query = GetPendingApprovalsQuery(tenantId = tenantId)
        return queryGateway.query(query)
    }

    suspend fun getRequestDetails(
        requestId: VmRequestId,
        tenantId: TenantId
    ): VmRequestDetails? {
        val query = GetVmRequestDetailsQuery(
            requestId = requestId,
            tenantId = tenantId
        )
        return queryGateway.query(query)
    }

    suspend fun getSystemHealth(): SystemHealthStatus {
        val query = GetSystemHealthQuery()
        return queryGateway.query(query)
    }

    suspend fun getAuditLog(
        tenantId: TenantId,
        filter: AuditLogFilter = AuditLogFilter.default()
    ): List<AuditLogEntry> {
        val query = GetAuditLogQuery(
            tenantId = tenantId,
            filter = filter
        )
        return queryGateway.query(query)
    }
}
```

---

## Authentication Flow

### Token-Based Authentication (Recommended)

```kotlin
// auth/TuiAuthenticator.kt
package de.acci.dvmm.tui.auth

import de.acci.eaf.auth.TokenValidator
import de.acci.eaf.tenant.TenantId
import org.springframework.stereotype.Component

@Component
class TuiAuthenticator(
    private val tokenValidator: TokenValidator,
    private val tuiProperties: TuiProperties
) {
    fun authenticate(gui: MultiWindowTextGUI, args: Array<String>): TuiSecurityContext {
        // Priority: CLI arg > Environment variable > Interactive login
        val token = args.findToken()
            ?: System.getenv("DVMM_TOKEN")
            ?: promptForLogin(gui)

        val claims = tokenValidator.validate(token)
            ?: throw AuthenticationException("Invalid or expired token")

        return TuiSecurityContext(
            userId = claims.userId,
            tenantId = claims.tenantId,
            roles = claims.roles,
            token = token
        )
    }

    private fun promptForLogin(gui: MultiWindowTextGUI): String {
        val loginScreen = LoginScreen()
        return loginScreen.show(gui)
    }
}

data class TuiSecurityContext(
    val userId: UserId,
    val tenantId: TenantId,
    val roles: Set<Role>,
    val token: String
) {
    fun hasRole(role: Role): Boolean = role in roles
    fun isAdmin(): Boolean = hasRole(Role.ADMIN) || hasRole(Role.MANAGER)
}
```

### CLI Usage

```bash
# Option 1: Provide token directly
dvmm-tui --token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...

# Option 2: Use environment variable
export DVMM_TOKEN=$(dvmm auth login --output=token)
dvmm-tui

# Option 3: Interactive login (fallback)
dvmm-tui
# → Prompts for username/password in TUI
```

---

## Configuration

```yaml
# application-tui.yml
spring:
  main:
    web-application-type: none
    banner-mode: off

logging:
  file:
    name: logs/dvmm-tui.log
  level:
    root: WARN
    de.acci.dvmm.tui: INFO
  pattern:
    console: ""  # Disable console output

dvmm:
  tui:
    refresh-interval: 5s
    session-timeout: 4h
    health-check-interval: 30s
    date-format: "yyyy-MM-dd HH:mm:ss"
    colors:
      primary: "cyan"
      success: "green"
      warning: "yellow"
      error: "red"
    keybindings:
      approve: "a"
      reject: "r"
      refresh: "F5"
      quit: "q"
      help: "?"
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
| Concurrent modification | Refresh data before each action; handle optimistic locking errors gracefully |
| Session timeout during long operations | Show warning before timeout; allow session refresh |
| Network interruption | Show connection status; queue actions for retry |

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
