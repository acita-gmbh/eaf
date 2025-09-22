# Project Brief: Enterprise Application Framework (v0.1)

## Executive Summary

This document outlines the official v0.1 project brief for the Enterprise Application Framework (EAF), a "batteries-included" development and management environment for developers at Axians. Its primary purpose is to provide a runnable local development stack with immediate code quality enforcement, a battle-tested persistence layer, and pre-configured modules for authentication and observability, allowing developers to focus exclusively on business logic.

This framework replaces the outdated, high-risk, and inefficient DCA framework (2016). The target market is Axians' internal development teams. The core value proposition is delivering a superb developer experience characterized by one-command project setup, a scaffolding CLI, and a comprehensive, fast-running test suite modeled on the successful innovations of the prototype (such as the "Nullable Design Pattern").

The v0.1 persistence layer will utilize **PostgreSQL** as the event store. This approach prioritizes the stability and maturity of its **native Axon Framework integration** over solutions requiring high-risk, custom integration development. Adhering strictly to Hexagonal boundaries, the persistence layer will be treated as a **swappable adapter**. This ensures the core domain logic remains isolated, safeguarding a low-effort future migration path to a streaming solution (like NATS) when defined performance triggers are met.

## Problem Statement

The legacy "DCA" framework (2016) has evolved from a technical asset into the primary strategic bottleneck for the business, directly impacting development costs, time-to-market, and revenue opportunities.

From a management and product perspective, the DCA framework causes three critical business failures:

1.  **Drastic Inefficiency (Cost):** An estimated **25% of all developer effort** is consumed by framework overhead, maintenance, and implementing workarounds for its brittle, custom-built persistence layer. This directly inflates the cost of all feature development.
2.  **Prohibitive Onboarding (Velocity):** The framework's complexity and complete lack of documentation or tests create a prohibitive **6+ month onboarding time** for new developers, severely limiting team scalability and velocity.
3.  **Blocked Business Opportunities (Revenue & Risk):** The framework's inflexibility directly impedes new revenue. Key products, such as **ZEWSSP**, are blocked from new customer acquisition because their underlying DCA platform cannot be updated, secured, or certified.

These business impacts are symptoms of specific, critical technical failures identified in the prototype analysis. The legacy DCA framework suffers from:
* A complete lack of **Multi-Tenancy support**.
* Critical **Security Gaps** (like no modern JWT validation).
* Zero **Observability** (insufficient logging, metrics, and monitoring).
* An inflexible, monolithic architecture that makes **Testing challenging** and iteration risky.

## Proposed Solution

The proposed solution is the v0.1 Enterprise Application Framework (EAF), a "batteries-included" development and management environment for Axians developers.

The core concept is a framework built on the prototype's production-validated architecture: **Hexagonal Architecture**, **CQRS/Event Sourcing**, and **Spring Modulith**, implemented in **Kotlin/Spring Boot**. This architecture programmatically enforces boundaries and isolates business logic, which directly solves the "inflexible" and "untestable" nature of the legacy DCA framework.

This solution will succeed where DCA failed by prioritizing a superb developer experience and verifiable quality. Key differentiators include:

1.  **One-Command Onboarding:** A runnable local stack (Docker Compose) initialized with a single command.
2.  **Scaffolding CLI (v1):** A tool for rapid domain creation (modules, aggregates) that enforces architectural patterns from inception.
3.  **Innovative Testing:** Adoption of the prototype's "Integration-First Testing" philosophy, including the "Nullable Design Pattern" (98% faster tests) and automated quality gates (ktlint, Detekt, mutation testing).
4.  **Secure by Default:** A pre-configured core including the prototype's 10-layer JWT validation and 3-layer multi-tenancy model.

The v0.1 persistence layer will utilize **PostgreSQL** as the event store. This approach prioritizes the stability and maturity of its **native Axon Framework integration** over solutions requiring high-risk, custom integration development. Adhering strictly to Hexagonal boundaries, the persistence layer will be treated as a **swappable adapter**. This ensures the core domain logic remains isolated, safeguarding a low-effort future migration path to a streaming solution (like NATS) when defined performance triggers are met.

## Target Users

This framework is built for distinct user segments whose goals are currently blocked by the legacy DCA framework.

* **Primary User Segment: Axians Developers**
    * **Profile:** Axians' in-house developers, possessing a medium skill level with specialization in the Node.js and React technology stacks.
    * **Pain Points:** Their workflow is frequently blocked by the ambiguity and lack of confidence in the existing DCA framework. They are burdened by its brittle persistence layer and the complete absence of a testing safety net.
    * **Goals:** Need clear documentation, a scaffolding CLI for rapid pattern adoption, and a comprehensive safety net of tests to confidently contribute to product quality.

* **Secondary User Segment: Product Manager**
    * **Profile:** Responsible for the strategic roadmap and feature backlog.
    * **Pain Points:** Their workflow is directly impeded by the DCA framework, which makes development timelines unpredictable and excessively long.
    * **Goals:** Drastically reduce time-to-market for new features and gain the ability to rapidly prototype new ideas that can evolve into production-ready MVPs.

* **Secondary User Segment: The Security Team**
    * **Profile:** Responsible for managing technical risk and ensuring product compliance.
    * **Pain Points:** The current DCA framework is a "black box" that is impossible to formally review or audit, blocking the company from acquiring customers with elevated security requirements.
    * **Goals:** Need the EAF to be "secure-by-default" (like the 10-layer JWT validation proven in the prototype) and provide automated ways to prove compliance (e.g., generating an SBOM).

* **Secondary User Segment: End Customers**
    * **Profile:** Companies purchasing and using the software products (like ZEWSSP or DPCM) built by Axians.
    * **Pain Points:** Their experience is negatively impacted by bugs, slow performance, an outdated UX, and a slow pace of feature development stemming from the legacy framework.
    * **Goals:** Need stable, high-performing products that meet their business needs, including critical features like multi-tenancy.

## Goals & Success Metrics

#### Business Objectives

1.  **Accelerate Development Velocity & Efficiency:** Reduce developer time spent on framework overhead from 25% to <5% and enable new MVP creation in under two weeks. (Target: Q1 2026)
2.  **Drastically Improve Developer Onboarding & Productivity:** Reduce new developer **time-to-productivity** (defined by ability to build a prototype) from 6+ months to **<1 month**.
3.  **Enhance Product Quality, Reliability, and Security:** Achieve 85%+ code coverage (verified by mutation testing) and attain "audit-readiness" for ISO 27001/NIS2, progressing OWASP ASVS 5.0 compliance to **100% L1 / 50% L2**. (Target: **Q1 2026**)
4.  **Unlock New Business & Market Opportunities:** Unblock new customer acquisition by refactoring the ZEWSSP product onto the EAF. (Target: Q1 2026)
5.  **Successfully Retire the Legacy DCA Framework:** Achieve 100% feature parity with DCA and migrate the flagship DPCM product. (Target: **Q3 2026**)

#### User Success Metrics

* **Developer Satisfaction:** Achieve and maintain a Developer Net Promoter Score (dNPS) of +50 via quarterly surveys.
* **Onboarding Effectiveness:** A new developer must be able to build and deploy a standard prototype (to a non-production environment) within their first week.
* **Framework Adoption and Engagement:** See consistent, high-frequency usage of the scaffolding CLI across all teams.

#### Key Performance Indicators (KPIs)

1.  **Developer Time-to-Value:** Reduce time from a new developer's start date to their first **production deployment** from >6 months to **<3 months**.
2.  **Legacy System Retirement Rate:** 100% migration of DPCM and ZEWSSP from DCA to the EAF by the end of Q3 2026.
3.  **New Market Enablement:** Secure at least one new enterprise customer requiring ISO 27001 / NIS2 compliance by the end of **Q2 2026**.
4.  **Developer Net Promoter Score (dNPS):** Achieve and maintain a dNPS of +50.

## MVP Scope

#### Core Features (Must Have) for MVP

1.  **One-Command Environment Setup:** A single command that initializes a complete, runnable local development stack (Docker Compose, database, etc.).
2.  **Scaffolding CLI (v1):** The initial CLI version, capable of generating core project structures, domains, and modules that adhere to the EAF patterns.
3.  **Pre-Configured Persistence Layer:** A ready-to-use persistence module (using PostgreSQL per our strategy) with automatic audit trails.
4.  **Built-in Multi-Tenancy:** Core architectural support and modules for data isolation between tenants.
5.  **Integrated Observability Suite (Core):** Pre-configured, standardized modules for structured **logging and metrics collection**. (Note: Visualization dashboards like Grafana panels are deferred to Post-MVP).
6.  **Default Authentication & Authorization:** A secure, ready-to-use module relying on **Keycloak** (our selected OIDC provider) for user access, aligning with the prototype's 10-layer JWT validation model.

#### Out of Scope for MVP

To ensure focus, the following features are explicitly deferred:

* **Full Dockets Orchestration Engine Parity:** The complex legacy automation engine (workflow, custom hooks, dynamic UI) will not be replicated in the MVP.
* AI-Powered Developer Assistant (doc/test generation, Q&A engine).
* Automated Project Generation (MCP) (capability for an LLM to use the CLI).
* Automated DCA Migration Tool (migrations will be manual efforts).

#### MVP Success Criteria

The MVP will be considered successful when these **three** criteria are met:

1.  **Production Viability:** The in-house licensing server application (Epic 6 in the prototype roadmap) must be successfully rebuilt, deployed, and run in production using *only* the EAF MVP components.
2.  **Onboarding & Velocity Validation:** A core team developer, using only the documentation and the scaffolding CLI, must successfully build and deploy a new, fully tested, non-trivial domain aggregate in less than three days (validating the DevEx/Velocity goal).
3.  **Security & Compliance Validation:** The Security Team must conduct a formal review of the MVP components (Auth, Tenancy, Logging) and confirm that the architecture successfully meets the defined ASVS 100% L1 / 50% L2 compliance targets, achieving the "audit-ready" status required by our goals.

## Post-MVP Vision

#### Phase 2 Features

* **Dockets Engine Implementation:** Implement the "Dockets" orchestration engine, plugging into the architectural hooks (command interceptors) defined in the v0.1 MVP.
* **Automated Data Migration Tool:** Develop an automated tool to assist with migrating **data** from legacy DCA applications, accelerating the retirement goals for ZEWSSP and DPCM.
* **Observability Dashboards:** Provide pre-configured Grafana panels and other visualization dashboards for the core metrics collected by the MVP modules.

#### Long-term Vision

* **Intelligent Developer Partner:** Evolve the EAF from a static framework into an intelligent partner for developers, featuring an integrated AI assistant for context-aware documentation, automated test generation, and an architectural Q&A engine.

#### Expansion Opportunities

* **AI-Driven Project Generation (MCP):** Explore AI-driven project generation (the "Automated Project Generation (MCP)" feature explicitly out of scope for the MVP), where an LLM can use the scaffolding CLI as a control protocol to build entire project prototypes directly from documents.

## Technical Considerations

#### Platform Requirements

* **Deployment Target:** Products must be deployable on customer-hosted single servers via Docker/Podman Compose.
* **Processor Architecture:** The framework must support `amd64`, `arm64`, and `ppc64le` processor architectures.
* **High Availability:** Must support an Active-Passive architecture with <2 minute automated failover for outages and zero-downtime manual failover for maintenance.
* **Disaster Recovery:** Must meet enterprise backup/restore standards with an RTO of 4 hours and an RPO of 15 minutes, achieved via PostgreSQL PITR.

#### Technology Constraints (Validated by Prototype)

These are the non-negotiable, version-locked technologies proven by the prototype to work:

* **Backend Language/Framework:** **Kotlin 2.2.20 (CURRENT)** and **Spring Boot 3.5.6 (CURRENT)** with full tooling compatibility via detekt workaround.
* **CQRS Framework:** **Axon Framework 4.9.4**.
* **Database (Event Store + Projections):** **PostgreSQL 16.1+** (per our "Strategy A" decision).
* **Frontend Options:** React, Vaadin/Hilla, or TUI (Terminal UI).
* **Testing Stack:** **Kotest** (replacing JUnit) and **Testcontainers** (for the Integration-First philosophy).

#### Architecture Considerations (Core Patterns)

* **Repository Structure:** A **Gradle Multi-Module Monorepo**.
* **Core Architectural Patterns:** The EAF must be built using **Hexagonal Architecture** (programmatically enforced via **Spring Modulith 1.4.3**), **Domain-Driven Design (DDD)**, and **CQRS/Event Sourcing (CQRS/ES)**.
* **Security Standard:** **Keycloak OIDC** integration is the standard.

## Constraints & Assumptions

#### Constraints

1.  **Timeline:** The project is governed by an aggressive, fixed timeline based on our finalized goals:
    * Target (Security Compliance ASVS 100% L1 / 50% L2): **End of Q1 2026**.
    * Target (New Market Enablement / ZEWSSP Migration): **End of Q2 2026**.
    * Target (Full Legacy Retirement / DPCM Migration): **End of Q3 2026**.
2.  **Scope Parity:** The EAF must achieve 100% feature parity with the legacy DCA. (Note: The complex "Dockets" feature parity is deferred to Post-MVP).
3.  **Resources:** The project relies on a fixed core team with defined allocations and must account for the required timeline for extensive Kotlin/architecture training for a senior developer.

#### Key Assumptions

The following assumptions (adopted from the prototype brief) must hold true for this plan to be viable:

* We assume the allocated core team capacity is sufficient to meet these deadlines.
* We assume that the required on-demand specialist developers (for specific technologies) will be available when needed.
* We assume the *non-Dockets* feature parity scope is well-understood and defined.

## Risks & Open Questions

#### Key Risks (v0.1)

1.  **PostgreSQL Event Store Scalability (Primary Accepted Risk):** We accept the known risk of performance degradation at scale (e.g., >1M events).
    * **Mitigation:** This risk is managed by a mandated, proactive optimization plan (snapshotting, partitioning, BRIN indexes) and the establishment of firm performance KPIs (p95 latency, processor lag) that serve as migration triggers to the commercial Axon Server.
2.  **Developer Learning Curve:** The prototype *validated* this risk. It proved that the ambitious <1 month onboarding goal is contradicted by the 2-3 month realistic ramp-up time for a complex CQRS/ES stack.
    * **Mitigation:** Heavy investment in the Scaffolding CLI, robust documentation, and "Golden Path" templates to meet the <1 month productivity goal.
3.  **EAF Hook Design:** The v0.1 architecture must correctly design the command interceptor "hooks" (the ports) for the future Dockets engine to prevent major refactoring during the Phase 2 implementation.
4.  **Axon v5 Migration Complexity:** The timeline must account for the complexity of migrating from Axon 4.9.4 to 5.x, which remains a valid technical risk.

#### Open Questions

* What is the procurement and availability lead time for the required "on-demand specialist developers" identified in the assumptions?

#### Areas Needing Further Research

* A formal audit of all legacy DCA features (beyond "Dockets") is required to confirm the 100% parity scope required for the DPCM migration.
* Verification of stable, supported Docker images for *all* tech stack components (Keycloak 26.0.0, Kotlin 2.2.20, etc.) on the mandatory `ppc64le` architecture.

## Appendices

#### A. Research Summary

A formal comparative analysis was performed on free/open-source (FOSS) event stores to validate or replace the prototype's choice of PostgreSQL.

* **Finding:** The research concluded that PostgreSQL is the **only viable FOSS event store** for this specific technical stack (Kotlin/Axon).
* **Rationale:** Competing solutions were ruled technically incompatible for the required role:
    * **Apache Kafka:** The official Axon Framework extension is an **event bus (router) only** and explicitly does not support its use as a primary event store.
    * **NATS/JetStream:** While architecturally a strong fit (low latency, efficient replay), it has **zero official Axon Framework integration**, requiring a high-risk, 3-6 month custom engineering effort to build a storage engine.
* **Decision:** The v0.1 strategy is confirmed to use PostgreSQL (Strategy A), prioritizing native integration maturity while proactively managing its known scaling risks via a mandated mitigation plan (partitioning, BRIN indexing, and snapshotting).

#### B. Stakeholder Input

* **Dockets Analysis:** A deep-dive technical analysis of the legacy "Dockets" feature was provided by the DPCM development team. This analysis revealed the feature to be a complex, multi-layered orchestration engine, not a simple script runner. This input was the deciding factor in strategically **de-scoping 100% parity of the Dockets engine from the v0.1 MVP** to mitigate unacceptable timeline risk.

#### C. References

* EAF Prototype Project Brief (project_brief.md)
* EAF Prototype Architecture Summary (EAF-ARCHITECTURE-SUMMARY.md)
* DPCM 'Dockets' Feature Deep-Dive Analysis (Dockets Analysis doc)
* Event Store Comparative Analysis Reports (Research Docs 1 & 2)
