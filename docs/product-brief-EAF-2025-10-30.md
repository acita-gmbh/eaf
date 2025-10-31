# Product Brief: Enterprise Application Framework (EAF) v1.0

**Date:** 2025-10-30
**Author:** Wall-E
**Status:** Draft for PM Review
**Version:** 1.0

---

## Executive Summary

The Enterprise Application Framework (EAF) v1.0 is a modern, "batteries-included" development platform designed to replace Axians' legacy DCA framework (2016). Built on a production-validated architecture combining Hexagonal Architecture, CQRS/Event Sourcing, and Spring Modulith, EAF delivers a superior developer experience with one-command project setup, automated code generation via a Scaffolding CLI, and comprehensive security through 10-layer JWT validation and 3-layer tenant isolation.

The framework addresses critical business bottlenecks: the legacy DCA framework consumes 25% of developer effort in maintenance overhead, requires 6+ months for new developer onboarding, and blocks revenue opportunities due to security and architectural limitations that prevent product updates and certifications.

EAF v1.0 targets internal Axians development teams and aims to reduce developer overhead to <5%, achieve developer productivity in <1 month, attain audit-ready security compliance (ASVS 100% L1 / 50% L2, ISO 27001/NIS2), and enable migration of flagship products (ZEWSSP, DPCM) to unlock new enterprise markets.

The technical foundation includes Kotlin 2.2.21, Spring Boot 3.5.7, Axon Framework 4.12.1, PostgreSQL 16.10 as a swappable event store adapter, Keycloak OIDC for authentication, and Flowable BPMN for workflow orchestration. The framework enforces Constitutional Test-Driven Development with Kotest and the innovative "Nullable Design Pattern" for 60%+ faster tests.

---

## Problem Statement

The legacy DCA framework (2016) has evolved from a technical asset into the primary strategic bottleneck for Axians' business, directly impacting development costs, time-to-market, and revenue opportunities.

### Business Impact

From a management and product perspective, the DCA framework causes three critical business failures:

1. **Drastic Inefficiency (Cost):** An estimated **25% of all developer effort** is consumed by framework overhead, maintenance, and implementing workarounds for its brittle, custom-built persistence layer. This directly inflates the cost of all feature development and reduces the velocity of delivering business value.

2. **Prohibitive Onboarding (Velocity):** The framework's complexity and complete lack of documentation or tests create a prohibitive **6+ month onboarding time** for new developers, severely limiting team scalability and velocity. New team members cannot contribute meaningfully to product development for over half a year.

3. **Blocked Business Opportunities (Revenue & Risk):** The framework's inflexibility directly impedes new revenue streams. Key products such as **ZEWSSP** are blocked from acquiring new customers because their underlying DCA platform cannot be updated, secured, or certified to meet modern enterprise requirements (ISO 27001, NIS2, OWASP ASVS).

### Technical Root Causes

These business impacts stem from specific, critical technical failures:

- **No Multi-Tenancy Support:** The legacy framework lacks any architectural support for data isolation between customers, making it unsuitable for modern SaaS deployments.
- **Critical Security Gaps:** No modern JWT validation, no structured security model, and impossible to audit or certify against security standards.
- **Zero Observability:** Insufficient logging, metrics, and monitoring make debugging production issues extremely difficult and time-consuming.
- **Inflexible Monolithic Architecture:** Tightly coupled components make testing challenging, refactoring risky, and iteration slow.
- **Custom Persistence Layer:** A brittle, undocumented custom-built persistence solution that consumes disproportionate maintenance effort and lacks the reliability of industry-standard solutions.

### Urgency

The window for action is closing. Without replacement:
- Current products (DPCM, ZEWSSP) face increasing security vulnerabilities and compliance risks
- Opportunity cost grows as competitors move faster with modern stacks
- Technical debt compounds, making eventual migration exponentially more expensive
- Developer turnover increases due to frustration with outdated tooling

---

## Proposed Solution

The Enterprise Application Framework (EAF) v1.0 is a "batteries-included" development and management environment for Axians developers, built on a production-validated architecture proven through extensive prototype development.

### Core Concept

EAF implements a modern, industry-standard architecture stack:
- **Hexagonal Architecture** (programmatically enforced via Spring Modulith 1.4.4)
- **Domain-Driven Design (DDD)** for clear business logic modeling
- **CQRS/Event Sourcing** via Axon Framework 4.12.1 for scalable, auditable systems
- **Kotlin 2.2.21 / Spring Boot 3.5.7** for type-safe, maintainable code
- **PostgreSQL 16.10** as a swappable event store adapter

This architecture programmatically enforces boundaries and isolates business logic, directly solving the "inflexible" and "untestable" nature of the legacy DCA framework.

### Key Differentiators

EAF will succeed where DCA failed by prioritizing developer experience and verifiable quality:

1. **One-Command Onboarding:** A runnable local development stack (Docker Compose) initialized with a single command (`./scripts/init-dev.sh`) that starts PostgreSQL, Keycloak, Redis, Prometheus, and Grafana with automatic seed data.

2. **Scaffolding CLI (v1):** A powerful code generation tool for rapid domain creation (modules, aggregates, commands, events, API endpoints, React-Admin UI components) that enforces architectural patterns from inception, eliminating boilerplate and ensuring consistency.

3. **Innovative Testing Philosophy:** Adoption of "Constitutional Test-Driven Development" with Integration-First approach (40-50% integration tests), the "Nullable Design Pattern" for 60%+ faster business logic tests, and mandatory real dependencies (Testcontainers) for integration testing. This provides a comprehensive safety net that DCA completely lacked.

4. **Secure by Default:** Pre-configured security core including:
   - 10-layer JWT validation (format, signature RS256, algorithm validation, claims schema, time-based, issuer/audience, revocation, role, user validation, injection detection)
   - 3-layer multi-tenancy model (request filter, service validation, PostgreSQL Row-Level Security)
   - Keycloak OIDC integration for enterprise-grade identity management

5. **Enterprise Workflow Engine:** Flowable BPMN integration replacing the legacy "Dockets" system with an industry-standard workflow engine, providing robust orchestration with compensating transactions and error handling.

6. **Comprehensive Observability:** Structured JSON logging with automatic trace/tenant correlation, Prometheus metrics with Micrometer, and OpenTelemetry distributed tracing - all configured by default.

### Strategic Technical Decision: PostgreSQL as Event Store

The v1.0 persistence layer utilizes **PostgreSQL** as the event store via Axon's native `JdbcEventStorageEngine`. This approach prioritizes:
- **Mature Integration:** Native Axon Framework support with battle-tested reliability
- **Operational Simplicity:** Single database instance for events, projections, and Flowable
- **Risk Mitigation:** Proven technology with clear operational patterns

Critically, the persistence layer is implemented as a **swappable Hexagonal adapter**, ensuring the core domain logic remains isolated. This architectural decision safeguards a low-effort future migration path to streaming solutions (like NATS or commercial Axon Server) when defined performance triggers are met, without requiring a core rewrite.

### Why This Will Succeed

The EAF architecture has been **validated through extensive prototype development** that proved:
- The patterns are learnable and effective for CQRS/ES development
- The Nullable Pattern delivers measurable test performance improvements
- The security model meets enterprise compliance requirements
- The architectural boundaries enforced by Spring Modulith prevent coupling
- One-command setup genuinely works and dramatically reduces onboarding friction

---

## Target Users

### Primary User Segment: Axians Developers

**Profile:** Axians' in-house development team, comprising developers with medium skill levels primarily specialized in Node.js and React technology stacks. These developers are transitioning to a Kotlin/Spring Boot/CQRS architecture and need clear guidance and productivity tools.

**Current Pain Points:**
- Workflow frequently blocked by ambiguity and lack of confidence in the existing DCA framework
- Burdened by brittle persistence layer with no documentation
- Complete absence of a testing safety net leads to fear of making changes
- 6+ month onboarding time before contributing meaningfully to products
- 25% of time consumed by framework overhead rather than business logic

**Goals and Needs:**
- Clear, comprehensive documentation with examples and "golden paths"
- Scaffolding CLI for rapid pattern adoption and boilerplate elimination
- Comprehensive test suite as a safety net for confident refactoring
- <1 month time to productivity on new projects
- Ability to focus on business logic rather than infrastructure concerns

**Success Criteria:** Developers can build and deploy a standard domain aggregate within 3 days using only documentation and CLI, achieving our productivity goals.

---

### Secondary User Segments

#### Product Managers

**Profile:** Responsible for strategic roadmap, feature prioritization, and product backlog management.

**Pain Points:** Development timelines are unpredictable and excessively long due to DCA framework limitations. Prototyping new ideas requires prohibitive investment, preventing rapid validation of market opportunities.

**Goals:** Drastically reduce time-to-market for new features, gain ability to rapidly prototype ideas that can evolve into production-ready MVPs, and achieve predictable delivery timelines.

---

#### Security Team

**Profile:** Responsible for managing technical risk, ensuring compliance, and conducting security reviews and audits.

**Pain Points:** The current DCA framework is a "black box" impossible to formally review or audit. This blocks customer acquisition for opportunities requiring elevated security requirements (ISO 27001, NIS2, OWASP ASVS).

**Goals:** "Secure-by-default" framework with 10-layer JWT validation, 3-layer tenant isolation, automated security scanning in CI/CD, and ability to generate compliance artifacts (SBOM, security documentation, audit trails).

---

#### End Customers

**Profile:** Companies purchasing and using Axians software products (ZEWSSP, DPCM) built on the framework.

**Pain Points:** Experience negatively impacted by bugs, slow performance, outdated UX, and slow pace of feature development stemming from legacy framework limitations. Cannot adopt products due to missing critical capabilities like multi-tenancy.

**Goals:** Stable, high-performing products that meet business needs with modern UX, regular feature updates, and enterprise-grade security and compliance certifications.

---

## Goals and Success Metrics

### Business Objectives

1. **Accelerate Development Velocity & Efficiency**
   - **Target:** Reduce developer time spent on framework overhead from 25% to <5%
   - **Measure:** Time-tracking analysis of framework vs. business logic development
   - **Target:** Enable new MVP creation in <2 weeks
   - **Measure:** Time from project initialization to first production deployment

2. **Drastically Improve Developer Onboarding & Productivity**
   - **Target:** Reduce new developer time-to-productivity from 6+ months to <1 month
   - **Definition:** Productivity = ability to build and deploy a standard domain aggregate to non-production environment
   - **Target:** Reduce time-to-first-production-deployment to <3 months
   - **Measure:** Track actual onboarding timelines for new team members

3. **Enhance Product Quality, Reliability, and Security**
   - **Target:** Achieve 85%+ code coverage verified by mutation testing (not just line coverage)
   - **Target:** Attain "audit-readiness" for ISO 27001/NIS2 compliance
   - **Target:** Progress OWASP ASVS 5.0 compliance to 100% Level 1 / 50% Level 2
   - **Measure:** Automated security scanning in CI/CD, formal security team review

4. **Unlock New Business & Market Opportunities**
   - **Target:** Complete ZEWSSP product migration to enable new customer acquisition (first migration target after Epic 9 completion)
   - **Target:** Secure at least one new enterprise customer requiring ISO 27001/NIS2 compliance through modernized ZEWSSP
   - **Measure:** Successful ZEWSSP migration completion, signed customer contracts

5. **Successfully Retire the Legacy DCA Framework**
   - **Target:** Achieve 100% feature parity with DCA (excluding deferred Dockets full complexity)
   - **Target:** Complete DPCM flagship product migration
   - **Measure:** All DCA-based products migrated and DCA infrastructure decommissioned

---

### User Success Metrics

- **Developer Satisfaction (dNPS):** Achieve and maintain a Developer Net Promoter Score of +50 via quarterly anonymous surveys measuring framework satisfaction, productivity impact, and recommendation likelihood.

- **Onboarding Effectiveness:** New developers must successfully build and deploy a standard prototype (non-trivial domain aggregate with full CQRS flow) to a non-production environment within their first week, demonstrating rapid productivity gains.

- **Framework Adoption Rate:** Consistent, high-frequency usage of the Scaffolding CLI across all teams, measured by CLI usage telemetry and commit analysis showing generated code patterns.

- **Documentation Utilization:** High engagement with framework documentation, measured by page views, search queries, and feedback ratings indicating developers can self-serve answers.

---

### Key Performance Indicators (KPIs)

1. **Developer Time-to-Value:** Reduce time from new developer start date to first **production deployment** from >6 months to **<3 months**. This composite metric captures onboarding, productivity, and confidence.

2. **Legacy System Retirement Rate:** 100% migration of DPCM and ZEWSSP from DCA to EAF, measured by percentage of production traffic served by EAF-based implementations.

3. **New Market Enablement:** Secure at least one new enterprise customer requiring ISO 27001/NIS2 compliance, demonstrating that security improvements unlock previously blocked revenue.

4. **Developer Net Promoter Score (dNPS):** Achieve and maintain dNPS of +50, indicating strong developer satisfaction and framework advocacy.

5. **Test Suite Performance:** Maintain full test suite execution time <15 minutes for comprehensive coverage, <3 minutes for fast feedback loop, enabling rapid iteration and CI/CD effectiveness.

6. **Framework Overhead Reduction:** Demonstrate <5% of developer time spent on framework-related issues vs. business logic development, measured through issue tracking and time analysis.

---

## MVP Scope

### Core Features (Must Have)

The MVP includes the following mandatory features that collectively deliver the promised developer experience and security baseline:

1. **One-Command Environment Setup**
   - Single command (`./scripts/init-dev.sh`) initializes complete local development stack
   - Docker Compose orchestration for PostgreSQL, Keycloak, Redis, Prometheus, Grafana
   - Automatic database migrations and seed data (Keycloak realm with test users/roles)
   - Health checks and verification scripts ensuring all services are ready
   - Configurable ports to prevent conflicts (e.g., GRAFANA_PORT override)

2. **Scaffolding CLI (v1)**
   - Command-line tool (`eaf`) for code generation using Mustache templates
   - `scaffold module` - Create new Spring Modulith-compliant modules
   - `scaffold aggregate` - Generate complete CQRS/ES vertical slices (aggregate, commands, events, handlers, projections)
   - `scaffold api-resource` - Generate REST API controllers with OpenAPI specs
   - `scaffold ra-resource` - Generate React-Admin UI components
   - All generated code passes quality gates (ktlint, Detekt) immediately

3. **Pre-Configured Persistence Layer**
   - Axon Framework integration with PostgreSQL via `JdbcEventStorageEngine`
   - Event store schema with partitioning and BRIN indexes for performance
   - Automatic audit trails on all events (timestamp, tenant, user)
   - Snapshot support for aggregate optimization
   - Projection tables with jOOQ for type-safe queries
   - Database migration management via Flyway

4. **Built-in Multi-Tenancy**
   - 3-layer tenant isolation architecture:
     - **Layer 1 (Request):** `TenantContextFilter` extracts tenant from JWT, populates `ThreadLocal` context
     - **Layer 2 (Service):** Command handler validation ensures tenant matches aggregate
     - **Layer 3 (Database):** PostgreSQL Row-Level Security policies enforce tenant isolation
   - Context propagation to async Axon event processors via message interceptors
   - Fail-closed design: missing tenant ID immediately rejects requests
   - Metrics and monitoring for tenant context operations

5. **Integrated Observability Suite (Core)**
   - **Structured Logging:** JSON format with automatic context (service_name, trace_id, tenant_id) via Logback/Logstash encoder
   - **Metrics Collection:** Prometheus endpoint (`/actuator/prometheus`) with Micrometer instrumentation for JVM, HTTP, Axon processing metrics, all tagged with tenant_id and service_name
   - **Distributed Tracing:** OpenTelemetry configuration with automatic trace propagation across API and Axon messages, trace_id injection into logs for correlation
   - Note: Visualization dashboards (Grafana panels) are explicitly Post-MVP

6. **Default Authentication & Authorization**
   - Keycloak OIDC integration via Spring Security OAuth2 Resource Server
   - 10-layer JWT validation standard:
     1. Format validation (3-part structure)
     2. Signature validation (RS256 with Keycloak public keys)
     3. Algorithm validation (RS256 only, reject HS256)
     4. Claim schema validation (required claims present)
     5. Time-based validation (exp, iat, nbf)
     6. Issuer/Audience validation
     7. Revocation check (Redis blacklist cache)
     8. Role validation (required roles present)
     9. User validation (user exists and active)
     10. Injection detection (SQL/XSS patterns in claims)
   - Provisioned Keycloak Testcontainer for integration tests
   - Emergency security recovery procedures and runbooks

7. **Flowable BPMN Workflow Integration**
   - Flowable engine integrated using dedicated PostgreSQL schema
   - Flowable-to-Axon bridge (dispatch commands from BPMN service tasks)
   - Axon-to-Flowable bridge (signal BPMN processes from event handlers)
   - Ansible adapter for legacy automation migration
   - Compensating transaction support for workflow error handling
   - "Dockets Pattern" BPMN template for migration compatibility

8. **Constitutional Quality Gates**
   - Automated enforcement via Gradle build and CI/CD pipeline:
     - ktlint (1.4.0) - zero formatting violations
     - Detekt (1.23.7) - zero static analysis violations
     - Konsist - Spring Modulith boundary verification
     - Pitest mutation testing - 80% minimum coverage
   - Test-First enforcement (RED-GREEN-Refactor mandate)
   - Integration test source sets (integrationTest, axonIntegrationTest)
   - Testcontainers for real dependencies (H2 forbidden)

---

### Out of Scope for MVP

To ensure focused delivery, the following features are explicitly deferred to Post-MVP:

- **Full Dockets Orchestration Engine Parity:** The complex legacy automation engine with dynamic UI generation, complex workflow builder, and all advanced features will not be replicated in MVP. Basic BPMN workflows with Flowable provide core orchestration needs.

- **Observability Dashboards:** Pre-configured Grafana panels and visualization dashboards are deferred. MVP provides metrics collection only; teams can build custom dashboards as needed.

- **AI-Powered Developer Assistant:** LLM-based features for documentation generation, test generation, and Q&A engine are Post-MVP.

- **Automated Project Generation (MCP):** Capability for an LLM to autonomously use the CLI for project generation is Post-MVP.

- **Automated DCA Migration Tool:** Migrations from DCA will be manual efforts. Automated migration tooling is deferred.

- **React-Admin Advanced Features:** Complex React-Admin features beyond basic CRUD (advanced filtering, bulk operations, complex dashboards) are deferred. MVP provides foundational admin portal only.

---

### MVP Success Criteria

The MVP will be considered successful when these three criteria are met:

1. **Production Viability**
   - A complete reference application (Epic 9) demonstrating all EAF v1.0 components must be successfully built using *only* framework capabilities
   - **Reference Application Scope:** Multi-tenant Widget Management System including:
     - Multiple domain aggregates with full CQRS command/query flows
     - Multi-tenancy demonstration (multiple tenant contexts, data isolation validation)
     - Authentication and authorization with role-based access
     - Flowable BPMN workflow integration (e.g., Widget approval workflow)
     - React-Admin UI for CRUD operations
     - Complete test coverage using Constitutional TDD patterns
   - Application must deploy and run in a production-like environment
   - Performance must meet defined KPIs (API latency p95 <200ms, event lag <10s)

2. **Onboarding & Velocity Validation**
   - Majlinda (incoming Senior Developer with no prior Kotlin/Spring experience), using only EAF documentation and Scaffolding CLI, must successfully build and deploy a new, fully tested, non-trivial domain aggregate in <3 days
   - This validation occurs during her onboarding phase (concurrent with or immediately following Epic 9)
   - Success validates the DevEx/Velocity goal and confirms documentation completeness for developers new to the stack
   - Majlinda must complete task with minimal framework team assistance (documentation and CLI only)

3. **Security & Compliance Validation**
   - Security Team conducts formal review of MVP components (Authentication, Multi-Tenancy, Observability)
   - Confirms architecture successfully meets ASVS 100% L1 / 50% L2 compliance targets
   - Achieves "audit-ready" status required for ISO 27001/NIS2 pursuits
   - Security review document produced for customer due diligence

---

## Post-MVP Vision

### Phase 2 Features

- **Dockets Engine Enhancement:** Implement advanced orchestration features from legacy Dockets system, including dynamic UI generation, complex workflow builder with visual DAG editor, and advanced hooks architecture.

- **Automated DCA Migration Tool:** Develop tooling to assist with migrating data and configurations from legacy DCA applications, accelerating ZEWSSP and DPCM retirement goals with reduced manual effort and error risk.

- **Observability Dashboards:** Provide pre-configured Grafana dashboards and panels visualizing the metrics collected by MVP observability modules, including golden signals, business metrics, and security monitoring.

- **Performance Optimization:** Implement advanced PostgreSQL optimizations (snapshotting strategy, advanced partitioning, materialized view projections) and establish migration triggers for Axon Server transition.

- **Advanced React-Admin Features:** Enhance admin portal with advanced filtering, bulk operations, relationship management, complex dashboards, and role-based UI customization.

---

### Long-term Vision (12-24 months)

**Intelligent Developer Partner:** Evolve EAF from a static framework into an intelligent development partner featuring:
- Integrated AI assistant providing context-aware documentation and examples
- Automated test generation based on aggregate behavior and business rules
- Architectural Q&A engine trained on framework patterns and best practices
- Proactive code review and refactoring suggestions
- Anomaly detection in metrics and logs with root cause analysis

**Platform Evolution:** Transform from monolithic deployment to cloud-native platform:
- Kubernetes operator for automated deployment and management
- Multi-region active-active deployment capabilities
- Advanced multi-tenancy with resource quotas and SLAs
- Self-service tenant provisioning and management portal

---

### Expansion Opportunities

- **AI-Driven Project Generation (MCP):** Explore Model Context Protocol integration where an LLM can autonomously use the Scaffolding CLI to build entire project prototypes directly from requirements documents, architecture diagrams, or user stories.

- **Framework as a Service:** Package EAF as a managed platform offering for external customers, providing hosted multi-tenant framework instances with automated operations and support.

- **Ecosystem Development:** Build marketplace for EAF modules, templates, and integrations, fostering community contributions and accelerating feature development through shared components.

- **Domain-Specific Variants:** Create specialized EAF variants for common use cases (IoT, Financial Services, Healthcare) with pre-built compliance controls, domain models, and integration patterns.

---

## Strategic Alignment and Financial Impact

### Financial Impact

**Cost Reduction:**
- **Developer Efficiency:** Reducing framework overhead from 25% to <5% liberates ~20% of development capacity. For the current team (Michael + Majlinda post-Epic), this represents approximately 0.4 FTE (~€40K annual value) that can be redirected to business logic. As the team scales to support multiple products (hypothetical 10-person scenario), this efficiency gain scales proportionally to ~2 FTE (~€200K annual value).
- **Onboarding Cost:** Reducing onboarding from 6 months to 1 month saves ~5 months per new hire. With Majlinda as immediate beneficiary and estimated 2-3 additional hires within 24 months, this saves ~15-20 person-months (~€125K-167K) in non-productive time and enables faster team scaling.
- **Maintenance Reduction:** Modern, well-documented architecture with comprehensive tests reduces ongoing maintenance burden. Estimated 30-40% reduction in bug triage, framework issues, and infrastructure problems.

**Revenue Enablement:**
- **ZEWSSP Migration (First Product):** Migration to EAF enables ZEWSSP to pursue new customers requiring security certifications. Confirmed as immediate post-Epic-9 priority. Conservatively estimated at 2-3 new enterprise contracts annually (~€500K-750K ARR potential).
- **DPCM Migration (Second Product):** Improved velocity enables faster feature delivery and customer retention. Reduced churn risk (~€200K annual risk mitigation).
- **New Product Opportunities:** Reduced MVP development time (2 weeks vs. 3-6 months) enables exploration of 3-4 additional market opportunities annually that were previously resource-constrained.

**Risk Mitigation:**
- **Security Breach Prevention:** Modern security architecture with automated scanning reduces breach risk. Industry average cost of breach is €4.45M; even 10% risk reduction justifies significant investment.
- **Compliance Penalties:** Avoiding NIS2/GDPR compliance failures (fines up to €10M or 2% revenue) through audit-ready architecture.
- **Technical Debt Compounding:** Early DCA retirement prevents exponential growth of technical debt and maintenance costs.

**Investment Required:**
- Core development team allocation for 12-18 months
- Infrastructure and tooling costs (CI/CD, monitoring, security scanning)
- Training and knowledge transfer for team

**ROI Timeline:**
- **Baseline:** ROI calculation starts from project initiation (Epic 1 start)
- **Break-even:** Expected within 12-18 months from project start through efficiency gains and avoided costs
- **Acceleration Point:** Positive ROI accelerates significantly upon first ZEWSSP contract secured (estimated 4-6 months post-Epic-9 completion)
- **Compounding Benefits:** ROI grows as more products migrate and team scales beyond initial 2-person core team

---

### Company Objectives Alignment

The EAF initiative directly supports Axians' strategic objectives:

1. **Digital Transformation Leadership:** Demonstrates commitment to modern, cloud-native architectures and positions Axians as a technology leader in enterprise software development.

2. **Operational Excellence:** Dramatically improves development efficiency, quality, and predictability, enabling faster response to market opportunities and customer needs.

3. **Security and Compliance:** Achieves audit-ready status (ISO 27001, NIS2, OWASP ASVS) enabling pursuit of enterprise and government contracts with elevated security requirements.

4. **Innovation Velocity:** Reduces time-to-market for new features and products, enabling rapid experimentation and validation of new business opportunities.

5. **Talent Attraction and Retention:** Modern technology stack and superior developer experience makes Axians more attractive to top engineering talent and reduces turnover risk.

6. **Customer Satisfaction:** Improved product quality, faster feature delivery, and modern UX directly enhance customer satisfaction and reduce churn.

---

### Strategic Initiatives

This framework enables several strategic company initiatives:

**Product Modernization Program:** EAF serves as the technical foundation for modernizing the entire product portfolio (DPCM, ZEWSSP, future products), replacing legacy DCA and enabling consistent architecture across all offerings.

**Cloud Migration Strategy:** While v1.0 targets on-premise deployment, the Hexagonal Architecture and swappable adapters position the framework for seamless cloud migration when business requirements evolve.

**Partner Ecosystem Development:** Clean API boundaries and comprehensive documentation enable external partners to build integrations and extensions, potentially creating a partner ecosystem around Axians products.

**International Market Expansion:** Security compliance and multi-tenancy support enable products to pursue international markets with varying regulatory requirements (EU GDPR, US compliance, etc.).

**Acquisition Integration:** Standardized framework simplifies integration of acquired companies' products into Axians portfolio, reducing integration time and technical risk.

---

## Migration Strategy & Customer Communication

### Post-Epic-9: ZEWSSP Migration Preparation

Following completion of Epic 9 (Reference Application validation), the focus shifts to ZEWSSP product migration as the first production migration target.

**Migration Preparation Activities:**

1. **Gap Analysis:** Comprehensive assessment of ZEWSSP-specific features requiring EAF adaptation
2. **Migration Planning:** Detailed technical migration plan including phasing, rollback procedures, and validation criteria
3. **Customer Communication Strategy Development:**
   - Identification of affected ZEWSSP customers and stakeholders
   - Communication timeline (pre-migration, during, post-migration)
   - Key messages: migration benefits (security compliance, new capabilities), timeline, support availability
   - Communication channels: direct customer contact, release notes, account manager briefings
   - Support escalation procedures during migration period
4. **Pilot Customer Selection:** Identify low-risk customer(s) for initial migration validation
5. **Rollback Planning:** Comprehensive rollback procedures in case of critical issues

**Customer Impact Management:**
- Transparent communication about migration timeline and potential service impacts
- Emphasis on business value: security certifications enabling new capabilities, improved performance, modern UX
- Dedicated support resources during migration window
- Success metrics tracking customer satisfaction throughout transition

---

## Technical Considerations

### Platform Requirements

**Deployment Target:**
- Products must be deployable on customer-hosted single servers via Docker Compose or Podman Compose
- Support for both containerized and traditional deployment models
- Zero-downtime deployment capability for production environments

**Processor Architecture Support:**
- **Mandatory:** amd64 (x86_64) - Primary deployment architecture
- **Mandatory:** arm64 (aarch64) - Apple Silicon development and emerging server platforms
- **Mandatory:** ppc64le (POWER9+) - Specific customer requirement for enterprise deployments
- All Docker images and binaries must be multi-architecture builds

**High Availability Requirements:**
- Active-Passive architecture with automated failover <2 minutes for outages
- Zero-downtime manual failover for maintenance windows
- PostgreSQL streaming replication for HA
- Patroni or similar for automated PostgreSQL failover management

**Disaster Recovery Requirements:**
- **RTO (Recovery Time Objective):** 4 hours maximum
- **RPO (Recovery Point Objective):** 15 minutes maximum
- PostgreSQL Point-In-Time Recovery (PITR) via WAL archiving
- Automated backup verification and testing procedures
- Quarterly disaster recovery drills

**Resource Requirements (Minimum):**
- 4 vCPU cores
- 8GB RAM
- 50GB storage (SSD recommended for PostgreSQL)
- Additional capacity required based on tenant count and event volume

---

### Technology Preferences

**Core Technology Stack (Non-Negotiable)**

These technologies are confirmed and non-negotiable for EAF v1.0:

- **Language & Runtime:** Kotlin on JVM 21 LTS for type-safe, maintainable enterprise code
- **Application Framework:** Spring Boot 3.x for enterprise application development
- **CQRS/Event Sourcing:** Axon Framework with PostgreSQL event store (designed as swappable adapter)
- **Identity & Access Management:** Keycloak OIDC for enterprise authentication and authorization
- **Database:** PostgreSQL 16.10 for event store, projections, and workflow engine
- **Deployment:** Docker Compose for local development and customer-hosted deployments
- **Quality Enforcement:** ktlint and Detekt for automated code quality gates (zero-tolerance)
- **Integration Testing:** Testcontainers for testing with real dependencies (in-memory databases forbidden)
- **Multi-Architecture:** Support for amd64, arm64, and ppc64le processor architectures

**Preferred Technologies (Under Evaluation)**

These technologies are preferred based on prototype validation but remain subject to final confirmation during Epic planning:

- **Testing Framework:** Kotest (Integration-First philosophy, BDD-style specifications)
- **Architectural Enforcement:** Spring Modulith (compile-time module boundary verification)
- **Workflow Engine:** Flowable BPMN (replacing legacy Dockets orchestration)
- **Functional Programming:** Arrow library (Either-based error handling in domain logic)
- **UI Framework:** React-Admin with Material-UI for operator portal
- **Query Layer:** jOOQ for type-safe SQL queries on projection tables
- **Observability:** Prometheus, Grafana, Jaeger for metrics and tracing

**Strategic Principles:**
- **FOSS-Only:** All components must be Free and Open Source Software
- **Swappable Adapters:** Infrastructure components designed for future replacement without core logic changes
- **Cloud-Ready:** Architecture supports future Kubernetes deployment while prioritizing Docker Compose
- **Enterprise-Grade:** All selections must support production deployment with security, HA/DR, and compliance requirements

**Detailed Specifications:** Complete version matrix, dependency configurations, and integration patterns documented in Architecture Document (see Appendix C).

---

### Architecture Considerations

**Repository Structure:**
- **Gradle Multi-Module Monorepo** validated by prototype
- Logical structure: `framework/`, `products/`, `shared/`, `apps/`
- Convention plugins in `build-logic/` for consistent configuration
- Version catalog (`libs.versions.toml`) for dependency management

**Core Architectural Patterns:**
- **Hexagonal Architecture:** Ports & Adapters pattern isolating domain logic from infrastructure
- **Spring Modulith 1.4.4:** Programmatic enforcement of module boundaries with compile-time verification
- **Domain-Driven Design (DDD):** Bounded contexts, aggregates, entities, value objects
- **CQRS/Event Sourcing:** Clear separation of write and read models, event-first persistence
- **Event-Driven Architecture:** Asynchronous processing via Axon Event Processors

**Persistence Strategy:**
- PostgreSQL implemented as **swappable Hexagonal adapter** (port)
- Event store schema with time-based partitioning and BRIN indexes
- Projection tables with jOOQ for type-safe queries
- Migration path preserved for future streaming solutions (NATS, Axon Server)

**Security Architecture:**
- **Defense in Depth:** Multiple validation layers (JWT, tenant, RLS)
- **Fail-Closed Design:** Missing required context immediately rejects requests
- **Principle of Least Privilege:** Role-based access control with fine-grained permissions
- **Security by Default:** All generated code includes security patterns

**Testing Philosophy:**
- **Constitutional TDD:** Test-First is law, RED-GREEN-Refactor mandatory
- **Integration-First:** 40-50% integration tests vs. traditional pyramid
- **Real Dependencies:** Testcontainers for PostgreSQL, Keycloak, Redis (H2 forbidden)
- **Nullable Pattern:** Fast business logic tests with stubbed infrastructure (60%+ faster)

**Error Handling:**
- **Arrow Either:** Functional error handling in domain logic
- **RFC 7807 Problem Details:** Standardized API error responses
- **Structured Logging:** All errors logged with context (trace_id, tenant_id)

---

## Constraints and Assumptions

### Constraints

1. **Timeline Constraints**
   - **Estimated MVP Duration:** ~11-13 weeks (approximately 3 months) for full Epic 1-9 completion
     - Epic 1-2 (Foundation + Walking Skeleton): 1-2 weeks
     - Epic 3-4 (Authentication + Multi-Tenancy): 3 weeks
     - Epic 5-6 (Observability + Flowable): 3 weeks
     - Epic 7 (Scaffolding CLI): 1 week
     - Epic 8 (Code Quality & Architectural Alignment): 1 week - Systematic resolution of architectural deviations and technical debt to ensure framework aligns with specifications
     - Epic 9 (Reference Application for MVP Validation): 2 weeks - Build complete multi-tenant Widget Management demo application
   - **Release Schedule:** Flexible with no fixed public release dates; delivery driven by completion of MVP Success Criteria
   - Multiple competing priorities for development team resources
   - Training time required for team to become proficient in Kotlin/CQRS patterns

2. **Scope Parity Requirement**
   - EAF must achieve 100% feature parity with legacy DCA (excluding deferred Dockets complexity)
   - Complete migration blockers must be identified and addressed before production cutover
   - Cannot retire DCA until all dependent products successfully migrated

3. **Resource Constraints**
   - **Core Team (During EAF Development - Epics 1-9):**
     - 1x Staff Engineer (Michael Walloschke, Senior Fullstack Developer) - 100% allocation
     - AI-assisted development via Claude Code agent
   - **Core Team (Post-Epic Completion - Product Development):**
     - Staff Engineer (Michael Walloschke) - 50% allocation (framework maintenance/evolution + mentorship)
     - 1x Senior Fullstack Developer (Majlinda) - 100% allocation (requires structured learning program)
   - **On-Demand Support:** IBM Power and Broadcom VMware experts available for product-specific guidance
   - **Training Program:** Self-directed learning with hands-on mentorship from Michael Walloschke covering:
     - **Timeline:** Begins during final Epic 9 execution, runs concurrently with EAF documentation finalization
     - **Duration:** Estimated 2-4 weeks intensive learning phase, followed by ongoing mentorship during ZEWSSP migration
     - **Curriculum:** Kotlin language fundamentals (transitioning from Node.js/JavaScript), Spring Boot 3.x and Spring Modulith architecture, CQRS/Event Sourcing patterns via Axon Framework, Hexagonal Architecture (Ports & Adapters) design principles
     - **Practical Application:** Hands-on learning through pair programming on ZEWSSP migration, validation of framework via MVP Success Criteria #2

4. **Technical Constraints**
   - Must support ppc64le architecture (limiting some technology choices)
   - Customer environments are air-gapped or have restricted internet access
   - Single-server deployment model constrains some scaling approaches
   - Cannot introduce breaking changes to existing product APIs during migration

5. **Organizational Constraints**
   - Change management required for development team adoption
   - Documentation and training materials must be comprehensive before handoff
   - Security team review and approval required before production deployment

6. **Licensing Constraints**
   - **Exclusive use of Free and Open Source Software (FOSS)** - no budget allocated for commercial licenses
   - All technology selections must have viable FOSS implementations
   - Enterprise support subscriptions or commercial editions not approved

---

### Key Assumptions

The following assumptions must hold true for this plan to remain viable:

**Team & Resources:**
- Core team capacity (Michael Walloschke 100% during Epic development) is sufficient to meet 11-13 week delivery timeline
- AI-assisted development (Claude Code) provides accelerated development velocity
- Majlinda's onboarding via self-directed learning with mentorship (no external training budget required)
- Learning curriculum covers Kotlin, Spring Boot/Modulith, CQRS/ES (Axon), and Hexagonal Architecture
- Practical training through pair programming on ZEWSSP migration ensures hands-on skill development
- IBM Power and Broadcom VMware specialists available on-demand for platform-specific guidance

**Scope & Requirements:**
- Non-Dockets feature parity scope is well-understood and stable
- No major new requirements will be added during Epic 1-9 development
- ZEWSSP migration confirmed as first product migration target (commencing after Epic 9 completion)
- DPCM migration planned as second migration target
- Legacy DCA can remain operational during parallel migration period

**Customer Communication:**
- Customer communication strategy for ZEWSSP and DPCM migrations will be developed prior to migration start
- Communication plan will address timing, stakeholder identification, migration benefits, potential impacts, and support during transition
- Strategy development occurs post-Epic-9 as part of migration preparation phase

**Technical Assumptions:**
- PostgreSQL event store performs adequately for projected event volumes
- Axon Framework 4.12.1 remains stable (5.x migration can be deferred)
- Docker images for all stack components exist for ppc64le architecture
- Network connectivity adequate for Docker registry, Keycloak, and development tools

**Organizational Assumptions:**
- Management support remains consistent throughout project
- Development team willing to adopt new technologies and practices
- Security team bandwidth available for reviews and audits
- Customer stakeholders support migration to new framework

**Market Assumptions:**
- Competitive landscape does not shift dramatically during development
- Enterprise compliance requirements (ISO 27001, NIS2) remain relevant
- Customer demand for modernized products continues

**Licensing Assumptions:**
- All selected FOSS components remain viable and actively maintained
- Community support and documentation sufficient for team needs
- No commercial licensing requirements emerge during development
- PostgreSQL performance remains adequate, avoiding need for commercial Axon Server migration

---

## Risks and Open Questions

### Key Risks

1. **PostgreSQL Event Store Scalability (Accepted Risk)**
   - **Risk:** Performance degradation at scale (>1M events per aggregate)
   - **Probability:** Medium-High (inherent to relational DB as event store)
   - **Impact:** High (potential production performance issues)
   - **Mitigation Strategy:**
     - Proactive optimization plan: snapshotting, partitioning, BRIN indexes
     - Establish firm performance KPIs as migration triggers (p95 latency, processor lag)
     - Architected as swappable adapter enabling migration to Axon Server or NATS
     - Regular performance testing with production-scale event volumes
   - **Status:** Accepted with mandatory mitigation plan

2. **Developer Learning Curve (Validated Risk)**
   - **Risk:** Ambitious <1 month productivity goal contradicted by 2-3 month realistic ramp-up for CQRS/ES
   - **Probability:** High (prototype proved complexity)
   - **Impact:** High (delays onboarding and team scaling)
   - **Mitigation Strategy:**
     - Heavy investment in Scaffolding CLI to eliminate boilerplate
     - Comprehensive "Golden Path" documentation with examples
     - Structured training program with hands-on exercises
     - Pair programming and mentorship during ramp-up
     - Redefine success metrics: <1 month for simple aggregate, <3 months for production deployment
   - **Status:** Mitigated through tooling and documentation investment

3. **Flowable/Dockets Migration Complexity**
   - **Risk:** Legacy Dockets workflows may not map cleanly to BPMN patterns
   - **Probability:** Medium (analysis identified complexity but documented patterns)
   - **Impact:** High (blocks DPCM/ZEWSSP migration)
   - **Mitigation Strategy:**
     - Comprehensive Dockets analysis completed (documented in stakeholder input)
     - De-scoped full Dockets complexity from MVP
     - BPMN template provides migration path for common patterns
     - Manual migration with Framework team support for complex cases
   - **Status:** De-risked through scope management

4. **EAF Hook Design for Future Extensibility**
   - **Risk:** v1.0 command interceptor architecture inadequate for future Phase 2 Dockets features
   - **Probability:** Medium (architectural design risk)
   - **Impact:** High (requires core refactoring if inadequate)
   - **Mitigation Strategy:**
     - Explicit architectural review of interceptor pattern design
     - Prototype Phase 2 hook integration scenarios before v1.0 finalization
     - Buffer time in schedule for architectural adjustments
   - **Status:** Requires focused architectural attention

5. **Multi-Tenancy Context Propagation**
   - **Risk:** Async event processing may lose tenant context leading to isolation failures
   - **Probability:** Medium (complex threading scenarios)
   - **Impact:** Critical (security vulnerability)
   - **Mitigation Strategy:**
     - ThreadLocal stack-based context with WeakReference for memory safety
     - Comprehensive integration tests for all async scenarios
     - Metrics and alerting for context propagation failures
     - Security team review of context propagation implementation
   - **Status:** Architectural pattern defined, requires thorough testing

6. **Axon v5 Migration Timing**
   - **Risk:** Axon 5.x provides improvements but requires migration effort
   - **Probability:** High (vendor roadmap indicates v5 maturity)
   - **Impact:** Medium (technical debt if delayed, churn if rushed)
   - **Mitigation Strategy:**
     - Monitor Axon 5.x stability and adoption in community
     - Schedule migration window after v1.0 stability achieved
     - Maintain compatibility layer during transition
   - **Status:** Deferred to Post-MVP, monitoring vendor roadmap

7. **Security Compliance Validation**
   - **Risk:** Security team review identifies gaps preventing ASVS certification
   - **Probability:** Low-Medium (architecture designed for compliance)
   - **Impact:** High (blocks customer acquisition goals)
   - **Mitigation Strategy:**
     - Early and continuous security team involvement
     - Formal review at multiple milestones, not just final
     - Automated security scanning in CI/CD catching issues early
     - Emergency recovery procedures documented and tested
   - **Status:** Continuous engagement required

---

### Open Questions

All critical questions have been resolved. No open questions remain at this time.

---

### Areas Needing Further Research

1. **DCA Feature Parity Audit**
   - Formal audit of all legacy DCA features (beyond analyzed "Dockets") required to confirm 100% parity scope
   - Dependency mapping between DCA features and dependent products
   - Hidden functionality or undocumented behavior that must be preserved
   - **Recommended Action:** Schedule comprehensive DCA codebase analysis with product teams

2. **Multi-Architecture Docker Image Verification**
   - Verify stable, supported Docker images for all tech stack components on **ppc64le** architecture
   - Test complete stack deployment on ppc64le hardware
   - Identify any components requiring custom builds or workarounds
   - **Recommended Action:** Set up ppc64le test environment and validate full stack

3. **Production Event Volume Analysis**
   - Analyze production event volumes from existing products to validate PostgreSQL scalability
   - Establish realistic performance benchmarks for event store queries
   - Identify threshold triggers for migration to streaming solution
   - **Recommended Action:** Extract anonymized production metrics from DPCM/ZEWSSP

4. **Security Certification Requirements**
   - Detailed requirements gathering for ISO 27001, NIS2, OWASP ASVS certifications
   - Gap analysis between current EAF architecture and certification requirements
   - Estimated effort and timeline for certification process
   - **Recommended Action:** Engage security team for formal requirements workshop

5. **Competitive Technology Assessment**
   - Evaluate alternative CQRS/ES frameworks and event stores emerging in market
   - Assess if any new technologies significantly outperform chosen stack
   - Review lessons learned from similar migrations in industry
   - **Recommended Action:** Schedule architecture review with external experts

---

## Appendices

### A. Research Summary

**Event Store Comparative Analysis**

A formal comparative analysis was performed evaluating free/open-source (FOSS) event stores as alternatives to PostgreSQL for the EAF prototype architecture.

**Key Finding:** PostgreSQL is the **only viable FOSS event store** for this specific technical stack (Kotlin/Spring Boot/Axon Framework).

**Analysis Results:**

| Solution | Compatibility | Integration | Performance | Verdict |
|----------|--------------|-------------|-------------|---------|
| **PostgreSQL** | Native Axon support | Mature `JdbcEventStorageEngine` | Good with optimizations | ✅ **SELECTED** |
| **Apache Kafka** | Partial | Axon extension is event bus only, not event store | Excellent streaming | ❌ Not viable as primary event store |
| **NATS/JetStream** | None | Zero official Axon integration | Excellent (low latency) | ❌ Requires 3-6 month custom integration |
| **EventStoreDB** | Third-party | Community connectors exist | Excellent | ⚠️ Commercial licensing, limited Axon adoption |

**Rationale for PostgreSQL Selection:**

1. **Native Integration Maturity:** Axon Framework's `JdbcEventStorageEngine` provides battle-tested, production-ready integration with PostgreSQL. This eliminates the 3-6 month custom integration risk.

2. **Operational Simplicity:** Single database instance for events, projections, and Flowable reduces operational complexity for customer on-premise deployments.

3. **Proven at Scale:** With proper optimizations (partitioning, BRIN indexes, snapshotting), PostgreSQL event stores have been validated in production for millions of events.

4. **Risk Mitigation:** Known limitations (scaling, query performance) are well-documented with established mitigation patterns. Unknown risks of custom integration outweigh known risks of PostgreSQL.

5. **Future Migration Path:** Hexagonal adapter architecture preserves low-effort migration to streaming solutions (NATS, Axon Server) when performance triggers are met.

**Decision:** v1.0 strategy confirmed to use PostgreSQL (Strategy A), prioritizing integration maturity and operational simplicity while proactively managing scaling risks.

---

### B. Stakeholder Input

**Dockets Analysis (DPCM Development Team)**

A comprehensive technical analysis of the legacy "Dockets" feature was provided by the DPCM development team, revealing significantly greater complexity than initially estimated.

**Key Findings:**

1. **Multi-Layer Orchestration Engine:** Dockets is not a simple script runner but a complex orchestration system with:
   - Dynamic workflow builder with visual DAG editor
   - Complex hook system (PRESCRIPT, POSTSCRIPT, error handlers)
   - State management and persistence
   - Integration with Ansible for automation
   - Custom DSL for workflow definition
   - UI components for workflow monitoring and control

2. **Timeline Impact:** Full feature parity would require estimated 6-9 months development effort, representing unacceptable risk to v1.0 timeline.

3. **Strategic Decision:** De-scope 100% Dockets parity from v1.0 MVP. Instead:
   - Provide Flowable BPMN foundation for core orchestration
   - Create "Dockets Pattern" BPMN template for common migration scenarios
   - Defer advanced features (visual builder, dynamic UI) to Phase 2
   - Support manual migration with Framework team assistance for complex workflows

**Impact:** This critical stakeholder input led to explicit scope reduction, significantly improving v1.0 delivery confidence while preserving core orchestration capabilities.

---

**Security Team Requirements**

The Security Team provided requirements for achieving "audit-ready" status:

1. **Automated Security Scanning:** All dependencies must be scanned in CI/CD with vulnerability database
2. **SBOM Generation:** Software Bill of Materials must be automatically generated for customer due diligence
3. **Compliance Documentation:** Comprehensive security documentation mapping architecture to ASVS/ISO 27001 controls
4. **Penetration Testing:** External security audit before production release
5. **Incident Response Procedures:** Documented and tested emergency recovery procedures

These requirements are incorporated into Epic 3 (Authentication) and overall architecture specifications.

---

### C. References

**Project Documentation:**
- EAF Project Brief (v0.1) - `/acci_eaf/docs/project_brief.md` - Strategic foundation and business context
- EAF Architecture Document (v1.0) - `/acci_eaf/docs/architecture.md` - Complete technical specifications
- EAF Product Requirements Document (v0.1) - `/acci_eaf/docs/prd.md` - Epic and story breakdown
- EAF UI/UX Specification - `/acci_eaf/docs/front-end-spec.md` - Interface design guidelines

**Technical Research:**
- Event Store Comparative Analysis - Research validating PostgreSQL selection
- Dockets Feature Deep-Dive Analysis - DPCM team analysis of orchestration complexity
- Spring Modulith 1.4.4 Documentation - Boundary enforcement patterns
- Axon Framework 4.12.1 Reference Guide - CQRS/ES implementation patterns

**Industry Standards:**
- OWASP ASVS 5.0 - Application Security Verification Standard
- ISO 27001:2022 - Information Security Management
- NIS2 Directive - Network and Information Systems Security
- WCAG 2.1 Level A - Web Content Accessibility Guidelines
- RFC 7807 - Problem Details for HTTP APIs

**Technology Documentation:**
- Kotlin 2.2.21 Language Reference
- Spring Boot 3.5.7 Reference Documentation
- PostgreSQL 16 Documentation - Event Store Configuration
- Keycloak 26.4.2 Server Administration Guide
- Flowable 7.1 User Guide - BPMN 2.0 Integration

---

_This Product Brief serves as the foundational input for Architecture Review and Product Requirements Document (PRD) refinement._

_Next Steps: Review with stakeholders, address open questions, proceed to architectural design and epic planning phases._
