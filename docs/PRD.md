# EAF Product Requirements Document (PRD)

**Author:** Wall-E
**Date:** 2025-10-31
**Project Level:** 2
**Target Scale:** Medium - Enterprise Framework for internal development teams

---

## Goals and Background Context

### Goals

1. **Replace Legacy DCA Framework** - Eliminate the primary technical and business bottleneck blocking developer productivity, product evolution, and new revenue opportunities

2. **Deliver Superior Developer Experience** - Achieve <5% framework overhead (from 25%), <1 month onboarding time (from 6+ months), and enable 3-day aggregate development through scaffolding CLI and comprehensive documentation

3. **Enable Enterprise Market Expansion** - Achieve audit-ready security compliance (OWASP ASVS L1/L2, ISO 27001/NIS2) unlocking previously blocked customer acquisition for ZEWSSP and DPCM products

### Background Context

Axians' legacy DCA framework (2016) has evolved from a technical asset into a critical business bottleneck. The framework consumes 25% of developer effort in maintenance overhead, requires 6+ months for new developer onboarding, and blocks revenue opportunities by preventing product certifications required for enterprise customers. Key products like ZEWSSP cannot acquire new customers due to missing security certifications (ISO 27001, NIS2, OWASP ASVS), while the complete absence of documentation and tests creates a prohibitive barrier to team scaling.

The Enterprise Application Framework (EAF) v1.0 addresses these challenges through a production-validated architecture combining Hexagonal Architecture, CQRS/Event Sourcing (Axon Framework), and Spring Modulith with programmatic boundary enforcement. Built on Kotlin 2.2.21 and Spring Boot 3.5.7, EAF delivers a "batteries-included" development platform with one-command setup, scaffolding CLI for code generation, enterprise-grade security (10-layer JWT validation, 3-layer multi-tenancy), comprehensive observability, and Constitutional Test-Driven Development. The framework has been validated through extensive prototype development, proving the architecture is learnable, the patterns are effective, and the developer experience goals are achievable.

---

## Requirements

### Functional Requirements

**FR001: Development Environment Setup and Infrastructure**
The system shall provide one-command initialization of complete local development stack (PostgreSQL, Keycloak, Redis, Prometheus, Grafana) with automatic migrations and seed data, plus Gradle multi-module monorepo structure with Spring Modulith boundary enforcement and Docker Compose templates.

**FR002: Code Generation with Bootstrap Fallbacks**
The system shall provide scaffolding CLI for generating modules, aggregates, API endpoints, projections, and UI components, plus manual bootstrap templates as fallback for initial setup without CLI dependencies.

**FR003: Event Store with Integrity and Performance**
The system shall provide PostgreSQL-based event store with partitioning, BRIN indexes, snapshots, jOOQ projections, automatic data integrity validation, performance monitoring with forecasting and early warnings, and hot migration capabilities for zero-downtime migration to alternative stores.

**FR004: Multi-Tenancy with Isolation and Quotas**
The system shall enforce three-layer tenant isolation (JWT extraction, service validation, PostgreSQL RLS) with continuous monitoring, security audit logging, cross-tenant leak detection, and per-tenant resource quotas with automatic throttling.

**FR005: Observability with Performance Limits**
The system shall provide structured JSON logging, Prometheus metrics, and OpenTelemetry tracing with automatic tenant/trace correlation, plus enforced performance limits including log rotation, intelligent sampling, and <1% overhead guarantees.

**FR006: Authentication, Security, and Compliance**
The system shall provide IdP abstraction layer supporting multiple providers (Keycloak, Auth0, Okta) with 10-layer JWT validation, comprehensive security audit trails, automated GDPR/privacy compliance (crypto-shredding, PII masking), and OWASP dependency scanning.

**FR007: Workflow Orchestration with Recovery**
The system shall integrate Flowable BPMN with bidirectional Axon bridge, Ansible adapter, compensating transactions, plus workflow recovery tools, dead letter queues, and debugging utilities.

**FR008: Quality Gates with Configurable Profiles**
The system shall enforce automated quality gates (ktlint, Detekt, Konsist, Pitest, Testcontainers) with configurable profiles (fast <30s, standard <3min, thorough <15min), flaky test detection, and clear failure diagnostics.

**FR010: Hexagonal Architecture with Swappable Adapters**
The system shall implement hexagonal architecture where infrastructure components are swappable adapters isolated from domain logic.

**FR011: Fast Feedback and Performance Monitoring**
The system shall provide fast feedback loops (full suite <3min, unit tests <30s, build <2min) and enforce performance SLAs (API p95 <200ms, event lag <10s) with built-in budgets and automated regression testing.

**FR012: Framework Migration and Multi-Version Support**
The system shall provide versioned migration scripts, automated upgrade tooling, support for multiple framework versions concurrently, version compatibility matrix, and automated breaking change detection.

**FR013: Event Sourcing Debugging Capabilities**
The system shall provide event replay, time-travel debugging, aggregate state reconstruction, and visual event stream inspection.

**FR014: Data Consistency and Concurrency Control**
The system shall enforce strong consistency through optimistic locking, conflict resolution strategies, and eventual consistency for projections with <10s lag target.

**FR015: Comprehensive Onboarding and Learning**
The system shall provide progressive complexity learning paths (minimal → standard → advanced), Golden Path documentation, interactive tutorials with validation, troubleshooting guides, aggregate correctness checks, architecture Q&A tools, and scalable cohort-based onboarding for 10-50 developers with peer support.

**FR016: Framework Extension and Customization**
The system shall provide documented extension points for custom behaviors including hooks, interceptors, validation logic, and plugin architecture.

**FR018: Error Recovery and Dependency Resilience**
The system shall provide circuit breakers, retry strategies with exponential backoff, graceful degradation, health checks, and specific fallbacks for Keycloak/Redis/PostgreSQL/Prometheus failures.

**FR025: Local Development Workflow Support**
The system shall provide hot reload, breakpoint debugging in event handlers, local feature flags, and pre-commit validation with automated fixes.

**FR027: Business Metrics and Analytics**
The system shall provide custom metric API for KPI tracking, feature adoption analytics, A/B testing support, and business dashboard integration.

**FR028: Release Management and Feature Control**
The system shall provide feature flag system, canary deployment support with auto-rollback, release versioning automation, and deployment health checks.

**FR030: Production Operations Support**
The system shall provide automated deployment strategies (blue/green, rolling), event store backup/restore, disaster recovery procedures, pre-configured alerts, and capacity planning tools.

### Non-Functional Requirements

**NFR001: Performance**
The system shall meet defined performance targets including API response time p95 <200ms, event processing lag <10s, full test suite execution <15min, and developer feedback loop <3min, ensuring rapid iteration and production responsiveness.

**NFR002: Security and Compliance**
The system shall achieve audit-ready security compliance including OWASP ASVS 5.0 (100% Level 1, 50% Level 2), support for ISO 27001/NIS2 certification requirements, automated OWASP dependency scanning in CI/CD with zero critical vulnerabilities in production, and comprehensive security documentation for customer due diligence.

**NFR003: Developer Experience**
The system shall reduce developer overhead to <5% (from 25%), achieve new developer time-to-productivity <3 days for simple aggregates and <3 months for first production deployment, maintain Developer Net Promoter Score (dNPS) ≥+50, and enable code generation that passes all quality gates immediately.

---

## User Journeys

### Journey 1: Developer Creates First CQRS Aggregate with EAF

**Persona:** Majlinda (Senior Developer, new to Kotlin/Spring/CQRS, transitioning from Node.js/React)

**Goal:** Build and deploy a fully functional, tested domain aggregate within 3 days

**Day 1 - Setup and Exploration (2-3 hours)**
1. Clone EAF repository and run `./scripts/init-dev.sh`
2. System automatically starts all services (PostgreSQL, Keycloak, Redis, Prometheus, Grafana)
3. Health checks confirm all services running
4. Explore reference Widget example in documentation
5. Run existing tests to verify setup (`./gradlew test`) - passes in <3 minutes
6. Explore project structure using Golden Path documentation
7. Use CLI to generate first aggregate: `eaf scaffold aggregate Order`
8. CLI generates complete CQRS vertical slice (aggregate, commands, events, handlers, projections)
9. Generated code passes all quality gates immediately (ktlint, Detekt)
10. Review generated code with inline documentation
11. Customize aggregate business logic (order validation, state transitions)

**Day 2 - Testing and Integration (3-4 hours)**
12. Write unit tests using generated test template and Nullable Pattern
13. Unit tests execute in <30 seconds, providing fast feedback
14. Add integration tests with Testcontainers (real PostgreSQL, Keycloak)
15. Integration tests pass, validating full CQRS flow with event store
16. Use event debugging tool to inspect event stream and verify state
17. Add API endpoint using `eaf scaffold api-resource Order`
18. Test API via Swagger UI at `/swagger-ui.html`
19. Add tenant context to commands following documentation pattern
20. Tenant isolation tests pass, confirming RLS policies work
21. Test cross-tenant access attempts - correctly blocked
22. Review tenant isolation audit logs in structured JSON format

**Day 3 - Production Readiness (2-3 hours)**
23. Run full test suite including mutation tests - achieve 85%+ coverage
24. All quality gates pass (ktlint, Detekt, Konsist, Pitest)
25. Add custom business metrics for order processing
26. Review Prometheus metrics at `/actuator/prometheus`
27. Create simple BPMN workflow for order approval using Flowable
28. Test workflow integration with Axon bridge
29. Commit code - pre-commit hooks validate quality gates
30. Create pull request - CI/CD pipeline passes (<15 min)
31. Merge to main branch
32. Deploy to staging environment using Docker Compose
33. Verify deployment with health checks and smoke tests
34. **Success:** First production-ready aggregate deployed in <3 days

**Outcome:** Developer successfully builds and deploys a fully functional, tested, multi-tenant CQRS aggregate, meeting MVP success criteria and validating EAF's developer experience goals.

---

## UX Design Principles

1. **Utility First** - Prioritize function, data density, and operational speed (enterprise tool, not marketing site)
2. **Clarity Over Cleverness** - Absolute consistency in patterns, predictable component behavior
3. **Progressive Disclosure** - Hide complex configurations until needed
4. **Accessible by Default** - WCAG 2.1 Level A compliance from inception

---

## User Interface Design Goals

**Platform & Framework:**
- **Primary:** Desktop-focused operator portal (1200px+)
- **Secondary:** Tablet functional (900px+)
- **Framework:** shadcn-admin-kit (react-admin core + shadcn/ui components)
- **Styling:** Tailwind CSS with shadcn design tokens
- **Icons:** Lucide Icons (shadcn standard)

**Component Library:**
- **shadcn/ui components** (Radix UI primitives with Tailwind styling)
- Custom domain components built with shadcn patterns
- React-Admin data provider integration for CRUD operations

**Performance Targets:**
- LCP (Largest Contentful Paint) <2.5s
- INP (Interaction Response) <200ms
- CLS (Cumulative Layout Shift) = 0
- Code-split routes with lazy loading

**Accessibility Requirements:**
- WCAG 2.1 Level A compliance (minimum)
- Full keyboard navigation support
- Alt text for all meaningful visuals
- Programmatically associated form labels
- Respect `prefers-reduced-motion` setting

**Reference:** Complete UI/UX specification exists in prototype repository and will be updated for shadcn-admin-kit in Epic 7.

---

## Epic List

**Epic 1: Foundation & Project Infrastructure**
Establish project foundation with Gradle multi-module structure, Spring Modulith boundary enforcement, Docker Compose development stack, and DDD base classes for all subsequent development.
*Estimated stories: 8-12*

**Epic 2: Walking Skeleton - CQRS/Event Sourcing Core**
Implement complete CQRS/ES vertical slice with PostgreSQL event store, Axon Framework integration, jOOQ projections, REST API foundation, and end-to-end command-to-query flow proving architecture viability.
*Estimated stories: 12-15*

**Epic 3: Authentication & Authorization**
Integrate Keycloak OIDC with 10-layer JWT validation, Spring Security OAuth2 Resource Server, Redis revocation cache, role-based access control, and custom ppc64le Keycloak Docker image for multi-architecture support.
*Estimated stories: 10-12*

**Epic 4: Multi-Tenancy & Data Isolation**
Implement 3-layer tenant isolation (JWT extraction, service validation, PostgreSQL RLS), ThreadLocal context propagation to async event processors, cross-tenant leak detection, and per-tenant resource quotas.
*Estimated stories: 8-10*

**Epic 5: Observability & Monitoring**
Integrate structured JSON logging with context injection (trace_id, tenant_id), Prometheus metrics with Micrometer, OpenTelemetry distributed tracing, PII masking, and performance limit enforcement.
*Estimated stories: 6-8*

**Epic 6: Workflow Orchestration**
Integrate Flowable BPMN engine with bidirectional Axon bridge (commands from BPMN, signals from events), Ansible adapter for legacy migration, compensating transactions, and Dockets pattern template.
*Estimated stories: 8-10*

**Epic 7: Scaffolding CLI & Developer Tooling**
Build code generation CLI with Picocli and Mustache templates for modules, aggregates, API endpoints, projections, and shadcn-admin-kit UI components, ensuring generated code passes all quality gates immediately.
*Estimated stories: 10-12*

**Epic 8: Code Quality & Architectural Alignment**
Systematic resolution of architectural deviations, implementation of LitmusKt concurrency testing, Pitest mutation testing, Git hooks enforcement, and validation of Constitutional TDD compliance across all framework modules.
*Estimated stories: 8-10*

**Epic 9: Golden Path Documentation**
Create comprehensive developer documentation including Getting Started guides, tutorials (simple/standard/production aggregates), How-To guides, reference documentation, and working examples - MUST complete before Epic 10 for Majlinda's onboarding validation.
*Estimated stories: 12-15*

**Epic 10: Reference Application for MVP Validation**
Build complete multi-tenant Widget Management demo application using ONLY framework capabilities, validating all components (CQRS, multi-tenancy, auth, workflow, UI), serving as Majlinda's onboarding validation (<3 days target), and benchmarking Nullables Pattern performance.
*Estimated stories: 10-12*

**Total Estimated Stories:** 92-106 stories across 10 epics

> **Note:** Detailed epic breakdown with full story specifications is available in [epics.md](./epics.md)

---

## Out of Scope

**Deferred to Post-MVP (Phase 2):**
- **Full Dockets Orchestration Engine Parity** - Complex legacy automation engine with dynamic UI generation, visual workflow builder (DAG editor), and all advanced features will not be replicated in MVP. Basic BPMN workflows with Flowable provide core orchestration needs.
- **Observability Dashboards** - Pre-configured Grafana panels and visualization dashboards are deferred. MVP provides metrics collection only; teams can build custom dashboards as needed.
- **AI-Powered Developer Assistant** - LLM-based features for documentation generation, test generation, and architectural Q&A engine are Post-MVP.
- **Automated Project Generation (MCP)** - Capability for an LLM to autonomously use the CLI for project generation is Post-MVP.
- **Automated DCA Migration Tool** - Migrations from DCA to EAF will be manual efforts with framework team support. Automated migration tooling is deferred.
- **shadcn-admin-kit Advanced Features** - Complex UI features beyond basic CRUD (advanced filtering, bulk operations, complex dashboards, relationship management) are deferred. MVP provides foundational operator portal only.

**Long-Term Vision (12-24 months):**
- **Intelligent Developer Partner** - Integrated AI assistant, automated test generation, proactive code review, anomaly detection
- **Cloud-Native Platform Evolution** - Kubernetes operator, multi-region active-active, advanced multi-tenancy with quotas
- **Framework as a Service** - Managed platform offering for external customers
- **Ecosystem Development** - Module marketplace, community contributions, shared components
- **Domain-Specific Variants** - Specialized EAF variants for IoT, Financial Services, Healthcare

**Not Included:**
- Mobile-optimized operator portal UI (desktop and tablet only)
- Real-time collaborative editing features
- Advanced React-Admin features requiring commercial licenses
- Integration with commercial/proprietary tools (FOSS-only constraint)
