# Comprehensive Technical Analysis: EAF v1.0 Technology Stack

**Date:** 2025-10-30
**Analysis Type:** Deep Technical Research - Technology Decision Validation
**Target Project:** Enterprise Application Framework (EAF) v1.0
**Product Brief:** `/Users/michael/eaf/docs/product-brief-EAF-2025-10-30.md`
**Analyst:** Claude Code Agent

---

## Executive Summary

This comprehensive technical analysis evaluates **ALL** technology decisions specified in the EAF v1.0 Product Brief. For each technology choice, we provide:

1. **Current choice verification** - Version accuracy, stated rationale
2. **Alternative research** - 2-3 viable alternatives with latest 2024-2025 data
3. **Critical evaluation** - Assessment against architectural constraints
4. **Risk assessment** - Technology risks, vendor lock-in, breaking changes
5. **Best practices** - Industry patterns, case studies, common pitfalls

### Key Findings Summary

✅ **VALIDATED CHOICES:**
- Kotlin 2.2.20 (Latest 2.2.21 available - minor update)
- Spring Boot 3.5.6 (Latest 3.5.7 available - patch update)
- PostgreSQL 16.1+ as Event Store (appropriate with optimization plan)
- Gradle for multi-module monorepo (superior to Maven for Kotlin)
- Kotest for Kotlin testing (better than JUnit 5 for Kotlin-first)
- Testcontainers for integration testing (industry standard)

⚠️ **REQUIRES ATTENTION:**
- **Axon Framework 4.12.1** - Version 5.x is in milestone release; plan migration timing
- **Keycloak 26.0** - ppc64le architecture lacks official Docker images
- **Flowable BPMN 7.1** - Camunda 7 EOL approaching; evaluate alternatives
- **Pitest mutation testing** - Kotlin support deprecated; Arcmutate (commercial) recommended
- **Active-Passive HA** - Product Brief states "Active-Active HA/DR" but describes Active-Passive

🚨 **CRITICAL RISKS:**
- **ppc64le architecture support** - Keycloak requires custom builds
- **PostgreSQL scalability** - Accepted risk; requires proactive monitoring
- **Axon 5.x migration** - Should occur post-MVP but before major product launches
- **Mutation testing gaps** - Open-source Kotlin mutation testing has limitations

---

## Table of Contents

1. [Core Technology Stack](#1-core-technology-stack)
2. [Persistence Layer](#2-persistence-layer)
3. [Security Stack](#3-security-stack)
4. [Workflow Engine](#4-workflow-engine)
5. [Testing Framework](#5-testing-framework)
6. [Observability Stack](#6-observability-stack)
7. [Code Quality Tools](#7-code-quality-tools)
8. [Frontend Framework](#8-frontend-framework)
9. [Build System](#9-build-system)
10. [Multi-Architecture Support](#10-multi-architecture-support)
11. [Architecture Patterns](#11-architecture-patterns)
12. [Technology Decision Matrix](#12-technology-decision-matrix)
13. [Risk Assessment Summary](#13-risk-assessment-summary)
14. [Recommendations](#14-recommendations)

---

## 1. Core Technology Stack

### 1.1 Kotlin 2.2.20

#### Current Choice Analysis

**Product Brief Statement:**
- Version: Kotlin 2.2.20
- Runtime: JVM 21 LTS
- Rationale: Type-safe, maintainable enterprise code

**Version Verification (2025-10-30):**
- **Stated Version:** 2.2.20
- **Release Date:** September 10, 2025
- **Latest Version:** Kotlin 2.2.21 (minor patch available)
- **Next Version:** Kotlin 2.3.0-Beta2 in EAP

**Key Features in 2.2.20:**
- Kotlin/Wasm in Beta with improved exception handling
- Swift export by default in Kotlin Multiplatform
- Stable cross-platform compilation for libraries
- Improved overload resolution for suspend functions
- Stack canaries in Kotlin/Native binaries
- Long values compiled to JavaScript BigInt in Kotlin/JS

#### Alternative Research

##### Alternative 1: Java 21 LTS
**Pros:**
- Mature ecosystem, vast library support
- Virtual threads (Project Loom) for scalable concurrency
- Pattern matching, sealed classes, records
- Strong enterprise adoption
- Excellent tooling and IDE support

**Cons:**
- More verbose syntax than Kotlin
- Null safety requires annotations (not enforced)
- Less functional programming support
- Slower language evolution

**Verdict:** ❌ **Not Recommended** - Kotlin provides superior type safety, null safety, and developer ergonomics critical for reducing bugs in complex CQRS/ES systems.

##### Alternative 2: Scala 3
**Pros:**
- Advanced type system with dependent types
- Strong functional programming support
- Excellent for complex domain modeling
- Mature ecosystem on JVM

**Cons:**
- Steeper learning curve (6+ months realistic vs 2-3 for Kotlin)
- Slower compilation times
- Smaller talent pool
- More complex syntax can intimidate medium-skill developers

**Verdict:** ❌ **Not Recommended** - Learning curve contradicts <1 month productivity goal; team skill level (medium, Node.js background) makes Kotlin more appropriate.

##### Alternative 3: Latest Kotlin (2.3.0-Beta2)
**Pros:**
- Cutting-edge features
- Performance improvements
- Latest language enhancements

**Cons:**
- Beta stability risks
- Potential breaking changes
- Less community testing
- Production risk unacceptable

**Verdict:** ❌ **Not Recommended** - Stay with stable 2.2.x line; upgrade to 2.2.21 for bug fixes.

#### Critical Evaluation

**Architectural Constraints:**
- ✅ **ppc64le Support:** Kotlin compiles to JVM bytecode; architecture-agnostic
- ✅ **Air-Gapped Deployment:** No runtime dependencies beyond JVM
- ✅ **FOSS Requirement:** Apache 2.0 license
- ✅ **Team Skill Level:** Gentler learning curve than Java for Node.js developers

**CQRS/ES Compatibility:**
- ✅ Excellent data class support for immutable events
- ✅ Sealed classes perfect for command/event hierarchies
- ✅ Coroutines support for async event processing
- ✅ Arrow library integration for functional error handling

**Multi-Tenancy Support:**
- ✅ ThreadLocal context propagation works seamlessly
- ✅ Coroutine context for tenant ID propagation
- ✅ Type-safe inline classes for TenantId value objects

#### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Breaking changes in Kotlin updates | Low | Medium | Pin to 2.2.x line; upgrade conservatively |
| Limited Kotlin expertise in team | Medium | High | Structured training, pair programming, comprehensive docs |
| JetBrains vendor dependency | Low | Low | Open-source Apache 2.0; community would fork if needed |
| Compilation performance | Low | Low | Incremental compilation, Gradle caching |

#### Recommendations

✅ **APPROVED** with minor adjustment:

1. **Upgrade to Kotlin 2.2.21** (latest patch) immediately
2. **Stay on 2.2.x line** through MVP and ZEWSSP migration
3. **Plan Kotlin 2.3 upgrade** post-ZEWSSP (Q2 2026 estimated)
4. **Document Kotlin patterns** extensively for team learning
5. **Mandate coroutine usage** for all async operations

**Sources:**
- https://blog.jetbrains.com/kotlin/2025/09/kotlin-2-2-20-released/
- https://kotlinlang.org/docs/releases.html

---

### 1.2 Spring Boot 3.5.6

#### Current Choice Analysis

**Product Brief Statement:**
- Version: Spring Boot 3.5.6
- Framework: Spring Boot 3.x
- Rationale: Enterprise application development

**Version Verification (2025-10-30):**
- **Stated Version:** 3.5.6
- **Release Date:** September 18, 2025
- **Latest Version:** Spring Boot 3.5.7 (October 23, 2025)
- **Next Major:** Spring Boot 4.0 (timeline TBD)

**Key Updates in 3.5.6:**
- 43 bug fixes and documentation improvements
- Spring Framework 6.2.11
- Spring Kafka 3.3.10
- Spring Integration 6.5.2
- Spring Security 6.5.5
- Hibernate 6.6.29.Final

**Latest (3.5.7) Includes:**
- 69 additional bug fixes
- Further dependency upgrades

#### Alternative Research

##### Alternative 1: Quarkus 3.x
**Pros:**
- Faster startup time (sub-second)
- Lower memory footprint (70% less than Spring)
- Native compilation with GraalVM
- Reactive-first design
- Growing ecosystem
- Better for serverless/containerized deployments

**Cons:**
- Less mature than Spring (released 2019)
- Smaller ecosystem and fewer libraries
- Reflection-heavy code requires GraalVM configuration
- Less community knowledge for troubleshooting
- Axon Framework integration less mature

**Verdict:** ⚠️ **Viable Alternative** - Consider for Phase 2 or greenfield microservices, but Spring Boot safer for MVP given team experience and Axon integration maturity.

##### Alternative 2: Micronaut 4.x
**Pros:**
- Compile-time dependency injection (faster startup)
- Low memory consumption
- Native GraalVM support
- Reactive-first architecture
- Good for microservices

**Cons:**
- Smaller ecosystem than Spring
- Less comprehensive documentation
- Fewer integrations available
- Team learning curve
- Limited Axon Framework examples

**Verdict:** ❌ **Not Recommended** - Ecosystem maturity and Axon integration not as proven as Spring Boot.

##### Alternative 3: Ktor 3.x (JetBrains)
**Pros:**
- Kotlin-first framework (not Java interop)
- Lightweight and flexible
- Coroutine-native
- Simple, unopinionated design
- Excellent for APIs

**Cons:**
- No Spring ecosystem integration
- Axon Framework designed for Spring
- Lacks enterprise features (security, metrics, health)
- Would require building infrastructure from scratch
- Too minimalist for "batteries-included" framework

**Verdict:** ❌ **Not Recommended** - Too much infrastructure to rebuild; contradicts "batteries-included" philosophy.

#### Critical Evaluation

**Architectural Constraints:**
- ✅ **ppc64le Support:** Spring Boot JVM-based; architecture-agnostic
- ✅ **Air-Gapped Deployment:** JAR packaging with embedded server
- ✅ **FOSS Requirement:** Apache 2.0 license
- ✅ **Single-Server Deployment:** Perfect for embedded Tomcat/Netty

**CQRS/ES Compatibility:**
- ✅ **Axon Framework Integration:** Native Spring Boot support
- ✅ **Spring Modulith 1.4.3:** Requires Spring Boot 3.x
- ✅ **Event-Driven Architecture:** Spring Events, Spring Integration
- ✅ **Transaction Management:** Robust @Transactional support

**Multi-Tenancy Support:**
- ✅ **Filters:** Servlet filters for tenant extraction
- ✅ **Interceptors:** HandlerInterceptor for tenant validation
- ✅ **ThreadLocal:** Spring Request Context propagation
- ✅ **Async:** @Async with TaskExecutor context propagation

**Observability:**
- ✅ **Micrometer:** Built-in Prometheus integration
- ✅ **Spring Boot Actuator:** Health checks, metrics, info endpoints
- ✅ **OpenTelemetry:** Spring Boot auto-configuration support
- ✅ **Structured Logging:** Logback integration

#### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Spring Boot 4.0 breaking changes | Medium | Medium | Stay on 3.x LTS; upgrade post-MVP |
| Framework overhead | Low | Low | Measured startup acceptable for on-prem |
| Version dependency conflicts | Low | Medium | Use Spring Boot BOM for dependency mgmt |
| Over-engineering | Medium | Low | Use Spring Modulith to enforce boundaries |

#### Recommendations

✅ **APPROVED** with update:

1. **Upgrade to Spring Boot 3.5.7** (latest patch)
2. **Use Spring Boot BOM** for all dependency management
3. **Enable Virtual Threads** (Java 21 feature) for better scalability
4. **Monitor Spring Boot 4.0** roadmap but don't rush adoption
5. **Leverage Actuator** extensively for operational insights

**Performance Notes:**
- Spring Boot 3.x startup time: ~2-3 seconds (acceptable for on-prem deployment)
- Memory footprint: ~300-500MB (acceptable for 8GB minimum requirement)
- Throughput: Handles thousands of requests/second with proper tuning

**Sources:**
- https://spring.io/blog/2025/09/18/spring-boot-3-5-6-available-now/
- https://spring.io/blog/2025/10/23/spring-boot-3-5-7-available-now/
- https://endoflife.date/spring-boot

---

### 1.3 Axon Framework 4.12.1

#### Current Choice Analysis

**Product Brief Statement:**
- Version: Axon Framework 4.12.1
- Purpose: CQRS/Event Sourcing implementation
- Rationale: Scalable, auditable systems with mature PostgreSQL integration

**Version Verification (2025-10-30):**
- **Stated Version:** 4.12.1
- **Latest 4.x Version:** 4.12.1 (still current)
- **Latest 5.x Version:** 5.0.0-M2 (Milestone 2)
- **5.0.0 Status:** Not yet released; milestone releases available

**Key 4.12.1 Features:**
- Native `JdbcEventStorageEngine` for PostgreSQL
- Mature Spring Boot integration
- Tested in production across many organizations
- Comprehensive documentation
- Large community knowledge base

#### Alternative Research

##### Alternative 1: Axon Framework 5.x (Milestone)
**Pros:**
- Modernized Configuration API (more modular)
- Improved developer experience
- Better Kotlin support
- Performance optimizations
- Future-proof architecture

**Cons:**
- **Milestone status** - Not production-ready (M2 as of 2025)
- Incomplete feature parity with 4.x
- Breaking changes in Configuration API
- Less community testing
- Migration effort required later

**Migration Considerations:**
- Annotation-based code mostly unchanged
- Configuration layer requires refactoring
- OpenRewrite recipes available for migration
- Feature parity not yet complete

**Verdict:** ⚠️ **Plan for Later** - 4.x is correct choice for MVP; plan 5.x migration for Phase 2 (post-ZEWSSP).

##### Alternative 2: EventSourcing + Custom CQRS
**Pros:**
- Full control over implementation
- No framework lock-in
- Lightweight
- Tailored to exact needs

**Cons:**
- 3-6 months development effort to build equivalent features
- Must implement event store, snapshots, sagas, handlers
- Testing and debugging complexity
- Reinventing the wheel
- **Contradicts MVP timeline goals**

**Verdict:** ❌ **Not Recommended** - Building from scratch contradicts 11-13 week MVP timeline and increases risk dramatically.

##### Alternative 3: Lagom Framework (Akka-based)
**Pros:**
- Reactive microservices framework
- Event Sourcing built-in
- CQRS patterns
- Persistent actors (Akka)

**Cons:**
- Different programming model (actor-based)
- Scala or Java (less Kotlin support)
- Learning curve steep
- Designed for distributed systems (overkill for single-server)
- Commercial Lightbend support required for production

**Verdict:** ❌ **Not Recommended** - Actor model inappropriate for single-server deployment; team learning curve too steep.

##### Alternative 4: EventStore + Custom Command Handling
**Pros:**
- Purpose-built event store database
- Excellent event sourcing features
- Projections built-in

**Cons:**
- Must build CQRS command layer
- No Kotlin/Spring integration
- Commercial licensing for production features
- Additional database to operate
- Not FOSS-only compatible

**Verdict:** ❌ **Not Recommended** - Violates FOSS-only requirement; integration effort too high.

#### Critical Evaluation

**Architectural Constraints:**
- ✅ **ppc64le Support:** JVM-based; architecture-agnostic
- ✅ **Air-Gapped Deployment:** No external dependencies
- ✅ **FOSS Requirement:** Apache 2.0 license
- ✅ **Single-Server Deployment:** Works perfectly with PostgreSQL
- ✅ **PostgreSQL Integration:** Native `JdbcEventStorageEngine`

**CQRS/ES Compatibility:**
- ✅ **Aggregate Design:** `@Aggregate` annotations
- ✅ **Command Handling:** `@CommandHandler` methods
- ✅ **Event Handling:** `@EventSourcingHandler` for aggregates
- ✅ **Event Processing:** `@EventHandler` for projections
- ✅ **Sagas:** `@Saga` for orchestration
- ✅ **Snapshots:** Automatic snapshotting support

**Multi-Tenancy Support:**
- ✅ **Context Propagation:** Message interceptors for tenant context
- ⚠️ **Tenant Isolation:** Requires custom implementation in aggregates
- ✅ **Async Processing:** Tenant context propagates to event processors

#### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Axon 5.x migration required | High | Medium | Plan migration post-MVP; use OpenRewrite |
| Framework lock-in | Medium | High | Hexagonal architecture isolates Axon to adapters |
| PostgreSQL performance limits | Medium | High | Proactive optimization, monitoring, migration path |
| Learning curve for team | High | High | Comprehensive training, documentation, pair programming |
| Complex event modeling errors | Medium | High | Extensive testing, event versioning strategy |

#### Axon 5.x Migration Planning

**Timeline Recommendation:**
- **Now - Epic 9:** Use Axon 4.12.1 (stable, proven)
- **ZEWSSP Migration:** Stay on 4.12.1 (stability critical)
- **Post-ZEWSSP (Q3 2026):** Evaluate Axon 5.0 GA status
- **Phase 2:** Migrate to Axon 5.x if GA released

**Migration Effort Estimate:**
- Configuration layer refactoring: 2-3 weeks
- Testing and validation: 2 weeks
- Documentation updates: 1 week
- **Total: ~1-1.5 months**

**Triggers for Migration:**
- Axon 5.0 GA released
- 6+ months community testing
- Critical features requiring 5.x
- Security vulnerabilities in 4.x

#### Recommendations

✅ **APPROVED** Axon Framework 4.12.1 for MVP with strong migration plan:

1. **Use Axon 4.12.1** through MVP and ZEWSSP migration
2. **Monitor Axon 5.x progress** - subscribe to AxonIQ blog
3. **Isolate Axon to Infrastructure Layer** - Hexagonal architecture critical
4. **Document event schemas rigorously** - migration requires clear contracts
5. **Plan Axon 5.x migration** for Q3-Q4 2026 (post-ZEWSSP)
6. **Budget 1-1.5 months** for Axon 5.x migration effort
7. **Use OpenRewrite recipes** when available for automated migration

**Learning Curve Mitigation:**
- Allocate 2-3 weeks for Majlinda's CQRS/ES training
- Redefine success metrics: <1 month for simple aggregate, <3 months for production
- Heavy investment in Scaffolding CLI to eliminate Axon boilerplate
- Comprehensive example aggregates in reference application

**Sources:**
- https://github.com/AxonFramework/AxonFramework/releases
- https://www.axoniq.io/blog/announcing-axon-framework-5-configuring-axon-made-easy
- https://docs.axoniq.io/reference-guide/release-notes/rn-axon-framework/rn-af-major-releases

---

## 2. Persistence Layer

### 2.1 PostgreSQL 16.1+ as Event Store

#### Current Choice Analysis

**Product Brief Statement:**
- Version: PostgreSQL 16.1+
- Implementation: Axon `JdbcEventStorageEngine`
- Design: Swappable Hexagonal adapter
- Rationale: Mature integration, operational simplicity, risk mitigation

**Version Verification (2025-10-30):**
- **Stated Version:** PostgreSQL 16.1+
- **Latest 16.x:** PostgreSQL 16.6 (October 2025)
- **Latest Stable:** PostgreSQL 17.1 (October 2025)
- **Latest Dev:** PostgreSQL 18 (in development)

**Key PostgreSQL 16 Features:**
- Logical replication improvements
- BRIN index enhancements (critical for event store)
- Better query planning
- Parallel query improvements
- Security enhancements

#### Alternative Research

##### Alternative 1: EventStoreDB
**Pros:**
- Purpose-built for event sourcing
- Excellent event streaming
- Built-in projections
- Optimized for append-only workloads
- Rich subscription mechanisms

**Cons:**
- **Commercial licensing** required for production (violates FOSS-only)
- No native Axon Framework integration
- 3-6 month custom integration effort
- Additional database to operate and monitor
- Smaller community than PostgreSQL

**Verdict:** ❌ **Not Viable** - Commercial licensing violates FOSS-only requirement; integration effort too high.

##### Alternative 2: NATS JetStream
**Pros:**
- High-performance streaming
- Low latency (<10ms)
- Built-in clustering
- Excellent for distributed systems
- Active development

**Cons:**
- **Zero official Axon integration** - requires custom implementation
- 3-6 month development effort to build event store
- Persistence model different from traditional event store
- Team learning curve steep
- Operational complexity

**Verdict:** ⚠️ **Future Migration Path** - Excellent choice for Phase 2 when PostgreSQL limits reached; preserve as migration option.

##### Alternative 3: Apache Kafka (Event Bus, Not Store)
**Pros:**
- Industry-standard event streaming
- Excellent scalability
- Axon extension available

**Cons:**
- **NOT an event store** - Axon Kafka extension is for event bus only
- Requires separate event store (PostgreSQL still needed)
- Operational complexity (Zookeeper/KRaft)
- Overkill for single-server deployment
- Retention policies incompatible with event sourcing

**Verdict:** ❌ **Not Viable as Event Store** - Kafka excels as event bus but not event store; doesn't solve the problem.

##### Alternative 4: Marten (PostgreSQL Event Sourcing Library)
**Pros:**
- Built on PostgreSQL
- Purpose-built for event sourcing
- Excellent .NET integration

**Cons:**
- **.NET only** - not available for JVM/Kotlin
- Would require porting to Kotlin
- No Axon Framework integration

**Verdict:** ❌ **Not Applicable** - Platform mismatch.

#### Critical Evaluation

**Architectural Constraints:**
- ✅ **ppc64le Support:** PostgreSQL fully supports ppc64le architecture
- ✅ **Air-Gapped Deployment:** No external dependencies
- ✅ **FOSS Requirement:** PostgreSQL license (permissive)
- ✅ **Single-Server Deployment:** Perfect fit
- ✅ **Swappable Adapter:** Hexagonal architecture enables future migration

**CQRS/ES Compatibility:**
- ✅ **Axon Integration:** Native `JdbcEventStorageEngine` support
- ✅ **Append-Only Storage:** Immutable events
- ✅ **Event Ordering:** Guaranteed per aggregate
- ✅ **Snapshots:** Stored in separate table
- ⚠️ **Scalability:** Degrades beyond ~1M events per aggregate without optimization

**Performance Characteristics:**

**Benchmarks (2025 Research):**
- **Write Throughput:** 3,000-7,000 events/second (clustered deployment)
- **Latency:** p95 <20ms (properly indexed)
- **Read Performance:** Excellent with BRIN indexes on timestamp
- **Scalability:** Proven to billions of events with partitioning

**Optimization Requirements:**
1. **Partitioning:** Time-based partitioning (monthly/quarterly)
2. **BRIN Indexes:** Block Range INdexes on sequence_number and timestamp
3. **Snapshotting:** Aggressive snapshot strategy (every 100-500 events)
4. **Connection Pooling:** HikariCP properly configured
5. **Vacuum Strategy:** Aggressive autovacuum for append-only tables

**Multi-Tenancy Support:**
- ✅ **Row-Level Security:** PostgreSQL RLS enforces tenant isolation
- ✅ **Partitioning by Tenant:** Possible for large tenants
- ✅ **Composite Indexes:** (tenant_id, aggregate_id, sequence_number)

#### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **Performance degradation at scale** | Medium-High | High | Proactive optimization plan; monitoring; migration triggers |
| Write lock contention | Low-Medium | Medium | Partition tables; optimize sequence generation |
| Query performance degradation | Medium | High | BRIN indexes; materialized views for complex queries |
| Storage growth | High | Low | Acceptable with disk space; archival strategy for old events |
| PostgreSQL expertise required | Medium | Medium | Comprehensive runbooks; DBA training |

#### PostgreSQL vs Streaming Event Store

**When PostgreSQL Works:**
- ✅ Event volumes <100K events/day per tenant
- ✅ Single-server deployment
- ✅ <100 aggregates per second write rate
- ✅ Operational simplicity priority

**When to Migrate to NATS/EventStoreDB:**
- ❌ Event volumes >1M events/day per tenant
- ❌ Multi-region active-active required
- ❌ Sub-10ms latency required
- ❌ >1000 aggregates per second write rate

**Migration Triggers (Defined KPIs):**
- Event store write latency p95 >200ms sustained
- Event processor lag >10 seconds sustained
- PostgreSQL CPU >80% sustained
- Query performance degradation impacting user experience

#### Recommendations

✅ **APPROVED** PostgreSQL 16.1+ as Event Store with strong optimization plan:

1. **Upgrade to PostgreSQL 16.6** (latest patch in 16.x line)
2. **Implement proactive optimization plan:**
   - Time-based partitioning from day 1
   - BRIN indexes on all event tables
   - Aggressive snapshotting strategy (100-500 events)
   - Monitor performance metrics continuously
3. **Define firm migration triggers:**
   - Write latency p95 >200ms sustained
   - Processor lag >10 seconds sustained
4. **Preserve migration path:**
   - Implement PostgreSQL as Hexagonal adapter (port)
   - Design NATS adapter alongside PostgreSQL adapter
   - Create migration runbook
5. **Plan NATS JetStream evaluation** for Phase 2
6. **Budget 4-6 weeks** for NATS migration if triggered

**PostgreSQL 17 Consideration:**
- PostgreSQL 17.1 released October 2025 with further improvements
- **Recommendation:** Stick with 16.x for MVP stability
- Plan 17.x upgrade post-ZEWSSP (Q3 2026)

**Production Event Volume Analysis Needed:**
- Extract anonymized metrics from DPCM/ZEWSSP
- Establish realistic performance benchmarks
- Validate PostgreSQL adequate for 2-3 years

**Sources:**
- https://www.postgresql.org/docs/16/release-16.html
- https://github.com/eugene-khyst/postgresql-event-sourcing
- https://softwaremill.com/reactive-event-sourcing-benchmarks-part-1-postgresql/
- https://risingwave.com/blog/eventstoredb-vs-postgresql-choose-the-best-database-for-your-needs/

---

### 2.2 Database Migration: Flyway

#### Current Choice Analysis

**Product Brief Statement:**
- Tool: Flyway
- Purpose: Database migration management
- Rationale: Not explicitly stated

**Version Verification:**
- Product Brief does not specify Flyway version
- **Latest Version:** Flyway 10.x (2025)
- **Recommendation:** Use latest stable release

#### Alternative Research

##### Alternative 1: Liquibase
**Pros:**
- Database-agnostic changelogs (XML, YAML, JSON, SQL)
- Rich rollback support (paid tiers)
- Advanced features (drift detection, flow orchestration)
- Strong enterprise adoption
- Sophisticated change tracking

**Cons:**
- More complex than Flyway
- XML configuration verbose
- Overkill for single-database projects
- Steeper learning curve
- Commercial features required for advanced capabilities

**Verdict:** ⚠️ **Viable Alternative** - Better for multi-database support, but Flyway simpler for PostgreSQL-only.

##### Alternative 2: Exposed Schema Migrations
**Pros:**
- Kotlin DSL
- Type-safe migrations
- Integrated with Exposed ORM

**Cons:**
- Limited to Exposed ORM users
- Less mature than Flyway/Liquibase
- Smaller community
- Not using Exposed for projections (using jOOQ)

**Verdict:** ❌ **Not Applicable** - Not using Exposed ORM.

#### Critical Evaluation

**Flyway vs Liquibase for EAF:**

| Feature | Flyway | Liquibase | EAF Need |
|---------|--------|-----------|----------|
| SQL-based migrations | ✅ Native | ⚠️ Supported | ✅ Preferred |
| Database-agnostic | ❌ No | ✅ Yes | ❌ PostgreSQL-only |
| Rollback support | ⚠️ Limited | ✅ Excellent | ⚠️ Rarely needed for events |
| Simplicity | ✅ Excellent | ⚠️ Complex | ✅ Important for team |
| Spring Boot integration | ✅ Native | ✅ Native | ✅ Required |
| Kotlin support | ✅ Java-based | ✅ Java-based | ✅ Both work |

#### Recommendations

✅ **APPROVED** Flyway for database migrations:

1. **Use Flyway** (simpler, SQL-native, PostgreSQL-focused)
2. **Naming convention:** `V{version}__{description}.sql` (e.g., `V001__create_event_store.sql`)
3. **Repeatable migrations:** Use `R__` prefix for views/functions
4. **Test migrations:** Run against Testcontainers PostgreSQL in CI/CD
5. **Rollback strategy:** Forward-only migrations (event sourcing philosophy)

**If Multi-Database Future:**
- Reevaluate Liquibase when supporting multiple databases
- Current single-database scope makes Flyway ideal

**Sources:**
- https://www.baeldung.com/liquibase-vs-flyway
- https://www.bytebase.com/blog/flyway-vs-liquibase/

---

### 2.3 Query Layer: jOOQ

#### Current Choice Analysis

**Product Brief Statement:**
- Tool: jOOQ
- Purpose: Type-safe SQL queries on projection tables
- Rationale: Type safety for read models

#### Alternative Research

##### Alternative 1: Exposed (JetBrains)
**Pros:**
- **Kotlin-first DSL** (not Java interop)
- Lightweight SQL framework
- Two layers: DSL + ORM
- Direct JDBC-based
- 20% faster development than traditional ORMs

**Cons:**
- Less comprehensive than jOOQ
- Smaller ecosystem
- Limited advanced SQL features
- No code generation

**Verdict:** ⚠️ **Viable Alternative** - More Kotlin-idiomatic but less feature-rich.

##### Alternative 2: Ktorm
**Pros:**
- Kotlin-native SQL DSL
- Pleasant to use
- No code generation needed
- Simple DB interactions

**Cons:**
- Limited database functionality vs jOOQ
- Manual table definitions
- Smaller community
- Less mature

**Verdict:** ⚠️ **Viable for Simple Cases** - Good for straightforward queries but jOOQ more powerful.

##### Alternative 3: Spring Data JPA
**Pros:**
- Repository pattern
- Spring Boot integration
- Query derivation from method names
- Large community

**Cons:**
- ORM overhead inappropriate for CQRS read models
- N+1 query problems
- Lazy loading issues
- Less control over SQL
- Not optimized for projection tables

**Verdict:** ❌ **Not Recommended** - ORM anti-pattern for CQRS projections.

#### Critical Evaluation

**jOOQ Strengths:**
- ✅ **Type-safe SQL** - Compile-time query validation
- ✅ **Code generation** - Tables/records from schema
- ✅ **SQL-first** - Embraces SQL, doesn't hide it
- ✅ **Rich DSL** - Complex queries supported
- ✅ **PostgreSQL optimization** - Database-specific features

**jOOQ vs Exposed vs Ktorm:**

| Feature | jOOQ | Exposed | Ktorm | EAF Need |
|---------|------|---------|-------|----------|
| Type safety | ✅ Excellent | ✅ Good | ✅ Good | ✅ Critical |
| Code generation | ✅ Yes | ❌ No | ❌ No | ✅ Preferred |
| SQL richness | ✅ Excellent | ⚠️ Limited | ⚠️ Limited | ✅ Important |
| Kotlin-native | ❌ Java-first | ✅ Yes | ✅ Yes | ⚠️ Nice to have |
| Learning curve | ⚠️ Moderate | ✅ Easy | ✅ Easy | ⚠️ Moderate OK |

#### Recommendations

✅ **APPROVED** jOOQ for projection queries:

1. **Use jOOQ** for all projection table queries
2. **Generate code** from Flyway migrations automatically
3. **Embrace SQL** - Don't fight jOOQ's SQL-first approach
4. **Separate read models** - Keep projection queries isolated from aggregates
5. **Consider Exposed** for very simple projections if jOOQ overhead unwanted

**Code Generation Strategy:**
- Run jOOQ code generation after Flyway migrations
- Commit generated code to repository
- CI/CD validates generated code matches schema

**Sources:**
- https://techlab.bol.com/en/blog/bye-bye-hibernate-discovering-alternatives-to-hibernate-in-kotlin/
- https://slack-chats.kotlinlang.org/t/5171246/anyone-got-extensive-experience-with-jooq-and-ktorm-i-like-s

---

## 3. Security Stack

### 3.1 Keycloak 26.0 (OIDC Authentication)

#### Current Choice Analysis

**Product Brief Statement:**
- Version: Keycloak 26.0
- Purpose: OIDC identity & access management
- Integration: Spring Security OAuth2 Resource Server
- Features: 10-layer JWT validation

**Version Verification (2025-10-30):**
- **Stated Version:** Keycloak 26.0
- **Latest 26.x:** Keycloak 26.4.0 (September 2025)
- **Status:** Actively maintained
- **Architecture:** Quarkus-based (modern, lightweight)

**Key Keycloak 26.x Features:**
- DPoP (Demonstrating Proof-of-Possession) support
- User registration standard support (`prompt=create`)
- Step-up authentication (ACR values)
- Request parameter length limits (4000 chars)
- Comprehensive OpenID Connect specifications

#### Alternative Research

##### Alternative 1: Open Source Self-Hosted IAM

**Auth0 (Commercial):**
- ❌ **Not FOSS** - Violates FOSS-only requirement
- Excellent developer experience but commercial licensing

**Ory Hydra:**
- ✅ FOSS (Apache 2.0)
- OAuth 2.0 and OpenID Connect provider
- Cloud-native, API-first
- Smaller feature set than Keycloak

**Pros:**
- Lightweight
- Modern architecture
- API-first design

**Cons:**
- No admin UI (separate Ory Kratos needed)
- Less comprehensive than Keycloak
- Smaller community
- Learning curve for team

**Verdict:** ⚠️ **Viable but More Work** - Keycloak more "batteries-included"

##### Alternative 2: Spring Authorization Server
**Pros:**
- Native Spring Boot integration
- FOSS (Apache 2.0)
- Full control over implementation

**Cons:**
- More work to implement user management
- No admin UI
- Must build user registration, password reset, etc.
- 2-3 months development effort

**Verdict:** ❌ **Too Much Work** - Contradicts MVP timeline.

##### Alternative 3: Casdoor
**Pros:**
- FOSS identity management
- Modern UI
- Multi-tenancy support

**Cons:**
- Less mature than Keycloak
- Smaller community
- Less documentation

**Verdict:** ❌ **Less Proven** - Keycloak safer choice.

#### Critical Evaluation

**Architectural Constraints:**
- ⚠️ **ppc64le Support:** **CRITICAL ISSUE** - No official Keycloak Docker images for ppc64le
- ✅ **Air-Gapped Deployment:** Runs standalone
- ✅ **FOSS Requirement:** Apache 2.0 license
- ✅ **Single-Server Deployment:** Embedded database option (PostgreSQL recommended)

**CQRS/ES Compatibility:**
- ✅ **Stateless JWT:** Perfect for event-driven systems
- ✅ **RS256 Signatures:** Public key validation in application
- ✅ **Custom Claims:** Tenant ID, roles in JWT

**Multi-Tenancy Support:**
- ✅ **Realms:** Separate realms per tenant possible
- ✅ **Custom Claims:** Tenant ID in JWT
- ⚠️ **Realm per Tenant:** Scalability limits (~100s of realms)
- ✅ **Single Realm:** Tenant ID as custom claim (recommended)

**10-Layer JWT Validation Assessment:**

Product Brief specifies comprehensive validation:
1. ✅ Format validation (3-part structure)
2. ✅ Signature validation (RS256)
3. ✅ Algorithm validation (RS256 only)
4. ✅ Claim schema validation
5. ✅ Time-based validation (exp, iat, nbf)
6. ✅ Issuer/Audience validation
7. ✅ Revocation check (Redis blacklist)
8. ✅ Role validation
9. ✅ User validation (active)
10. ✅ Injection detection (SQL/XSS patterns)

**Evaluation:** ✅ All layers implementable with Keycloak + Spring Security

#### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **ppc64le Docker image unavailable** | High | Critical | **Build custom ppc64le images**; test thoroughly |
| Keycloak complexity | Medium | Medium | Comprehensive documentation; training |
| Version upgrade breaking changes | Low | Medium | Pin version; test upgrades in staging |
| Admin UI learning curve | Low | Low | Admin training; runbooks |
| Single point of failure | Medium | High | HA deployment with PostgreSQL replication |

#### ppc64le Architecture Challenge

**CRITICAL FINDING:**
- Keycloak does **NOT** publish official ppc64le Docker images
- arm64 images available as of Keycloak 18+
- ppc64le requires **custom builds**

**Mitigation Strategy:**
1. **Build custom ppc64le Keycloak images:**
   - Use Keycloak source from GitHub
   - Cross-compile for ppc64le using Docker buildx
   - Test extensively on ppc64le hardware
   - Automate builds in CI/CD

2. **Verify Quarkus ppc64le support:**
   - Keycloak 26 uses Quarkus
   - Validate Quarkus supports ppc64le

3. **Alternative: x86 emulation:**
   - Run amd64 Keycloak on ppc64le using emulation
   - Performance penalty acceptable for auth workload
   - Not ideal but workable

**Recommended Action:**
- **Test ppc64le build** during Epic 3 (Authentication)
- Budget 1-2 weeks for ppc64le image creation and testing
- Document build process comprehensively
- Create CI/CD pipeline for multi-arch builds

#### Recommendations

⚠️ **APPROVED WITH CRITICAL MITIGATION** - Keycloak 26.0 with ppc64le custom builds:

1. **Upgrade to Keycloak 26.4.0** (latest in 26.x line)
2. **Build custom ppc64le Docker images** during Epic 3
3. **Test ppc64le deployment** on target hardware early
4. **Use PostgreSQL backend** (not embedded H2) for production
5. **Single Realm + Tenant Claim** approach for multi-tenancy
6. **Implement all 10 JWT validation layers** as specified
7. **Document Keycloak admin procedures** extensively

**HA Configuration:**
- Use PostgreSQL streaming replication
- Keycloak stateless (JWT-based)
- Load balancer for multiple Keycloak instances (optional)

**Sources:**
- https://www.keycloak.org/2025/09/keycloak-2640-released
- https://keycloak.discourse.group/t/question-is-there-a-keycloak-docker-container-for-linux-arm64-arch/6754
- https://groups.google.com/g/keycloak-user/c/ZJJ3GWZqArI (ppc64le discussion)

---

### 3.2 JWT Validation Strategy

#### Current Choice Analysis

**Product Brief Statement:**
- 10-layer JWT validation standard
- RS256 signature validation with Keycloak public keys
- Redis blacklist for revocation checking
- Injection detection for SQL/XSS patterns

#### Best Practices Assessment (2025)

**Industry Standards:**
- ✅ **Format validation** - Standard
- ✅ **RS256 signatures** - Industry best practice (avoid HS256 for distributed systems)
- ✅ **Algorithm validation** - CRITICAL (prevents JWT alg=none attacks)
- ✅ **Time-based validation** - Standard
- ✅ **Issuer/Audience** - OWASP recommended
- ⚠️ **Revocation check** - Good but adds latency
- ✅ **Role validation** - Application-specific
- ⚠️ **User active check** - Adds database lookup
- ⚠️ **Injection detection** - Rarely needed if claims validated

**Performance Considerations:**

| Validation Layer | Performance Impact | Mitigation |
|------------------|-------------------|------------|
| Format, algorithm, time | Negligible | In-memory |
| Signature (RS256) | Low (~1-5ms) | Cache public keys |
| Revocation (Redis) | Medium (~5-10ms) | Redis cache; short TTLs |
| User validation (DB) | High (~10-50ms) | Cache user status; consider removing |
| Injection detection | Low (~1-2ms) | Regex patterns |

**Total JWT Validation Overhead:** ~20-70ms per request

#### Recommendations

✅ **APPROVED** with optimizations:

1. **Implement all 10 layers** as specified (comprehensive security)
2. **Optimize performance:**
   - Cache Keycloak public keys (refresh every 1 hour)
   - Redis connection pooling
   - Consider removing user active check (rely on token TTL)
   - Short JWT TTLs (5-15 minutes) to minimize revocation need
3. **Monitoring:**
   - Track JWT validation time (alert if >100ms p95)
   - Monitor Redis hit rate
   - Alert on validation failures (potential attack)

**Trade-offs:**
- Security vs Performance: 10 layers comprehensive but adds latency
- Recommendation: Accept latency for MVP; optimize if user experience impacted

---

## 4. Workflow Engine

### 4.1 Flowable BPMN 7.1

#### Current Choice Analysis

**Product Brief Statement:**
- Tool: Flowable BPMN 7.1
- Purpose: Workflow orchestration (replacing legacy Dockets)
- Integration: Bidirectional bridge with Axon
- Scope: MVP defers advanced Dockets features

**Version Verification (2025-10-30):**
- **Stated Version:** Flowable 7.1
- **Latest Version:** Likely 7.x (specific patch unclear from search)
- **Status:** Actively maintained

#### Alternative Research

##### Alternative 1: Camunda Platform 8
**Pros:**
- Modern cloud-native architecture
- Excellent BPMN tooling
- Strong community
- Advanced features

**Cons:**
- **Camunda 7 EOL approaching** (October 2025 - community updates only; ~2027 support ends)
- Camunda 8 requires Zeebe (distributed architecture)
- Overkill for single-server deployment
- Migration effort from Camunda 7 to 8 significant

**Verdict:** ⚠️ **Risky Choice** - Camunda 7 EOL imminent; Camunda 8 too complex for single-server.

##### Alternative 2: Temporal.io
**Pros:**
- Modern workflow orchestration
- Code-as-workflow (not XML)
- Excellent for microservices
- Durable execution model

**Cons:**
- Different paradigm (not BPMN)
- Learning curve steep
- Designed for distributed systems
- Operational complexity

**Verdict:** ❌ **Not Recommended** - Not BPMN; overkill for use case.

##### Alternative 3: Apache Airflow
**Pros:**
- Workflow orchestration
- Python-based
- Large community

**Cons:**
- Data pipeline focus (not business workflows)
- Not BPMN-compliant
- Python dependency (not JVM)

**Verdict:** ❌ **Wrong Use Case** - Data pipelines, not business workflows.

#### Critical Evaluation

**Flowable vs Camunda (2025):**

| Feature | Flowable | Camunda 7 | Camunda 8 | EAF Need |
|---------|----------|-----------|-----------|----------|
| BPMN 2.0 support | ✅ Full | ✅ Full | ✅ Full | ✅ Required |
| CMMN support | ✅ Yes | ❌ Dropped | ❌ No | ⚠️ Dockets needs |
| DMN support | ✅ Yes | ✅ Yes | ✅ Yes | ✅ Useful |
| Single-server | ✅ Perfect | ✅ Works | ❌ Complex | ✅ Critical |
| EOL status | ✅ Active | ⚠️ 2025-2027 | ✅ Active | ✅ Important |
| Spring Boot integration | ✅ Native | ✅ Native | ⚠️ Requires Zeebe | ✅ Required |
| Operational simplicity | ✅ Excellent | ✅ Good | ❌ Complex | ✅ Critical |

**Key Differences:**
- **Flowable:** Fork of Activiti; FOSS; supports BPMN, CMMN, DMN
- **Camunda 7:** EOL approaching; strong tooling but sunset
- **Camunda 8:** Cloud-native; requires Zeebe; distributed architecture

#### Architectural Constraints Assessment

- ✅ **ppc64le Support:** JVM-based; architecture-agnostic
- ✅ **Air-Gapped Deployment:** No external dependencies
- ✅ **FOSS Requirement:** Apache 2.0 license
- ✅ **Single-Server Deployment:** Perfect fit
- ✅ **PostgreSQL Integration:** Uses same database

**CQRS/ES Integration:**
- ✅ **Flowable → Axon:** Service tasks dispatch Axon commands
- ✅ **Axon → Flowable:** Event handlers signal BPMN processes
- ✅ **Compensation:** BPMN compensation events for saga rollback
- ✅ **Error Handling:** Boundary events for error scenarios

#### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Flowable learning curve | Medium | Medium | Training; comprehensive examples; "Dockets Pattern" template |
| Limited advanced features vs Camunda | Low | Low | MVP scope limited; Phase 2 for advanced features |
| Flowable community size | Low | Low | Sufficient for use case; active development |
| BPMN complexity for team | Medium | Medium | Start simple; incremental adoption |

#### Recommendations

✅ **APPROVED** Flowable BPMN 7.1:

1. **Use Flowable 7.1** (stable, appropriate for single-server)
2. **Avoid Camunda** (EOL approaching; Camunda 8 too complex)
3. **Create "Dockets Pattern" BPMN templates** for common scenarios
4. **Bidirectional Axon integration:**
   - Service tasks dispatch Axon commands
   - Event handlers signal BPMN processes
5. **Start simple:** Basic workflows in MVP; advanced features Phase 2
6. **Training:** BPMN 2.0 fundamentals for team
7. **Monitoring:** Flowable metrics integrated with Prometheus

**Dockets Migration Strategy:**
- MVP: Basic orchestration with Flowable
- Phase 2: Advanced features (visual builder, dynamic UI)
- Acceptable: Some Dockets features may require custom development

**Sources:**
- https://www.getmagical.com/blog/best-flowable-alternatives
- https://www.peerspot.com/products/comparisons/camunda_vs_flowable
- https://eximeebpms.org/blog/navigating-the-camunda-7-eol-alternatives-and-migration-strategies/

---

## 5. Testing Framework

### 5.1 Kotest vs JUnit 5

#### Current Choice Analysis

**Product Brief Statement:**
- Framework: Kotest
- Purpose: Integration-First testing philosophy
- Features: BDD-style specifications, Constitutional TDD

#### Alternative Research

##### Alternative 1: JUnit 5 (Jupiter)
**Pros:**
- Industry standard (70%+ adoption)
- Vast ecosystem and plugins
- Familiar to Java developers
- Rich documentation
- Parameterized tests
- Extensions model

**Cons:**
- Java-centric syntax
- Less Kotlin-idiomatic
- More verbose than Kotest
- Traditional testing approach

**Verdict:** ⚠️ **Viable for Java Teams** - But Kotest better for Kotlin-first projects.

##### Alternative 2: Spek Framework
**Pros:**
- BDD-style specifications
- Kotlin-native
- Works as JUnit 5 engine

**Cons:**
- **Deprecated/inactive** - limited development
- Smaller community than Kotest
- Less feature-rich

**Verdict:** ❌ **Not Recommended** - Kotest supersedes Spek.

#### Critical Evaluation

**Kotest vs JUnit 5 for Kotlin:**

| Feature | Kotest | JUnit 5 | EAF Need |
|---------|--------|---------|----------|
| Kotlin DSL | ✅ Native | ⚠️ Java-first | ✅ Preferred |
| Test styles | ✅ 10 styles | ⚠️ One style | ✅ Flexibility |
| BDD support | ✅ Excellent | ⚠️ Limited | ✅ CQRS scenarios |
| Matchers | ✅ `shouldBe()` | ⚠️ `assertEquals()` | ✅ More readable |
| Coroutine support | ✅ Native | ⚠️ Via extensions | ✅ Critical for async |
| CI/CD performance | ✅ 70% faster setup | ⚠️ Standard | ✅ Important |
| Learning curve | ⚠️ Moderate | ✅ Easy (familiar) | ⚠️ Team upskilling |

**Kotest Advantages for CQRS/ES:**
- ✅ **Given-When-Then:** Natural fit for command/event testing
- ✅ **Coroutine support:** Essential for async event processors
- ✅ **Matchers:** `shouldBe`, `shouldContain`, etc. more expressive
- ✅ **Property testing:** Excellent for aggregate invariants
- ✅ **Table-driven tests:** Parameterized scenarios

**Sample Kotest Test:**
```kotlin
class CreateWidgetCommandTest : FunSpec({
    test("given valid command, should create widget aggregate") {
        // Arrange
        val command = CreateWidgetCommand(...)

        // Act
        val result = commandGateway.send(command)

        // Assert
        result.get() shouldBe widgetId
        eventStore.readEvents(widgetId).shouldContain(WidgetCreatedEvent(...))
    }
})
```

#### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Kotest learning curve | Medium | Medium | Training; examples; team ramp-up time |
| Smaller community than JUnit | Low | Low | Community sufficient; active development |
| IDE support gaps | Low | Low | IntelliJ IDEA excellent support |
| Migration effort if switching | Low | Medium | Unlikely to switch; commit to Kotest |

#### Recommendations

✅ **APPROVED** Kotest for testing framework:

1. **Use Kotest** for all test types (unit, integration, E2E)
2. **Test styles:**
   - `FunSpec` for most tests (simple, clear)
   - `BehaviorSpec` for CQRS scenarios (Given-When-Then)
   - `StringSpec` for quick unit tests
3. **Matchers:** Use Kotest matchers extensively (`shouldBe`, `shouldThrow`, etc.)
4. **Property testing:** For aggregate invariant validation
5. **Training:** Allocate 3-5 days for team Kotest training
6. **Document patterns:** Create test examples in reference application

**Integration with Testcontainers:**
- Kotest + Testcontainers work excellently together
- Use Kotest `TestLifecycleListener` for container management

**Sources:**
- https://www.baeldung.com/kotlin/kotest-vs-junit-5
- https://github.com/kotest/kotest
- https://moldstud.com/articles/p-top-kotlin-integration-testing-frameworks-pros-cons-expert-recommendations

---

### 5.2 Testcontainers

#### Current Choice Analysis

**Product Brief Statement:**
- Tool: Testcontainers
- Purpose: Real dependencies for integration testing
- Philosophy: H2 forbidden; real PostgreSQL required

#### Alternative Research

##### Alternative 1: Docker Compose for Tests
**Pros:**
- Simple docker-compose.yml
- Familiar tooling
- Manual control

**Cons:**
- Not integrated with test lifecycle
- Manual startup/cleanup
- Less flexible than Testcontainers

**Verdict:** ❌ **Not Recommended** - Testcontainers more integrated.

##### Alternative 2: Embedded PostgreSQL (zonkyio/embedded-postgres)
**Pros:**
- Fast startup
- No Docker required

**Cons:**
- Not real PostgreSQL (fork)
- Diverges from production environment
- Limited to specific PostgreSQL versions

**Verdict:** ❌ **Violates Philosophy** - Not real dependencies.

##### Alternative 3: Arquillian
**Pros:**
- Java middleware testing platform
- Runtime environment management

**Cons:**
- More complex than Testcontainers
- Older approach
- Less active development

**Verdict:** ❌ **Not Recommended** - Testcontainers more modern.

#### Critical Evaluation

**Testcontainers Strengths:**
- ✅ **Real Dependencies:** Actual PostgreSQL, Keycloak, Redis
- ✅ **Test Lifecycle Integration:** Automatic container management
- ✅ **Multi-Language Support:** JVM, .NET, Go, Python, Node.js
- ✅ **Strong Ecosystem:** Modules for most databases/services
- ✅ **Active Development:** Industry standard for 2025

**Constitutional TDD Alignment:**
- ✅ Integration-First philosophy requires real dependencies
- ✅ Testcontainers enables 40-50% integration tests
- ✅ Fast feedback with Docker layer caching
- ✅ CI/CD performance acceptable (<3 min fast tests, <15 min full suite)

#### Recommendations

✅ **APPROVED** Testcontainers with best practices:

1. **Use Testcontainers** for PostgreSQL, Keycloak, Redis
2. **Container reuse:**
   - Singleton containers for test suite (start once)
   - Cleanup between tests (transaction rollback)
3. **Optimize startup:**
   - Docker layer caching
   - Parallel test execution
4. **CI/CD:**
   - Docker-in-Docker or Testcontainers Cloud
   - Ensure Docker available in CI environment
5. **Modules:**
   - `testcontainers-postgresql`
   - Custom Keycloak container
   - `testcontainers-redis`

**Sources:**
- https://testcontainers.com/
- https://www.docker.com/blog/testcontainers-best-practices/

---

### 5.3 Mutation Testing: Pitest

#### Current Choice Analysis

**Product Brief Statement:**
- Tool: Pitest mutation testing
- Target: 80% minimum coverage
- Purpose: Verify test quality (not just line coverage)

**CRITICAL FINDING (2025-10-30):**
- **Pitest Kotlin plugin DEPRECATED** - official plugin no longer maintained
- **Arcmutate (Commercial)** - recommended replacement by plugin author
- **Open-source Kotlin mutation testing has gaps**

#### Alternative Research

##### Alternative 1: Arcmutate (Commercial)
**Pros:**
- **Best Kotlin support** - inline functions, coroutines, language features
- Created by Pitest team
- Excellent Kotlin bytecode understanding
- Supports Spring, Git integration

**Cons:**
- **Commercial license** - violates FOSS-only requirement
- Pricing unclear
- Vendor lock-in

**Verdict:** ❌ **Commercial Licensing Conflict** - But most technically sound option.

##### Alternative 2: Pitest with Deprecated Kotlin Plugin
**Pros:**
- FOSS
- Some Kotlin support

**Cons:**
- **Deprecated** - no active maintenance
- Issues with Kotlin keywords (`when`, annotations)
- Coroutine support lacking
- Suboptimal for Kotlin projects

**Verdict:** ⚠️ **Use with Limitations** - Accept reduced mutation testing quality.

##### Alternative 3: No Mutation Testing
**Pros:**
- Simpler CI/CD
- Faster builds

**Cons:**
- **Product Brief specifies 80% mutation coverage** - requirement stated
- Reduces test quality verification
- Risks shipping weak tests

**Verdict:** ❌ **Violates Requirements** - Product Brief specifies mutation testing.

##### Alternative 4: Custom Kotlin Mutation Testing
**Pros:**
- Full control
- Kotlin-native

**Cons:**
- 3-6 months development effort
- Complex compiler integration
- Maintenance burden

**Verdict:** ❌ **Too Much Effort** - Unrealistic for MVP.

#### Critical Evaluation

**Mutation Testing Gap:**
- ⚠️ **Open-source Kotlin mutation testing significantly limited**
- ⚠️ **Arcmutate commercial** - best option but violates FOSS
- ⚠️ **Pitest Kotlin plugin deprecated** - suboptimal but FOSS

**Risk Assessment:**

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Weak mutation testing | High | Medium | Use Pitest despite limitations; focus on integration tests |
| Commercial tool needed | Medium | Low | Budget exception for Arcmutate if critical |
| Developer frustration | Medium | Medium | Set realistic expectations; don't mandate 80% if tooling inadequate |

#### Recommendations

⚠️ **APPROVED WITH COMPROMISE** - Mutation testing limited by tooling:

**Option A: Use Pitest (FOSS, Limited)**
1. Accept deprecated Kotlin plugin limitations
2. **Reduce mutation coverage target** from 80% to **60-70%** (realistic)
3. Focus mutation testing on critical business logic
4. Supplement with integration tests (40-50% of suite)
5. Monitor Kotlin mutation testing ecosystem for improvements

**Option B: Commercial Exception for Arcmutate**
1. Request budget exception for Arcmutate license
2. Justification: Superior Kotlin support critical for quality
3. Achieves 80% mutation coverage as specified
4. Consider cost vs benefit

**Option C: Defer Mutation Testing to Phase 2**
1. Focus on integration tests for MVP
2. Evaluate mutation testing options post-MVP
3. Reevaluate when Kotlin mutation tools mature

**RECOMMENDED:** **Option A** (Pitest with reduced target)

**Rationale:**
- Maintains FOSS-only commitment
- Realistic given tooling limitations
- Integration-First philosophy provides safety net
- 60-70% mutation coverage still excellent

**Product Brief Adjustment Needed:**
- Current: "80% minimum coverage verified by mutation testing"
- **Recommended:** "60-70% mutation coverage on critical business logic; 85%+ line coverage verified by integration tests"

**Sources:**
- https://pitest.org/
- https://www.arcmutate.com/
- https://github.com/pitest/pitest-kotlin (deprecated)
- https://slack-chats.kotlinlang.org/t/540058/hi-folks-i-m-looking-for-an-equivalent-of-pitest-for-mutatio

---

## 6. Observability Stack

### 6.1 Prometheus + Grafana + OpenTelemetry

#### Current Choice Analysis

**Product Brief Statement:**
- **Metrics:** Prometheus with Micrometer
- **Visualization:** Grafana (dashboards deferred to Post-MVP)
- **Tracing:** OpenTelemetry distributed tracing
- **Logging:** Structured JSON with Logback/Logstash encoder

**Version Verification:**
- Prometheus: Latest stable 2.x
- Grafana: Latest stable (11.x as of 2025)
- OpenTelemetry: 1.x stable

#### Alternative Research

##### Alternative 1: OpenTelemetry-Native Platforms (All-in-One)

**SigNoz (FOSS):**
- ✅ Open-source (MIT license)
- ✅ OpenTelemetry-native
- ✅ Unified logs, metrics, traces
- ✅ Datadog-like experience
- ⚠️ Additional service to deploy

**Verdict:** ⚠️ **Viable Alternative** - Consider for Phase 2 to reduce operational complexity.

**Grafana Cloud (SaaS):**
- ✅ Managed Prometheus + Grafana + Loki + Tempo
- ✅ Excellent integration
- ❌ SaaS (not on-premise)
- ❌ Commercial pricing

**Verdict:** ❌ **Not Applicable** - Air-gapped deployment requirement.

##### Alternative 2: ELK Stack (Elasticsearch, Logstash, Kibana)
**Pros:**
- Mature log aggregation
- Powerful search
- Comprehensive ecosystem

**Cons:**
- Heavy resource requirements (Elasticsearch)
- Complex operational overhead
- Overkill for single-server
- Less modern than OpenTelemetry

**Verdict:** ❌ **Too Heavy** - Not appropriate for single-server deployment.

##### Alternative 3: Loki + Grafana (Logs)
**Pros:**
- Lightweight log aggregation
- Integrates with Grafana
- Label-based indexing (like Prometheus)

**Cons:**
- Separate from Prometheus (additional service)
- Less powerful search than Elasticsearch

**Verdict:** ⚠️ **Consider for Phase 2** - Good complement to Prometheus.

#### Critical Evaluation

**Prometheus + Grafana + OpenTelemetry Assessment:**

| Component | Purpose | Pros | Cons | EAF Fit |
|-----------|---------|------|------|---------|
| **Prometheus** | Metrics | Industry standard, TSDB, efficient | Pull-based (requires /metrics endpoint) | ✅ Excellent |
| **Grafana** | Visualization | Powerful dashboards, multi-source | Dashboard config complexity | ✅ Excellent |
| **OpenTelemetry** | Tracing | Vendor-neutral, standard, growing | Setup complexity | ✅ Future-proof |
| **Micrometer** | Instrumentation | Spring Boot native, easy | JVM-only | ✅ Perfect |

**Architectural Constraints:**
- ✅ **ppc64le Support:** All components support ppc64le
- ✅ **Air-Gapped Deployment:** All services self-hosted
- ✅ **FOSS Requirement:** All Apache 2.0 / MIT
- ✅ **Single-Server Deployment:** Lightweight enough
- ⚠️ **Operational Complexity:** 3 services (Prometheus, Grafana, Jaeger/Tempo)

**Three Pillars of Observability:**
1. **Logs:** Structured JSON → Stdout → (Future: Loki)
2. **Metrics:** Micrometer → Prometheus → Grafana
3. **Traces:** OpenTelemetry → (Future: Jaeger/Tempo)

#### Recommendations

✅ **APPROVED** Prometheus + Grafana + OpenTelemetry with phased rollout:

**MVP (Epic 5):**
1. **Prometheus + Micrometer:**
   - `/actuator/prometheus` endpoint
   - JVM metrics (heap, GC, threads)
   - HTTP metrics (request rate, latency, errors)
   - Axon metrics (command processing, event lag)
   - Custom business metrics (tenant_id tag)
2. **Structured Logging:**
   - JSON format with Logback
   - `trace_id`, `tenant_id`, `service_name` in all logs
   - Correlation between logs and traces
3. **OpenTelemetry (basic):**
   - Auto-instrumentation for HTTP
   - Manual spans for critical operations
   - Trace propagation across Axon messages

**Post-MVP (Phase 2):**
4. **Grafana Dashboards:**
   - Golden signals (latency, traffic, errors, saturation)
   - Business metrics (aggregates created, events processed)
   - Tenant-specific dashboards
5. **Loki for Logs:**
   - Centralized log aggregation
   - Query logs by trace_id
6. **Jaeger/Tempo for Traces:**
   - Distributed tracing visualization
   - Performance analysis

**Alternatives to Consider:**
- **SigNoz (FOSS):** All-in-one OpenTelemetry platform; evaluate for Phase 2 to simplify stack
- **Grafana Tempo:** Lightweight trace backend; integrate with existing Grafana

**Sources:**
- https://www.dash0.com/comparisons/best-grafana-alternatives-2025
- https://uptrace.dev/blog/opentelemetry-compatible-platforms
- https://betterstack.com/community/comparisons/opentelemetry-tools/

---

## 7. Code Quality Tools

### 7.1 ktlint + Detekt + Konsist

#### Current Choice Analysis

**Product Brief Statement:**
- **ktlint 1.4.0:** Zero formatting violations
- **Detekt 1.23.7:** Zero static analysis violations
- **Konsist:** Spring Modulith boundary verification
- **Enforcement:** CI/CD pipeline + Git hooks

**Version Verification (2025-10-30):**
- **ktlint:** 1.4.0 recent; check for 1.4.x patches
- **Detekt:** 1.23.7; latest likely 1.23.x
- **Konsist:** Version unspecified

#### Alternative Research

##### Alternative 1: ktlint vs Detekt - Use One or Both?

**ktlint (Formatting):**
- Code style, indentation, spacing
- Auto-formatting capabilities
- Fast execution

**Detekt (Static Analysis):**
- Code complexity, code smells
- Maintainability issues
- Deeper code analysis

**Verdict:** ✅ **Use Both** - Complementary tools (no conflicts).

##### Alternative 2: SonarQube
**Pros:**
- Comprehensive quality analysis
- Security vulnerability detection
- Technical debt tracking
- Multi-language support

**Cons:**
- Server infrastructure required
- Commercial features needed for teams
- Heavier than ktlint + Detekt
- Overlaps with Detekt

**Verdict:** ⚠️ **Consider for Phase 2** - ktlint + Detekt sufficient for MVP.

##### Alternative 3: Spotless (Gradle)
**Pros:**
- Gradle plugin for formatting
- Multi-language support
- Integrates ktlint

**Cons:**
- Adds abstraction layer
- Less direct control

**Verdict:** ⚠️ **Optional Wrapper** - Direct ktlint + Detekt also fine.

#### Critical Evaluation

**ktlint + Detekt Best Practices (2025):**

**KotlinConf 2025 Session:**
- "Code Quality at Scale: Future-Proof Your Android Codebase with KtLint and Detekt"
- Indicates continued relevance and best practices

**Integration Strategy:**
1. **ktlint:** Formatting enforced pre-commit
2. **Detekt:** Static analysis in CI/CD
3. **Konsist:** Architectural rules validation

**Detekt with ktlint:**
- Detekt includes ktlint wrapper (`ktlint` rule set)
- Can run both from single Detekt execution
- Recommended for simplicity

**Git Hooks:**
- Pre-commit: ktlint auto-format
- Pre-push: Detekt analysis
- Prevents bad commits from entering repository

#### Recommendations

✅ **APPROVED** ktlint + Detekt + Konsist with optimizations:

1. **Use Detekt with embedded ktlint:**
   - Single tool invocation
   - Consistent configuration
2. **Configuration:**
   - `detekt.yml` with strict rules
   - ktlint official style guide
   - Konsist rules for Spring Modulith boundaries
3. **Enforcement:**
   - **Pre-commit:** ktlint auto-format (fast)
   - **CI/CD:** Detekt + Konsist (comprehensive)
   - **Zero-tolerance:** Build fails on violations
4. **IDE Integration:**
   - IntelliJ IDEA ktlint plugin
   - Detekt IntelliJ plugin
   - Auto-format on save
5. **Baseline:**
   - Generate Detekt baseline for existing code if needed
   - New code must pass all rules

**Konsist Architecture Rules:**
```kotlin
@Test
fun `domain layer must not depend on infrastructure` {
    Konsist.assertArchitecture {
        classes()
            .that { it.resideInPackage("..domain..") }
            .should { not.dependOn("..infrastructure..") }
    }
}

@Test
fun `commands must be in commands package` {
    Konsist.assertArchitecture {
        classes()
            .that { it.name.endsWith("Command") }
            .should { it.resideInPackage("..commands..") }
    }
}
```

**Sources:**
- https://github.com/detekt/detekt
- https://2025.kotlinconf.com/talks/814689/ (KotlinConf 2025)
- https://medium.com/@mohamad.alemicode/enforcing-code-quality-in-android-with-detekt-and-ktlint-a-practical-guide-907b57d047ec

---

## 8. Frontend Framework

### 8.1 React-Admin

#### Current Choice Analysis

**Product Brief Statement:**
- Framework: React-Admin with Material-UI
- Purpose: Operator portal (admin UI)
- Scope: MVP provides foundational CRUD; advanced features Post-MVP

#### Alternative Research

##### Alternative 1: Refine
**Pros:**
- **React-based** like React-Admin
- Headless (UI framework agnostic)
- React Query for data management
- Decoupled from UI components
- Growing community

**Cons:**
- Less batteries-included than React-Admin
- More configuration needed
- Smaller ecosystem

**Verdict:** ⚠️ **Viable Alternative** - More flexible but requires more setup.

##### Alternative 2: CoreUI Free React Admin Template
**Pros:**
- FOSS (MIT license)
- Bootstrap 5 + React 19
- Modern design
- Lightweight

**Cons:**
- Template (not framework)
- Manual CRUD implementation
- No data provider abstraction

**Verdict:** ❌ **Not Recommended** - React-Admin more batteries-included.

##### Alternative 3: Custom React + React Query
**Pros:**
- Full control
- Tailored to exact needs
- Modern stack

**Cons:**
- 2-3 months development effort
- Reinventing the wheel
- Contradicts MVP timeline

**Verdict:** ❌ **Too Much Work** - Framework reduces development time.

##### Alternative 4: Server-Side Admin (htmx + Thymeleaf)
**Pros:**
- Simple server-side rendering
- No SPA complexity
- Fast development

**Cons:**
- Less interactive than React
- Old-school approach
- Limited rich UI

**Verdict:** ❌ **Not Modern Enough** - React provides better UX.

#### Critical Evaluation

**React-Admin vs Refine:**

| Feature | React-Admin | Refine | EAF Need |
|---------|-------------|--------|----------|
| Data providers | ✅ Built-in | ✅ Built-in | ✅ Required |
| CRUD scaffolding | ✅ Excellent | ⚠️ Manual | ✅ Important |
| SSR/Next.js support | ❌ No | ✅ Yes | ❌ Not needed |
| UI framework | ✅ Material-UI | ⚠️ Any (headless) | ✅ Preference |
| Learning curve | ✅ Easy | ⚠️ Moderate | ✅ Team skill |
| Batteries-included | ✅ Excellent | ⚠️ Less | ✅ MVP timeline |

**Scaffolding CLI Integration:**
- `scaffold ra-resource` generates React-Admin components
- Reduces boilerplate significantly
- Consistent UI across modules

#### Recommendations

✅ **APPROVED** React-Admin for operator portal:

1. **Use React-Admin** for MVP admin portal
2. **Material-UI** for consistent design system
3. **Custom data provider:**
   - Integrate with EAF REST APIs
   - Tenant context in API calls
   - JWT authentication
4. **Scaffolding CLI integration:**
   - Generate CRUD components automatically
   - Consistent patterns across modules
5. **Advanced features Post-MVP:**
   - Complex dashboards
   - Advanced filtering
   - Bulk operations
   - Relationship management

**Alternative Consideration:**
- Evaluate **Refine** if customization needs increase in Phase 2
- React-Admin sufficient for MVP scope

**Sources:**
- https://refine.dev/blog/refine-vs-react-admin/
- https://marmelab.com/blog/2023/07/04/react-admin-vs-refine.html
- https://blog.forestadmin.com/forest-admin-vs-react-admin/

---

## 9. Build System

### 9.1 Gradle vs Maven

#### Current Choice Analysis

**Product Brief Statement:**
- Build System: Gradle multi-module monorepo
- Structure: `framework/`, `products/`, `shared/`, `apps/`
- Convention Plugins: `build-logic/` for configuration
- Version Catalog: `libs.versions.toml`

#### Alternative Research

##### Alternative 1: Maven Multi-Module
**Pros:**
- Industry standard
- XML familiar
- Stable
- Large ecosystem

**Cons:**
- **Slower builds** - no incremental compilation
- **XML verbose** - large projects unwieldy
- **Inflexible** - customization difficult
- **Not ideal for Kotlin** - Kotlin DSL in Gradle superior

**Verdict:** ❌ **Not Recommended** - Gradle significantly better for Kotlin multi-module.

##### Alternative 2: Gradle Kotlin DSL vs Groovy DSL
**Kotlin DSL Pros:**
- Type-safe build scripts
- IDE auto-completion
- Kotlin consistency

**Groovy DSL Pros:**
- More concise
- Legacy ecosystem

**Verdict:** ✅ **Kotlin DSL** - Consistency with application code.

#### Critical Evaluation

**Gradle Advantages for Kotlin Multi-Module:**

| Feature | Gradle | Maven | EAF Need |
|---------|--------|-------|----------|
| **Build speed** | ✅ Incremental, cache | ❌ Full rebuilds | ✅ Critical |
| **Kotlin DSL** | ✅ Native | ❌ No | ✅ Consistency |
| **Flexibility** | ✅ Excellent | ❌ Rigid | ✅ Customization |
| **Multi-module** | ✅ Excellent | ⚠️ Works | ✅ Monorepo |
| **Convention plugins** | ✅ Yes | ❌ No | ✅ DRY config |
| **Version catalog** | ✅ Built-in | ❌ BOM only | ✅ Central deps |

**Gradle Performance:**
- Incremental compilation: Only changed files
- Build cache: Reuse outputs across machines
- Parallel execution: Multi-module builds faster

**Best Practices (2025):**
- **Kotlin DSL:** All build scripts `.gradle.kts`
- **Convention Plugins:** Share configuration across modules
- **Version Catalog:** `libs.versions.toml` for centralized versions
- **Build Cache:** Enable for CI/CD

#### Recommendations

✅ **APPROVED** Gradle with Kotlin DSL:

1. **Use Gradle 8.x** (latest stable)
2. **Kotlin DSL** for all build scripts
3. **Convention Plugins** in `build-logic/`:
   - `kotlin-conventions.gradle.kts` (common Kotlin config)
   - `spring-conventions.gradle.kts` (Spring Boot config)
   - `testing-conventions.gradle.kts` (test config)
4. **Version Catalog** (`libs.versions.toml`):
   - Centralized dependency versions
   - Consistent across all modules
5. **Build Cache:**
   - Local cache for developer machines
   - Remote cache for CI/CD (consider Gradle Enterprise)
6. **Parallel Builds:**
   - Enable `org.gradle.parallel=true`
   - Configure max workers

**Sample Structure:**
```
eaf/
├── build-logic/
│   └── src/main/kotlin/
│       ├── kotlin-conventions.gradle.kts
│       ├── spring-conventions.gradle.kts
│       └── testing-conventions.gradle.kts
├── framework/
│   ├── core/
│   ├── cqrs/
│   └── security/
├── products/
│   ├── zewssp/
│   └── dpcm/
├── shared/
│   └── common/
├── apps/
│   └── reference-app/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
    └── libs.versions.toml
```

**Sources:**
- https://www.dhiwise.com/post/kotlin-maven-vs-gradle-which-build-tool-is-right
- https://buildkite.com/resources/comparison/maven-vs-gradle/
- https://gradlehero.com/maven-vs-gradle-comparison/

---

## 10. Multi-Architecture Support

### 10.1 ppc64le + arm64 + amd64 Docker Images

#### Current Choice Analysis

**Product Brief Statement:**
- **Mandatory Architectures:**
  - amd64 (x86_64) - Primary deployment
  - arm64 (aarch64) - Apple Silicon dev + emerging servers
  - ppc64le (POWER9+) - Specific customer requirement
- **Requirement:** All Docker images and binaries must be multi-architecture

#### Critical Evaluation by Component

##### ✅ **FULL SUPPORT:**
1. **PostgreSQL:**
   - ✅ Official ppc64le images available
   - ✅ `ppc64le/postgres` Docker Hub repository
   - ✅ Full feature parity

2. **Spring Boot Applications:**
   - ✅ JVM bytecode architecture-agnostic
   - ✅ Multi-arch base images (eclipse-temurin, etc.)
   - ✅ Build with Docker buildx

3. **Kotlin / JVM 21:**
   - ✅ JVM bytecode architecture-agnostic
   - ✅ OpenJDK 21 supports ppc64le

4. **Prometheus:**
   - ✅ Multi-arch support including ppc64le

5. **Grafana:**
   - ✅ Multi-arch support including ppc64le

6. **Redis:**
   - ✅ Multi-arch support including ppc64le

##### ⚠️ **REQUIRES CUSTOM BUILDS:**
1. **Keycloak:**
   - ❌ **NO official ppc64le Docker images**
   - ✅ arm64 images available since Keycloak 18
   - ⚠️ **Mitigation:** Build custom ppc64le images from source
   - ✅ Quarkus-based (Keycloak 26) supports ppc64le compilation

2. **Flowable:**
   - ⚠️ **Unclear official ppc64le support**
   - ✅ JVM-based (should work)
   - ⚠️ **Mitigation:** Test thoroughly; build custom if needed

##### ❓ **NEEDS VERIFICATION:**
1. **Axon Framework:**
   - ✅ JVM-based (should work)
   - ⚠️ Needs testing on ppc64le hardware

2. **React-Admin Frontend:**
   - ✅ Static assets (architecture-agnostic)
   - ✅ Node.js builds support ppc64le

#### Risk Assessment

| Component | ppc64le Risk | Mitigation | Effort |
|-----------|--------------|------------|--------|
| **Keycloak** | High | Custom Docker builds | 1-2 weeks |
| **Flowable** | Medium | Test + custom build if needed | 1 week |
| **PostgreSQL** | None | Official images | 0 |
| **Spring Boot** | None | JVM bytecode | 0 |
| **Prometheus/Grafana** | None | Multi-arch support | 0 |
| **Redis** | None | Multi-arch support | 0 |

#### Multi-Arch Build Strategy

**Docker Buildx (Multi-Architecture):**
```bash
docker buildx build \
  --platform linux/amd64,linux/arm64,linux/ppc64le \
  -t eaf/keycloak:26.4.0 \
  --push \
  .
```

**CI/CD Pipeline:**
1. **Build Matrix:**
   - Build for amd64, arm64, ppc64le
   - Run tests on each architecture
2. **Registry:**
   - Push multi-arch manifests
   - Single image tag works across architectures
3. **Testing:**
   - Automated tests on emulated ppc64le (QEMU)
   - Manual validation on real ppc64le hardware (IBM Power)

#### Recommendations

⚠️ **APPROVED WITH CRITICAL ACTIONS** - Multi-architecture support requires custom builds:

1. **Immediate Actions (Epic 1-2):**
   - Verify PostgreSQL ppc64le images work
   - Test Spring Boot multi-arch builds
   - Document multi-arch build process

2. **Epic 3 (Authentication):**
   - **Build custom Keycloak ppc64le images** (budget 1-2 weeks)
   - Test Keycloak thoroughly on ppc64le
   - Automate Keycloak multi-arch builds in CI/CD

3. **Epic 5 (Observability):**
   - Verify Prometheus/Grafana ppc64le images

4. **Epic 6 (Flowable):**
   - Test Flowable on ppc64le
   - Build custom images if official unavailable

5. **ppc64le Testing:**
   - Obtain IBM Power hardware access for testing
   - Automated QEMU emulation in CI/CD (slow but validates)
   - Quarterly validation on real ppc64le hardware

6. **Documentation:**
   - Multi-arch build runbook
   - ppc64le troubleshooting guide
   - Customer deployment guide for each architecture

**Custom Build Repository:**
```
eaf-docker-images/
├── keycloak/
│   ├── Dockerfile.ppc64le
│   ├── build.sh
│   └── README.md
├── flowable/
│   └── (if needed)
└── CI/
    └── build-multiarch.yml
```

**Alternative: Architecture Emulation:**
- Run amd64 images on ppc64le using QEMU
- Performance penalty (~50% slower)
- **Not recommended** but fallback option

**Sources:**
- https://hub.docker.com/r/ppc64le/postgres/
- https://keycloak.discourse.group/t/question-is-there-a-keycloak-docker-container-for-linux-arm64-arch/6754
- https://groups.google.com/g/keycloak-user/c/ZJJ3GWZqArI
- https://devopscube.com/build-multi-arch-docker-image/

---

## 11. Architecture Patterns

### 11.1 Hexagonal Architecture + DDD + CQRS/ES

#### Current Choice Analysis

**Product Brief Statement:**
- **Hexagonal Architecture** (Ports & Adapters)
- **Domain-Driven Design** (DDD)
- **CQRS/Event Sourcing** via Axon Framework
- **Spring Modulith 1.4.3** for boundary enforcement

#### Industry Best Practices (2025)

**Hexagonal Architecture + DDD + CQRS:**
- ✅ **Proven pattern** for complex domains
- ✅ **Isolates business logic** from infrastructure
- ✅ **Testability** - mock ports easily
- ✅ **Flexibility** - swap adapters (PostgreSQL → NATS)

**Recent Case Studies (2025):**
- Retail e-commerce: 64% read performance improvement with CQRS
- Banking: 73% better performance under variable load
- Energy management: Scalable, reliable system with Axon + CQRS

#### Spring Modulith Evaluation

**Current Choice:**
- Spring Modulith 1.4.3 (August 2025)
- Latest: Spring Modulith 1.4.4 and 2.0 RC1 (October 2025)

**Alternatives to Spring Modulith:**
1. **ArchUnit:**
   - ✅ Architecture rules as tests
   - ❌ Time-consuming configuration
   - ❌ Manual rule maintenance
   - **Verdict:** ⚠️ Better than nothing but Spring Modulith superior

2. **jMolecules:**
   - ✅ DDD annotations
   - ✅ Used by Spring Modulith internally
   - ⚠️ Complementary to Spring Modulith

3. **Java Module System (JPMS):**
   - ✅ Language-level modules
   - ❌ Complex to configure
   - ❌ Tooling immature
   - **Verdict:** ❌ Premature for Kotlin projects

#### Recommendations

✅ **APPROVED** Hexagonal + DDD + CQRS + Spring Modulith:

1. **Upgrade to Spring Modulith 1.4.4** (latest stable)
2. **Module Structure:**
   ```
   framework.core/
   ├── domain/          (pure business logic)
   ├── application/     (use cases, command handlers)
   ├── ports/           (interfaces)
   └── adapters/
       ├── persistence/ (PostgreSQL, Axon)
       ├── api/         (REST controllers)
       └── messaging/   (Axon event handlers)
   ```
3. **Spring Modulith Verification:**
   ```kotlin
   @SpringBootTest
   class ModularityTests {
       @Test
       fun `verify module structure`() {
           ApplicationModules
               .of(EafApplication::class.java)
               .verify()
       }
   }
   ```
4. **Boundary Enforcement:**
   - Explicit module dependencies in `spring-modulith.yml`
   - Fail build on violations (Konsist + Spring Modulith)

5. **Event-Driven Modules:**
   - Modules communicate via application events
   - Loose coupling between modules

**Sources:**
- https://spring.io/blog/2025/10/27/spring-modulith-2-0-rc1-1-4-4-and-1-3-10-released/
- https://www.javacodegeeks.com/2025/10/cqrs-and-event-sourcing-in-practice-building-scalable-systems.html
- https://medium.com/@ishansoninitj/creating-a-multi-module-monolith-using-spring-modulith-f83053736762

---

### 11.2 Active-Active vs Active-Passive HA/DR

#### Critical Discrepancy in Product Brief

**Product Brief Statement (Section: Technical Considerations):**
> "High Availability Requirements: Active-Passive architecture with automated failover <2 minutes"

**Product Brief Statement (Section: Problem Statement - Focus Areas):**
> "Multi-arch: Active-Active HA/DR (NOT Active-Passive!)"

**CRITICAL INCONSISTENCY DETECTED**

#### Industry Best Practices (2025)

**Active-Passive Recommendations:**
- ✅ **80% of enterprises** use Active-Passive for databases
- ✅ **Predictable, cheaper, compliance-friendly**
- ✅ **Appropriate for:** Banks, e-commerce, single-region deployments

**Active-Active Recommendations:**
- ✅ **Global SaaS with 24/7 writes** (Slack, Zoom, multiplayer games)
- ✅ **Low-latency apps** where every region accepts writes
- ⚠️ **Complex conflict resolution** required
- ⚠️ **Higher operational cost**

**PostgreSQL HA Options:**

**Active-Passive (Recommended for EAF):**
- ✅ **Patroni:** Automated PostgreSQL failover
- ✅ **Streaming Replication:** Async or sync replication
- ✅ **HAProxy / Pgpool-II:** Connection pooling and failover
- ✅ **Simple, proven, reliable**

**Active-Active (Complex):**
- ⚠️ **pgactive (AWS RDS):** Multi-master writes with conflict policies
- ⚠️ **Conflict resolution:** Last-write-wins, timestamp-based, custom
- ❌ **Not natively supported in Azure / GCP managed services**
- ❌ **Event sourcing append-only** makes multi-master challenging

#### Critical Evaluation

**Event Sourcing + Active-Active:**
- ⚠️ **Append-only event store** makes active-active simpler than traditional DB
- ⚠️ **Conflict-free** if events timestamped and merged
- ⚠️ **BUT:** Command validation becomes complex (duplicate commands)

**EAF Use Case:**
- ✅ **Single-server deployment** specified in Product Brief
- ✅ **On-premise customer installations**
- ❌ **NOT global SaaS with multi-region writes**

**Recommendation:**
- **Active-Passive is correct** for EAF v1.0 use case
- **Active-Active inappropriate** for single-server deployment model

#### Recommendations

🚨 **CLARIFICATION REQUIRED** - Product Brief inconsistency:

**Recommended Resolution:**
1. **Confirm requirement** with product owner
2. **Most likely intent:** Active-Passive HA/DR for v1.0
3. **Active-Active** may be long-term vision (Phase 2, cloud deployment)

**Recommended HA Architecture (Active-Passive):**
1. **Primary Server:**
   - PostgreSQL primary
   - Spring Boot application
   - Keycloak
   - Flowable

2. **Standby Server:**
   - PostgreSQL streaming replica (sync or async)
   - Spring Boot application (standby)
   - Automated failover with Patroni

3. **Failover:**
   - Patroni detects primary failure
   - Promotes standby PostgreSQL to primary
   - Spring Boot application switches connections
   - RTO <2 minutes (as specified)

4. **Disaster Recovery:**
   - PostgreSQL WAL archiving to remote storage
   - Point-in-Time Recovery (PITR)
   - RPO <15 minutes (as specified)
   - Quarterly DR drills

**Active-Active (Future Phase 2):**
- Requires multi-region deployment
- NATS JetStream for event replication
- Complex conflict resolution
- Higher operational cost
- **Not appropriate for v1.0 single-server model**

**Sources:**
- https://www.pgedge.com/blog/understanding-the-differences-and-advantages-of-active-active-vs-active-passive-replication
- https://medium.com/@vishalpriyadarshi/postgresql-ha-in-the-cloud-active-active-or-active-passive-692724024d3e
- https://aerospike.com/blog/active-active-vs-active-passive/

---

## 12. Technology Decision Matrix

### Summary Table: Current Choices vs Alternatives

| Category | Current Choice | Version Status | Alternatives Evaluated | Recommendation |
|----------|----------------|----------------|------------------------|----------------|
| **Language** | Kotlin 2.2.20 | ⚠️ 2.2.21 available | Java 21, Scala 3 | ✅ Upgrade to 2.2.21 |
| **Framework** | Spring Boot 3.5.6 | ⚠️ 3.5.7 available | Quarkus, Micronaut, Ktor | ✅ Upgrade to 3.5.7 |
| **CQRS/ES** | Axon 4.12.1 | ⚠️ 5.x milestone | Custom, Lagom, EventStore | ✅ Keep 4.x; plan 5.x migration |
| **Event Store** | PostgreSQL 16.1+ | ⚠️ 16.6 / 17.1 available | EventStoreDB, NATS, Kafka | ✅ Upgrade to 16.6 |
| **Auth/IAM** | Keycloak 26.0 | ⚠️ 26.4.0 available | Ory Hydra, Spring Auth Server | ⚠️ Upgrade to 26.4; custom ppc64le builds |
| **Workflow** | Flowable 7.1 | ✅ Current | Camunda, Temporal | ✅ Approved |
| **Testing** | Kotest | ✅ Current | JUnit 5, Spek | ✅ Approved |
| **Mutation Testing** | Pitest | ⚠️ Kotlin plugin deprecated | Arcmutate (commercial) | ⚠️ Use Pitest; reduce target to 60-70% |
| **Containers** | Testcontainers | ✅ Current | Docker Compose, Arquillian | ✅ Approved |
| **Metrics** | Prometheus | ✅ Current | SigNoz, ELK | ✅ Approved |
| **Visualization** | Grafana | ✅ Current | Kibana, SigNoz | ✅ Approved; dashboards Post-MVP |
| **Tracing** | OpenTelemetry | ✅ Current | Jaeger, Zipkin | ✅ Approved |
| **Code Quality** | ktlint + Detekt | ✅ Current | SonarQube, Spotless | ✅ Approved |
| **Architecture** | Konsist | ✅ Current | ArchUnit, jMolecules | ✅ Approved |
| **Query Layer** | jOOQ | ✅ Current | Exposed, Ktorm, Spring Data JPA | ✅ Approved |
| **DB Migration** | Flyway | ✅ Current | Liquibase | ✅ Approved |
| **Frontend** | React-Admin | ✅ Current | Refine, CoreUI, Custom | ✅ Approved |
| **Build System** | Gradle | ✅ Current | Maven | ✅ Approved (Kotlin DSL) |
| **Error Handling** | Arrow (Either) | ✅ Current | Kotlin Result, Exceptions | ✅ Approved |
| **Architecture** | Hexagonal + DDD + CQRS | ✅ Current | Clean Architecture, Onion | ✅ Approved |
| **Modularity** | Spring Modulith 1.4.3 | ⚠️ 1.4.4 / 2.0 RC1 available | ArchUnit, JPMS | ✅ Upgrade to 1.4.4 |

### Legend:
- ✅ **Approved** - Current choice validated; no changes needed
- ⚠️ **Update Available** - Minor version update recommended
- 🚨 **Requires Action** - Critical issue identified; mitigation needed

---

## 13. Risk Assessment Summary

### Critical Risks (Require Immediate Attention)

| Risk | Impact | Probability | Mitigation | Timeline |
|------|--------|-------------|------------|----------|
| **Keycloak ppc64le images unavailable** | Critical | High | Build custom images; test thoroughly | Epic 3 (2 weeks) |
| **Axon 5.x migration required** | Medium | High | Plan post-MVP; use OpenRewrite | Q3 2026 (1-1.5 months) |
| **PostgreSQL scalability limits** | High | Medium | Proactive optimization; monitoring; NATS migration path | Ongoing; triggered by KPIs |
| **Mutation testing gaps (Kotlin)** | Medium | High | Reduce target to 60-70%; focus on integration tests | Immediate |
| **Active-Active vs Active-Passive inconsistency** | Medium | N/A | Clarify with product owner | Immediate |
| **ppc64le multi-arch testing** | Medium | Medium | Obtain IBM Power hardware; automate QEMU testing | Epic 1-2 |

### Medium Risks (Monitor and Manage)

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| **Kotlin learning curve** | High | High | Training, docs, Scaffolding CLI, pair programming |
| **CQRS/ES complexity** | High | High | Comprehensive training; 2-3 month realistic timeline |
| **Flowable BPMN learning curve** | Medium | Medium | BPMN training; "Dockets Pattern" templates |
| **Spring Boot overhead** | Low | Low | Acceptable for on-prem; optimize with Virtual Threads |
| **Detekt/ktlint strict enforcement** | Low | Medium | Baseline existing code; enforce for new code |

### Low Risks (Acceptable)

- Kotlin version updates (regular patch releases)
- Spring Boot version updates (backward compatible)
- PostgreSQL minor version upgrades
- Keycloak version upgrades
- Developer tooling (IDE plugins, etc.)

---

## 14. Recommendations

### Immediate Actions (Before Epic 1)

1. ✅ **Upgrade to latest patch versions:**
   - Kotlin 2.2.20 → 2.2.21
   - Spring Boot 3.5.6 → 3.5.7
   - PostgreSQL 16.1 → 16.6
   - Keycloak 26.0 → 26.4.0
   - Spring Modulith 1.4.3 → 1.4.4

2. 🚨 **Clarify Active-Active vs Active-Passive:**
   - Product Brief has conflicting statements
   - Recommend Active-Passive for v1.0 (single-server deployment)
   - Active-Active deferred to Phase 2 (multi-region cloud)

3. 🚨 **Plan ppc64le custom builds:**
   - Keycloak requires custom Docker images
   - Budget 1-2 weeks in Epic 3
   - Test on IBM Power hardware

4. ⚠️ **Reduce mutation testing target:**
   - 80% unrealistic with deprecated Kotlin plugin
   - Recommend 60-70% with Pitest
   - Supplement with 85%+ line coverage via integration tests

### Epic-Specific Recommendations

**Epic 1-2 (Foundation + Walking Skeleton):**
- ✅ Use Gradle Kotlin DSL with convention plugins
- ✅ Set up multi-arch Docker builds (amd64, arm64)
- ⚠️ Test ppc64le builds (QEMU emulation initially)

**Epic 3 (Authentication + Multi-Tenancy):**
- 🚨 **Build custom Keycloak ppc64le images** (1-2 weeks)
- ✅ Implement all 10 JWT validation layers
- ✅ Use single Keycloak realm with tenant claim

**Epic 4 (Multi-Tenancy):**
- ✅ PostgreSQL Row-Level Security
- ✅ ThreadLocal + Coroutine context propagation
- ✅ Comprehensive testing of async tenant context

**Epic 5 (Observability):**
- ✅ Prometheus + Micrometer (MVP)
- ✅ Structured JSON logging with trace_id
- ⚠️ Defer Grafana dashboards to Post-MVP

**Epic 6 (Flowable Integration):**
- ✅ Use Flowable 7.1 (not Camunda)
- ✅ Test Flowable on ppc64le
- ✅ Create "Dockets Pattern" BPMN templates

**Epic 7 (Scaffolding CLI):**
- ✅ Generate code passing ktlint/Detekt immediately
- ✅ React-Admin components for CRUD

**Epic 8 (Code Quality):**
- ✅ Detekt + embedded ktlint
- ✅ Konsist for Spring Modulith boundaries
- ⚠️ Pitest mutation testing (reduced target)

**Epic 9 (Reference Application):**
- ✅ Validate all technology choices
- ✅ Demonstrate multi-tenancy, CQRS, workflows
- ✅ Test on all architectures (amd64, arm64, ppc64le)

### Post-MVP Recommendations

**Phase 2 (ZEWSSP Migration + Beyond):**
1. **Axon 5.x Migration:**
   - Timeline: Q3-Q4 2026
   - Effort: 1-1.5 months
   - Use OpenRewrite recipes

2. **PostgreSQL Optimization:**
   - Monitor performance KPIs continuously
   - Trigger NATS migration if write latency p95 >200ms sustained

3. **Observability Enhancements:**
   - Grafana dashboards (golden signals, business metrics)
   - Loki for log aggregation
   - Jaeger/Tempo for trace visualization
   - Consider SigNoz (FOSS all-in-one)

4. **Advanced Testing:**
   - Evaluate Arcmutate (commercial) if budget approved
   - Property-based testing with Kotest
   - Chaos engineering (resilience testing)

5. **Multi-Arch Validation:**
   - Quarterly testing on real ppc64le hardware
   - Automated QEMU testing in CI/CD

6. **Frontend Enhancements:**
   - Advanced React-Admin features (complex dashboards, bulk ops)
   - Evaluate Refine if customization needs increase

### Long-Term Strategic Recommendations

**Cloud Migration Path (12-24 months):**
- Kubernetes deployment (Hexagonal architecture enables this)
- NATS JetStream for event streaming
- Multi-region active-active (if business requires)
- Managed services (RDS, Elastic Kubernetes Service)

**Technology Refresh Cycle:**
- **Kotlin:** Upgrade to 2.3.x when stable (Q2 2026)
- **Spring Boot:** Monitor 4.0 roadmap; upgrade post-ZEWSSP
- **PostgreSQL:** Upgrade to 17.x post-ZEWSSP (Q3 2026)
- **Axon:** Migrate to 5.x post-ZEWSSP (Q3-Q4 2026)

---

## Appendix A: Version Matrix (As of 2025-10-30)

| Technology | Product Brief | Latest Stable | Recommended |
|------------|---------------|---------------|-------------|
| Kotlin | 2.2.20 | 2.2.21 | 2.2.21 |
| Spring Boot | 3.5.6 | 3.5.7 | 3.5.7 |
| Axon Framework | 4.12.1 | 4.12.1 / 5.0.0-M2 | 4.12.1 (plan 5.x) |
| PostgreSQL | 16.1+ | 16.6 / 17.1 | 16.6 |
| Keycloak | 26.0 | 26.4.0 | 26.4.0 |
| Spring Modulith | 1.4.3 | 1.4.4 / 2.0 RC1 | 1.4.4 |
| Flowable | 7.1 | 7.x | 7.1 |
| ktlint | 1.4.0 | 1.4.x | Latest 1.4.x |
| Detekt | 1.23.7 | 1.23.x | Latest 1.23.x |

---

## Appendix B: Research Sources

### Technology Documentation
- Kotlin: https://kotlinlang.org/docs/releases.html
- Spring Boot: https://spring.io/projects/spring-boot
- Axon Framework: https://docs.axoniq.io/
- PostgreSQL: https://www.postgresql.org/docs/
- Keycloak: https://www.keycloak.org/documentation
- Flowable: https://www.flowable.com/open-source

### Comparative Analysis
- Gradle vs Maven: https://gradlehero.com/maven-vs-gradle-comparison/
- Kotest vs JUnit: https://www.baeldung.com/kotlin/kotest-vs-junit-5
- Flyway vs Liquibase: https://www.bytebase.com/blog/flyway-vs-liquibase/
- Axon 4 vs 5: https://www.axoniq.io/blog/announcing-axon-framework-5

### Industry Case Studies
- CQRS/ES in Production: https://www.javacodegeeks.com/2025/10/cqrs-and-event-sourcing-in-practice-building-scalable-systems.html
- PostgreSQL Event Store: https://github.com/eugene-khyst/postgresql-event-sourcing
- Event Sourcing Benchmarks: https://softwaremill.com/reactive-event-sourcing-benchmarks-part-1-postgresql/

### Multi-Architecture Support
- ppc64le Docker Images: https://hub.docker.com/u/ppc64le
- Multi-Arch Builds: https://devopscube.com/build-multi-arch-docker-image/

### High Availability
- Active-Active vs Active-Passive: https://www.pgedge.com/blog/understanding-the-differences-and-advantages-of-active-active-vs-active-passive-replication
- PostgreSQL HA: https://medium.com/@vishalpriyadarshi/postgresql-ha-in-the-cloud-active-active-or-active-passive-692724024d3e

---

## Appendix C: Glossary

**CQRS:** Command Query Responsibility Segregation
**ES:** Event Sourcing
**DDD:** Domain-Driven Design
**BDD:** Behavior-Driven Development
**TDD:** Test-Driven Development
**HA:** High Availability
**DR:** Disaster Recovery
**RTO:** Recovery Time Objective
**RPO:** Recovery Point Objective
**FOSS:** Free and Open Source Software
**BPMN:** Business Process Model and Notation
**OIDC:** OpenID Connect
**JWT:** JSON Web Token
**PITR:** Point-in-Time Recovery
**WAL:** Write-Ahead Logging (PostgreSQL)
**BRIN:** Block Range INdex (PostgreSQL)

---

**END OF COMPREHENSIVE TECHNICAL ANALYSIS**

---

**Next Steps:**
1. Review recommendations with product owner
2. Clarify Active-Active vs Active-Passive inconsistency
3. Plan ppc64le custom build effort (Keycloak)
4. Update Product Brief with version adjustments
5. Proceed with Epic 1 implementation
