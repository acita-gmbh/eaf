# Story 2.12: Email Notifications

Status: ready-for-dev

## Story

As a **user** or **admin**,
I want email notifications for request status changes,
so that I stay informed without checking the portal.

## Acceptance Criteria

**Scenario 1: New Request Notification (to Admins)**
**Given** a user submits a VM request
**When** `VmRequestCreated` event is persisted
**Then** an email is sent to the tenant's configured admin address (or all admins)
**And** Subject: "[DVMM] New VM Request: {vmName}"
**And** Body contains: Requester Name, VM Name, Project, Size, Justification
**And** Body includes a link to the Admin Approval Queue

**Scenario 2: Approval Notification (to Requester)**
**Given** an admin approves a request
**When** `VmRequestApproved` event is persisted
**Then** an email is sent to the requester's email address
**And** Subject: "[DVMM] Request Approved: {vmName}"
**And** Body contains: Approval confirmation, Next steps (Provisioning started)

**Scenario 3: Rejection Notification (to Requester)**
**Given** an admin rejects a request
**When** `VmRequestRejected` event is persisted
**Then** an email is sent to the requester's email address
**And** Subject: "[DVMM] Request Rejected: {vmName}"
**And** Body contains: Rejection reason, Link to request details

**Scenario 4: Async Execution & Resilience**
**Given** the email server is slow or temporarily unavailable
**When** an event occurs
**Then** the request flow is NOT blocked (email sent asynchronously)
**And** failure to send email does NOT roll back the transaction
**And** failed emails are logged as warnings (fire-and-forget for MVP, or retry via outbox)

**Scenario 5: Configuration (SMTP)**
**Given** a tenant has configured SMTP settings
**When** emails are sent
**Then** the tenant's specific SMTP server/credentials are used
**And** if no tenant config exists, system default SMTP is used (or email suppressed with warning)

## Tasks / Subtasks

- [ ] **Framework Layer (eaf-notifications)**
  - [ ] **Create Module:** `eaf/eaf-notifications` with `build.gradle.kts` (dependencies: `spring-boot-starter-mail`, `thymeleaf-spring6`).
  - [ ] **Define Interfaces:**
    - `NotificationService`: `suspend fun sendEmail(to: String, subject: String, template: String, variables: Map<String, Any>)`
    - `NotificationProvider`: Abstraction for SMTP vs other providers.
  - [ ] **Implement SMTP Provider:** `SmtpNotificationProvider` using `JavaMailSender`.
  - [ ] **Implement Template Engine:** `ThymeleafTemplateEngine` for HTML emails.
  - [ ] **Configuration:** `EafNotificationAutoConfiguration` to wire beans.

- [ ] **Auth Layer (eaf-auth)**
  - [ ] **User Lookup:** Add `UserDirectory` interface to `eaf-auth` for looking up user details (email) by `UserId` (service-to-service).
  - [ ] **Keycloak Implementation:** Implement `KeycloakUserDirectory` in `eaf-auth-keycloak` using Keycloak Service Client (admin-cli behavior).
    - *Note:* Requires Keycloak Admin Client dependency.

- [ ] **Domain Layer (dvmm-domain)**
  - [ ] **Tenant Settings:** Create `TenantSettingsAggregate` (or similar) to store SMTP configuration (Host, Port, User, Password - encrypted).
    - *Decision:* For MVP, can we use application properties `spring.mail.*` as system default and skip per-tenant SMTP?
    - *Refinement:* Use system default SMTP for MVP to reduce scope, but ensure `NotificationService` accepts `TenantId` for future per-tenant config.

- [ ] **Application Layer (dvmm-application)**
  - [ ] **Event Listener:** Create `VmRequestNotificationListener` in `dvmm-application`.
    - Listen to: `VmRequestCreated`, `VmRequestApproved`, `VmRequestRejected`.
    - Annotate with `@Async` or use CoroutineScope (ApplicationScope).
  - [ ] **Data Gathering:**
    - `VmRequestCreated`: Fetch Admin Email (from config/properties) + Requester Name (via `UserDirectory`).
    - `Approved/Rejected`: Fetch Requester Email (via `UserDirectory` using `requesterId` from event).
  - [ ] **Send Logic:** Call `NotificationService.sendEmail`.

- [ ] **Resources (dvmm-app)**
  - [ ] **Templates:** Create Thymeleaf templates in `src/main/resources/templates/mail/`:
    - `vm-request-created.html`
    - `vm-request-approved.html`
    - `vm-request-rejected.html`
  - [ ] **Styling:** Simple CSS inlining (Tech Teal branding).

- [ ] **Testing**
  - [ ] **Unit Tests:** Test `VmRequestNotificationListener` (mock NotificationService).
  - [ ] **Integration Tests:** Test `SmtpNotificationProvider` with GreenMail (Testcontainers).
  - [ ] **E2E:** Verify emails are sent (using MailHog/GreenMail API in tests).

## Dev Notes

### eaf-notifications Module Structure

```kotlin
interface NotificationService {
    suspend fun sendEmail(
        tenantId: TenantId, // For future tenant-specific config
        recipient: EmailAddress,
        subject: String,
        templateName: String,
        context: Map<String, Any>
    ): Result<Unit, NotificationError>
}
```

### User Lookup (eaf-auth)

To send emails to requesters, we need their email address. The Event contains `requesterId` (UUID).
We need a way to resolve `UserId` -> `Email`.

```kotlin
interface UserDirectory {
    suspend fun getUser(userId: UserId): UserInfo?
}
```
In `eaf-auth-keycloak`, this implementation needs a Keycloak client with `view-users` role.

**MVP Simplification:**
If Keycloak Admin lookup is too complex for this story:
1.  **Option A:** Include `email` in the Domain Event (User info is available in Command Handler via Token).
    - *Pros:* No lookup needed, decoupling.
    - *Cons:* PII in Event Store (Crypto-shredding concern?). Architecture says "Personal data encrypted".
    - *Decision:* **Option A is preferred for Event Sourcing**. Add `requesterEmail` to `VmRequestCreated` event. It's immutable at point of creation.
    - For `Approved`/`Rejected`, we rely on the Aggregate state? Or carry it forward?
    - Actually, `VmRequestAggregate` state should have the email.
    - But the Event Listener receives the *Event*.
    - If `VmRequestCreated` has email, we can use it.
    - For `Approved`, we don't want to look up the Aggregate if possible (Listener receives Event).
    - *Refined Plan:* Add `requesterEmail` to `VmRequestAggregate` state (from Create command).
    - Add `requesterEmail` to `VmRequestApproved` / `VmRequestRejected` events (denormalized).
    - **WAIT**: `VmRequestApproved/Rejected` events were defined in Story 2.11. Do they have email?
    - Checked Story 2.11: `VmRequestApproved` has `vmName`, `projectId`, `requesterId`. **NO EMAIL**.
    - **Action:** modifying the events in 2.11 (already done) is hard if they are released. But we are in MVP/Dev.
    - **REQUIRED CHANGE:** Update `VmRequestApproved` and `VmRequestRejected` events (defined in Story 2.11 context) to include `requesterEmail: String`.
    - **REQUIRED CHANGE:** Ensure `VmRequestCreated` event also includes `requesterEmail: String`.
    - This avoids the complex `UserDirectory` lookup and Keycloak Admin dependency for now.

### Security / PII

- Email addresses in events are PII.
- GDPR: If we store email in events, we need crypto-shredding support (Epic 5).
- For now (MVP), plain text is acceptable, but note technical debt tag `[GDPR-DEBT]`.

### Async Execution

Use Spring's `@Async` or (better) Kotlin Coroutine with `CoroutineScope(SupervisorJob() + Dispatchers.IO)`.
Do NOT block the command handler transaction.

```kotlin
@Component
class VmRequestNotificationListener(
    private val notificationService: NotificationService,
    @Qualifier("applicationScope") private val externalScope: CoroutineScope
) {
    @EventListener
    fun on(event: VmRequestCreated) {
        externalScope.launch {
            notificationService.sendEmail(...)
        }
    }
}
```

### Project Structure Notes

- **New Module:** `eaf/eaf-notifications`
- **Templates:** `dvmm/dvmm-app/src/main/resources/templates/email/`

### References

- [Spring Boot Email Guide](https://spring.io/guides/gs/sending-email/)
- [Thymeleaf Email Templates](https://www.thymeleaf.org/doc/articles/springmail.html)

## Backlog / Follow-up Items

- [ ] **Project Name Resolution**: Email notifications currently display project UUID instead of human-readable project name (e.g., `projectName = aggregate.projectId.value.toString()`). This is an intentional MVP tradeoff. Future enhancement should resolve project names via a `ProjectDirectory` interface similar to `UserDirectory`. See code comments: "MVP: Project name resolved at query time".

## Dev Agent Record

### Context Reference

- `docs/epics.md` (Epics definition)
- `docs/sprint-artifacts/2-11-approve-reject-actions.md` (Events definition)
- `docs/architecture.md` (Module structure)

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List
