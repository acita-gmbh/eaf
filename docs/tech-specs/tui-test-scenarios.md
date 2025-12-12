# TUI Admin Interface - Test Scenarios

**Version:** 1.0
**Date:** 2025-11-29
**Related:** [TUI Tech Spec](./tui-admin-interface.md) | [ADR-010](../adr/ADR-010-tui-grpc-unix-socket.md)

---

## Overview

This document defines test scenarios for the DCM TUI Admin Interface, mapping to Functional Requirements (TUI-FR) and Non-Functional Requirements (TUI-NFR) from the tech spec.

---

## Test Scenario Categories

| Category | Count | Priority |
|----------|-------|----------|
| Authentication | 8 | Critical |
| Approval Workflow | 10 | Critical |
| Real-Time Streaming | 6 | High |
| Health Dashboard | 5 | Medium |
| Audit Log | 4 | Medium |
| Error Handling | 7 | High |
| Performance | 5 | High |
| Accessibility | 4 | Medium |

---

## 1. Authentication Scenarios

### TS-AUTH-01: Unix User Auto-Login

**Covers:** TUI-FR8

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | SSH as mapped user `admin` | TUI launches |
| 2 | TUI connects to Unix socket | Session established |
| 3 | Server reads SO_PEERCRED | Unix UID extracted |
| 4 | UID mapped via config | User identified as `admin@example.com` |
| 5 | Main dashboard displayed | Header shows user email |

**Verification:**
- No login prompt shown
- Correct tenant context applied
- Session token issued

---

### TS-AUTH-02: Unix User Not Mapped

**Covers:** TUI-FR8, TUI-FR9

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | SSH as unmapped user `developer` | TUI launches |
| 2 | Unix UID extracted | No mapping found |
| 3 | Falls through to token auth | Token file checked |
| 4 | No token file exists | Login screen displayed |
| 5 | Enter credentials | Interactive login |

---

### TS-AUTH-03: Token File Authentication

**Covers:** TUI-FR9

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create `~/.config/dcm-tui/token` | Valid JWT token |
| 2 | Launch TUI | Token file read |
| 3 | Token validated | Session established |
| 4 | Main dashboard displayed | No login prompt |

**Verification:**
- Token expiration handled
- Invalid token shows error

---

### TS-AUTH-04: CLI Token Argument

**Covers:** TUI-FR9

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run `dcm-tui --token=eyJ...` | Token parsed from CLI |
| 2 | Token validated with server | AuthResponse received |
| 3 | Session established | Dashboard displayed |

---

### TS-AUTH-05: Session Expiration

**Covers:** TUI-FR9

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Authenticated session active | User on dashboard |
| 2 | Session timeout (4h) | gRPC call returns UNAUTHENTICATED |
| 3 | TUI detects expiration | "Session expired" message |
| 4 | Login screen displayed | User re-authenticates |

---

### TS-AUTH-06: Socket Permission Denied

**Covers:** TUI-FR8

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | User not in `dcm-admins` group | Launch TUI |
| 2 | Connect to socket | Permission denied |
| 3 | Error displayed | "Access denied. Add user to dcm-admins group." |

---

### TS-AUTH-07: Socket Not Found

**Covers:** Error Handling

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | dcm-app not running | Socket file absent |
| 2 | Launch TUI | Connection attempt fails |
| 3 | Error displayed | "Cannot connect to DCM. Is the service running?" |
| 4 | Retry option | Press R to retry |

---

### TS-AUTH-08: Concurrent Sessions

**Covers:** TUI-NFR6

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Open 10 SSH sessions | 10 TUI instances |
| 2 | All authenticate | 10 sessions established |
| 3 | All view approval queue | Independent views |
| 4 | Session 11 connects | Either allowed or "Max sessions reached" |

---

## 2. Approval Workflow Scenarios

### TS-APPR-01: View Pending Approvals

**Covers:** TUI-FR1

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to Approval Queue | Screen displays |
| 2 | Table shows pending requests | Columns: ID, VM Name, Size, Requester |
| 3 | First row selected | Detail panel shows request info |
| 4 | Arrow keys navigate | Selection moves |

**Verification:**
- Only pending status requests shown
- Correct tenant isolation

---

### TS-APPR-02: Approve Request

**Covers:** TUI-FR2

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Select request REQ-0042 | Row highlighted |
| 2 | Press `A` | Confirmation: "Approve REQ-0042?" |
| 3 | Press Enter to confirm | ApproveCommand sent via gRPC |
| 4 | Server processes | CommandResult(success=true) |
| 5 | Request removed from list | Success message displayed |
| 6 | Event persisted | VmRequestApproved in event store |

---

### TS-APPR-03: Reject Request with Reason

**Covers:** TUI-FR3

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Select request REQ-0043 | Row highlighted |
| 2 | Press `R` | Rejection dialog opens |
| 3 | Empty reason field | Submit disabled |
| 4 | Enter reason (< 10 chars) | Validation error shown |
| 5 | Enter valid reason | Submit enabled |
| 6 | Press Enter | RejectCommand sent |
| 7 | Request removed from list | Status: Rejected |

---

### TS-APPR-04: View Request Details

**Covers:** TUI-FR5

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Select request | Detail panel visible |
| 2 | Press Enter | Full detail view opens |
| 3 | All fields displayed | Name, Size, Specs, Justification |
| 4 | Press Esc | Return to list |

---

### TS-APPR-05: Concurrent Approval Conflict

**Covers:** Error Handling

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Admin A selects REQ-0042 | Viewing request |
| 2 | Admin B approves REQ-0042 | Request approved |
| 3 | Admin A presses `A` | ApproveCommand sent |
| 4 | Server rejects | "Request already processed" |
| 5 | List refreshes | REQ-0042 removed |

---

### TS-APPR-06: Empty Approval Queue

**Covers:** TUI-FR1

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | No pending requests | Navigate to queue |
| 2 | Empty state displayed | "No pending requests" |
| 3 | Guidance shown | "New requests will appear automatically" |

---

### TS-APPR-07: Pagination

**Covers:** TUI-FR1

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | 50 pending requests | Page 1 shows 20 |
| 2 | Press PgDn | Page 2 (items 21-40) |
| 3 | Press PgDn | Page 3 (items 41-50) |
| 4 | Press PgUp | Back to page 2 |

---

### TS-APPR-08: Keyboard Shortcuts Work

**Covers:** TUI-FR2, TUI-FR3

| Key | Context | Expected Action |
|-----|---------|-----------------|
| `A` | Request selected | Open approve dialog |
| `R` | Request selected | Open reject dialog |
| `Enter` | Request selected | Open detail view |
| `↑↓` | List | Navigate rows |
| `Esc` | Any dialog | Cancel/close |
| `Q` | Main screen | Quit application |

---

### TS-APPR-09: Filter by Project (Future)

**Covers:** Future Enhancement

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Press `/` | Filter input opens |
| 2 | Select project | List filtered |
| 3 | Press Esc | Filter cleared |

---

### TS-APPR-10: Request Age Highlighting

**Covers:** TUI-FR1

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Request pending > 24h | Row shows amber indicator |
| 2 | Request pending > 48h | Row shows red indicator |
| 3 | Tooltip explains | "Waiting for 52 hours" |

---

## 3. Real-Time Streaming Scenarios

### TS-STREAM-01: New Request Appears

**Covers:** TUI-FR10, TUI-NFR3

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Viewing approval queue | Stream active |
| 2 | User submits new request | VmRequestCreated event |
| 3 | ApprovalEvent pushed | Type: SUBMITTED |
| 4 | Queue updates | New row appears (< 100ms) |
| 5 | Count badge updates | +1 pending |

---

### TS-STREAM-02: Request Approved by Another Admin

**Covers:** TUI-FR10

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Admin A viewing queue | REQ-0042 visible |
| 2 | Admin B approves REQ-0042 | VmRequestApproved event |
| 3 | ApprovalEvent pushed | Type: APPROVED |
| 4 | Admin A's queue updates | REQ-0042 removed |
| 5 | Notification shown | "REQ-0042 approved by admin-b@..." |

---

### TS-STREAM-03: Stream Disconnection and Reconnect

**Covers:** TUI-FR10

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Stream active | ● Live indicator green |
| 2 | Network blip occurs | Stream disconnected |
| 3 | Indicator changes | ○ Reconnecting (yellow) |
| 4 | Exponential backoff | Retry at 1s, 2s, 4s, 8s |
| 5 | Connection restored | ● Live indicator green |
| 6 | Missed events fetched | Queue synchronized |

---

### TS-STREAM-04: Health Updates Streaming

**Covers:** TUI-FR4, TUI-NFR3

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | View health dashboard | SubscribeHealth active |
| 2 | Wait 10 seconds | HealthUpdate received |
| 3 | All services show status | Latency, status, timestamp |
| 4 | Service degrades | Status changes to ○ Degraded |
| 5 | UI updates immediately | < 100ms from event |

---

### TS-STREAM-05: Stream Keeps Connection Alive

**Covers:** TUI-FR10

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Idle for 30 minutes | No user interaction |
| 2 | Stream remains active | Keep-alive pings |
| 3 | New request arrives | Appears in queue |

---

### TS-STREAM-06: Multiple Concurrent Streams

**Covers:** TUI-NFR6

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | 10 TUI sessions active | 10 SubscribeApprovals streams |
| 2 | New request submitted | Event pushed to all 10 |
| 3 | All queues update | Within 100ms |

---

## 4. Health Dashboard Scenarios

### TS-HEALTH-01: All Services Healthy

**Covers:** TUI-FR4

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to Health | Dashboard displayed |
| 2 | All services listed | VMware, Keycloak, DB, Email |
| 3 | All show ● Healthy | Green indicators |
| 4 | Latencies displayed | e.g., "42ms" |

---

### TS-HEALTH-02: Service Degraded

**Covers:** TUI-FR4

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Redis latency > 100ms | HealthUpdate pushed |
| 2 | Status changes | ○ Degraded (yellow) |
| 3 | Warning displayed | "High latency detected" |
| 4 | Other services unaffected | Still ● Healthy |

---

### TS-HEALTH-03: Service Unhealthy

**Covers:** TUI-FR4

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | VMware connection fails | HealthUpdate pushed |
| 2 | Status changes | ✖ Unhealthy (red) |
| 3 | Error message shown | "Connection refused" |
| 4 | Last healthy timestamp | "Last healthy: 5 min ago" |

---

### TS-HEALTH-04: Manual Refresh

**Covers:** TUI-FR4

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | On health screen | Press `R` |
| 2 | Immediate health check | Spinner shown |
| 3 | Results displayed | Fresh latencies |

---

### TS-HEALTH-05: Uptime Display

**Covers:** TUI-FR4

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | View health dashboard | Uptime section visible |
| 2 | Shows percentages | Today: 100%, Week: 99.8% |

---

## 5. Audit Log Scenarios

### TS-AUDIT-01: View Audit Log

**Covers:** TUI-FR6

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to Audit Log | Table displayed |
| 2 | Entries shown | Timestamp, Event, Actor, Entity |
| 3 | Sorted by timestamp | Newest first |

---

### TS-AUDIT-02: Paginate Audit Log

**Covers:** TUI-FR6

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | 142 audit entries | Page 1 of 15 shown |
| 2 | Press PgDn | Page 2 displayed |
| 3 | Footer updates | "Page 2 of 15" |

---

### TS-AUDIT-03: Filter by Event Type

**Covers:** TUI-FR6

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Press `/` | Filter opens |
| 2 | Select "REQUEST_APPROVED" | Filter applied |
| 3 | Only approvals shown | Count updates |

---

### TS-AUDIT-04: Export to CSV

**Covers:** TUI-FR7

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Press `E` | Export dialog |
| 2 | Confirm export | CSV generated |
| 3 | File saved | `~/dcm-audit-export-2025-11-29.csv` |
| 4 | Success message | "Exported 142 entries" |

---

## 6. Error Handling Scenarios

### TS-ERR-01: gRPC Unavailable

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | dcm-app crashes | Socket closes |
| 2 | Next gRPC call | UNAVAILABLE error |
| 3 | Error displayed | "Server unavailable. Retrying..." |
| 4 | Reconnect attempts | With backoff |

---

### TS-ERR-02: Command Fails

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Approve request | Command sent |
| 2 | Server returns error | CommandResult(success=false) |
| 3 | Error displayed | error_message shown |
| 4 | Request remains in list | User can retry |

---

### TS-ERR-03: Network Timeout

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Slow network | Command sent |
| 2 | 30s timeout | Request times out |
| 3 | Error displayed | "Request timed out" |
| 4 | Retry option | Press R to retry |

---

### TS-ERR-04: Invalid Response

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Server sends malformed data | Protobuf parse fails |
| 2 | Error logged | With correlation ID |
| 3 | Graceful degradation | "Unexpected response" |

---

### TS-ERR-05: Terminal Resize

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Resize terminal window | SIGWINCH received |
| 2 | Lanterna redraws | UI adapts to new size |
| 3 | No data loss | State preserved |

---

### TS-ERR-06: Ctrl+C Handling

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Press Ctrl+C | SIGINT received |
| 2 | Graceful shutdown | Streams closed |
| 3 | Session ended | "Goodbye" message |

---

### TS-ERR-07: Corrupted Config File

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Invalid YAML in config | Launch TUI |
| 2 | Config parse fails | Clear error message |
| 3 | Shows example config | How to fix |

---

## 7. Performance Scenarios

### TS-PERF-01: Startup Time

**Covers:** TUI-NFR1

| Measurement | Target | Method |
|-------------|--------|--------|
| Cold start to login screen | < 2s | Stopwatch from launch |
| JVM startup | < 1s | JFR profiling |
| gRPC channel init | < 500ms | Trace logging |

---

### TS-PERF-02: gRPC Latency

**Covers:** TUI-NFR2

| Measurement | Target | Method |
|-------------|--------|--------|
| Unix socket RTT | < 1ms | gRPC interceptor timing |
| ApproveCommand latency | < 50ms | End-to-end |
| Query response | < 100ms | For 100 items |

---

### TS-PERF-03: Event Propagation

**Covers:** TUI-NFR3

| Measurement | Target | Method |
|-------------|--------|--------|
| Event to UI update | < 100ms | Timestamp comparison |
| Stream push latency | < 10ms | Server-side timing |
| UI render time | < 50ms | Lanterna profiling |

---

### TS-PERF-04: Memory Usage

**Covers:** TUI-NFR4

| Measurement | Target | Method |
|-------------|--------|--------|
| JVM heap at steady state | < 128MB | jcmd/jstat |
| After 1 hour usage | < 128MB | No memory leak |
| Peak during large list | < 200MB | Acceptable spike |

---

### TS-PERF-05: Concurrent Load

**Covers:** TUI-NFR6

| Measurement | Target | Method |
|-------------|--------|--------|
| 10 concurrent sessions | All responsive | Load test |
| Event broadcast latency | < 100ms to all | Timing |
| Server resource usage | < 500MB heap | JMX monitoring |

---

## 8. Accessibility Scenarios

### TS-A11Y-01: Keyboard-Only Navigation

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Launch TUI | No mouse required |
| 2 | Navigate all screens | Tab, arrows, Enter work |
| 3 | Complete approval | Keyboard only |

---

### TS-A11Y-02: Color Contrast

| Element | Foreground | Background | Ratio |
|---------|------------|------------|-------|
| Normal text | White | Black | 21:1 |
| Selected row | Black | Cyan | 8.5:1 |
| Error text | Red | Black | 4.5:1 |
| Status indicators | Green/Yellow/Red | Black | > 4.5:1 |

---

### TS-A11Y-03: Status Without Color

| Status | Color | Non-Color Indicator |
|--------|-------|---------------------|
| Healthy | Green | ● (filled circle) |
| Degraded | Yellow | ○ (empty circle) |
| Unhealthy | Red | ✖ (X mark) |
| Pending | Amber | ⧖ (hourglass) |
| Approved | Green | ✓ (checkmark) |
| Rejected | Red | ✗ (X) |

---

### TS-A11Y-04: Screen Reader Compatibility

| Feature | Implementation |
|---------|----------------|
| Focus announcements | Lanterna accessibility labels |
| Table navigation | Row/column announcement |
| Dialog focus trap | Focus stays in dialog |
| Status changes | Announced when changed |

---

## Test Matrix: Terminal Compatibility

**Covers:** TUI-NFR5

| Terminal | OS | Status | Notes |
|----------|-----|--------|-------|
| xterm | Linux | Must Pass | Reference terminal |
| iTerm2 | macOS | Must Pass | Common dev terminal |
| GNOME Terminal | Linux | Must Pass | Ubuntu default |
| Terminal.app | macOS | Should Pass | macOS default |
| Konsole | Linux | Should Pass | KDE default |
| tmux | Linux/macOS | Should Pass | Multiplexer |
| PuTTY | Windows (SSH) | Should Pass | Remote SSH |

---

## Traceability Matrix

| Test Scenario | FR/NFR | Story |
|---------------|--------|-------|
| TS-AUTH-01 | TUI-FR8 | 6.7 |
| TS-AUTH-02 | TUI-FR8, TUI-FR9 | 6.7, 6.8 |
| TS-AUTH-03 | TUI-FR9 | 6.8 |
| TS-APPR-01 | TUI-FR1 | 6.1, 6.2 |
| TS-APPR-02 | TUI-FR2 | 6.2 |
| TS-APPR-03 | TUI-FR3 | 6.2 |
| TS-STREAM-01 | TUI-FR10, TUI-NFR3 | 6.3 |
| TS-HEALTH-01 | TUI-FR4 | 6.5 |
| TS-AUDIT-01 | TUI-FR6 | 6.6 |
| TS-PERF-01 | TUI-NFR1 | 6.1 |
| TS-PERF-02 | TUI-NFR2 | 6.1 |

---

_Last Updated: 2025-11-29_
