# TUI Analysis for DCM Admin Interface

**Author:** Claude (AI Assistant)
**Date:** 2025-11-29
**Status:** Draft
**Related Documents:** [Architecture](./architecture.md), [PRD](./prd.md), [Epics](./epics.md)

---

## Executive Summary

This document analyzes the feasibility and approach for implementing an optional Terminal User Interface (TUI) for DCM, targeting experienced administrators who prefer keyboard-driven workflows over web interfaces. The TUI would serve as a complementary interface for power users, particularly useful in headless server environments or SSH sessions.

**Recommendation:** Implement a Lanterna-based TUI as a post-MVP feature, focusing on admin-critical workflows (approvals, monitoring, audit access).

---

## Business Case

### Target Users

| User Type | Use Case | Benefit |
|-----------|----------|---------|
| **DevOps Engineers** | Quick approvals during incident response | No browser context-switch |
| **System Administrators** | Headless server management via SSH | Works without GUI |
| **Power Users** | Batch operations, scripting | Keyboard efficiency |
| **On-Call Staff** | After-hours approvals from mobile terminal | Lightweight access |

### Value Proposition

1. **Speed:** Keyboard shortcuts for common actions (approve/reject in <2 seconds)
2. **Accessibility:** Works over SSH, tmux, screen sessions
3. **Resource Efficiency:** Minimal bandwidth, no browser overhead
4. **Scriptability:** Can be invoked from automation scripts
5. **Differentiation:** Few competitors offer TUI interfaces

### Non-Goals

- **NOT** a replacement for the web UI
- **NOT** feature-complete parity with web interface
- **NOT** targeting end-users (only admins)

---

## Framework Evaluation

### Candidates Analyzed

| Framework | Language | Approach | License |
|-----------|----------|----------|---------|
| **Lanterna** | Java | GUI Toolkit (Windows, Widgets) | LGPL 3.0 |
| **Jexer** | Java | TurboVision-style GUI | MIT |
| **Kotter** | Kotlin | Declarative DSL | Apache 2.0 |
| **Spring Shell** | Java | CLI Commands (no GUI) | Apache 2.0 |

### Component Comparison

| Component | Lanterna | Jexer | Kotter |
|-----------|----------|-------|--------|
| Windows/Dialogs | Yes | Yes | No |
| Buttons | Yes | Yes | No |
| CheckBox/Radio | Yes | Yes | No |
| ComboBox/Dropdown | Yes | Yes | No |
| Text Input | Yes | Yes | Yes |
| Tables | Yes | Yes | No |
| Menu Bar | Yes | Yes | No |
| Action Lists | Yes | Yes | No |
| Progress Bar | Yes | Yes | Manual |
| Mouse Support | Yes | Yes | No |
| Layout Managers | Yes | Yes | No |
| Kotlin-native | No | No | Yes |
| Reactive Updates | Manual | Manual | Yes (LiveVars) |

### Decision: Lanterna

**Rationale:**

1. **Complete Widget Set:** All required components available out-of-the-box
2. **Mature & Stable:** 2.5k+ GitHub stars, active maintenance, production-proven
3. **Pure Java:** No native dependencies, works everywhere JVM runs
4. **IDE Fallback:** Automatically uses Swing terminal emulator in development
5. **Kotlin-Compatible:** Direct interop, can add thin DSL wrapper

**Trade-offs Accepted:**

- Java API (mitigated by Kotlin wrapper)
- LGPL 3.0 license (acceptable for optional module)
- Imperative style (mitigated by wrapper DSL)

**Why Not Kotter:**

Kotter lacks fundamental GUI components (windows, buttons, checkboxes, tables, menus) required for a dashboard-style admin interface. It's better suited for simple CLI tools with colored output, not multi-panel TUIs.

**Why Not Jexer:**

While feature-rich (including image support), Jexer is more complex than needed. Lanterna provides sufficient functionality with simpler API.

---

## Feature Scope Analysis

### Tier 1: Core Admin Workflows (MVP TUI)

| Feature | PRD Reference | Complexity | Value |
|---------|---------------|------------|-------|
| **Approval Queue** | FR25-FR29 | Medium | Critical |
| **Quick Approve/Reject** | FR27-FR28 | Low | Critical |
| **Request Details View** | FR26 | Low | High |
| **System Health Dashboard** | FR73 | Low | High |
| **Real-time Status Updates** | FR44 | Medium | High |

### Tier 2: Extended Admin Features

| Feature | PRD Reference | Complexity | Value |
|---------|---------------|------------|-------|
| **Audit Log Viewer** | FR58-FR60 | Medium | Medium |
| **CSV Export** | FR57 | Low | Medium |
| **VMware Connection Test** | FR71 | Low | Medium |
| **Quota Overview** | FR82-FR84 | Low | Medium |
| **Request Filtering** | FR53-FR54 | Medium | Medium |

### Tier 3: Nice-to-Have

| Feature | Description | Complexity |
|---------|-------------|------------|
| Tenant Switching | Multi-tenant context switch | Low |
| VM List Browser | Browse VMs by project | Medium |
| Notification History | View past notifications | Low |
| Keyboard Shortcuts Help | F1 help overlay | Low |

### Explicitly Out of Scope

| Feature | Reason |
|---------|--------|
| VM Request Creation | Too complex for TUI (form validation, project selection) |
| User Management | Rare operation, web-only acceptable |
| Full Reporting | Complex visualizations need web |
| Onboarding Flows | First-time users should use web |

---

## Architecture Fit

### Hexagonal Architecture Compliance

The TUI integrates as a **Driving Adapter** in our hexagonal architecture:

```
                    ┌─────────────────────────────────────┐
                    │           Driving Adapters          │
                    │  ┌─────────┐  ┌─────────┐          │
                    │  │ REST API│  │   TUI   │ ◄── NEW  │
                    │  │ (dcm-  │  │ (dcm-  │          │
                    │  │   api)  │  │   tui)  │          │
                    │  └────┬────┘  └────┬────┘          │
                    └───────┼────────────┼───────────────┘
                            │            │
                            ▼            ▼
                    ┌─────────────────────────────────────┐
                    │         Application Layer           │
                    │         (dcm-application)          │
                    │                                     │
                    │  CommandGateway    QueryGateway     │
                    │       │                 │           │
                    │       ▼                 ▼           │
                    │  CommandHandlers   QueryHandlers    │
                    └─────────────────────────────────────┘
                                    │
                                    ▼
                    ┌─────────────────────────────────────┐
                    │           Domain Layer              │
                    │         (dcm-domain)               │
                    └─────────────────────────────────────┘
```

**Key Principle:** The TUI is just another adapter. It uses the same `CommandGateway` and `QueryGateway` as the REST API. Zero business logic duplication.

### Module Structure

```
dcm/
├── dcm-domain/          # Unchanged
├── dcm-application/     # Unchanged
├── dcm-api/             # Unchanged (REST)
├── dcm-infrastructure/  # Unchanged
├── dcm-app/             # Web application entry point
└── dcm-tui/             # NEW: TUI adapter module
    ├── build.gradle.kts
    └── src/main/kotlin/de/acci/dcm/tui/
        ├── DvmmTuiApplication.kt
        ├── config/
        ├── screens/
        ├── widgets/
        └── adapters/
```

### Dependency Direction

```
dcm-tui
    │
    ├──► dcm-application (CommandGateway, QueryGateway)
    ├──► eaf-cqrs-core (Command, Query interfaces)
    ├──► eaf-auth (Authentication context)
    └──► lanterna (TUI framework)
```

**Architecture Rules Preserved:**
- TUI module depends on application layer (not vice versa)
- No domain logic in TUI module
- TUI cannot bypass application layer

---

## Technical Considerations

### Authentication

The TUI must authenticate users before allowing operations:

**Option A: Interactive Login**
```
┌─────────────────────────────────────┐
│         DCM Admin Console          │
├─────────────────────────────────────┤
│                                     │
│  Username: admin@acme.de            │
│  Password: ********                 │
│                                     │
│  Tenant:   [acme-corp        ▼]     │
│                                     │
│         [ Login ]  [ Exit ]         │
│                                     │
└─────────────────────────────────────┘
```

**Option B: Token-based (Recommended)**
```bash
# User authenticates once via browser, gets token
dcm-tui --token=$(dcm auth login)

# Or use environment variable
export DCM_TOKEN="eyJ..."
dcm-tui
```

**Recommendation:** Option B for better security (no password in terminal history).

### Tenant Context

Multi-tenancy must be enforced:

1. Tenant selected at startup (from token or prompt)
2. All queries/commands include tenant context
3. Tenant displayed in TUI header at all times
4. Tenant switch requires re-authentication

### Real-time Updates

For live status updates (approval queue changes, provisioning progress):

**MVP Approach:** Polling
- Background thread polls every 5 seconds
- Updates screen on changes
- Simple, reliable

**Growth Approach:** Event Subscription
- Subscribe to domain events via EventStore
- Push updates to TUI
- More efficient, instant updates

### Logging Considerations

Lanterna controls the terminal. Standard logging would corrupt the display.

**Solution:**
1. Redirect logs to file when TUI is active
2. Provide in-TUI log viewer for recent entries
3. Use structured logging for machine parsing

```kotlin
// application.yml for TUI profile
spring:
  profiles: tui
logging:
  file:
    name: dcm-tui.log
  pattern:
    console: ""  # Disable console logging
```

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Lanterna API changes | Low | Medium | Pin version, integration tests |
| Terminal compatibility issues | Medium | Low | Test on common terminals (xterm, iTerm, PuTTY) |
| User adoption resistance | Medium | Low | Keep web UI primary, TUI optional |
| Maintenance burden | Medium | Medium | Limit scope, share logic with web |
| LGPL license concerns | Low | Low | Keep as separate optional module |

---

## Effort Estimation

| Phase | Scope | Estimate |
|-------|-------|----------|
| **Phase 1: Foundation** | Module setup, Kotlin DSL wrapper, auth flow | 3-4 stories |
| **Phase 2: Core Screens** | Approval queue, health dashboard | 3-4 stories |
| **Phase 3: Extended** | Audit viewer, export, filtering | 3-4 stories |
| **Total** | Full TUI implementation | ~10 stories |

**Recommended Timeline:** Post-MVP (after Epic 5), as optional enhancement.

---

## Recommendation

### Proceed with Lanterna-based TUI

1. **Create `dcm-tui` module** as optional adapter
2. **Build Kotlin DSL wrapper** for cleaner Lanterna usage
3. **Implement Tier 1 features first** (approval queue, health dashboard)
4. **Evaluate adoption** before investing in Tier 2/3

### Success Criteria

| Metric | Target |
|--------|--------|
| Approval action time | <3 seconds (keyboard only) |
| Terminal compatibility | Works on xterm, iTerm2, Windows Terminal, PuTTY |
| Adoption rate | 20%+ of admin users try TUI |
| Feature coverage | 100% of Tier 1, 50% of Tier 2 |

---

## References

- [Lanterna GitHub](https://github.com/mabe02/lanterna)
- [Lanterna Wiki](https://github.com/mabe02/lanterna/wiki)
- [Kotter GitHub](https://github.com/varabyte/kotter)
- [Jexer Homepage](https://jexer.sourceforge.io/)
- [Spring Shell](https://spring.io/projects/spring-shell)
