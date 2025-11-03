// products/widget-demo placeholder module
// Implementation planned for Epic 2: Walking Skeleton - CQRS/Event Sourcing Core

plugins {
    id("eaf.kotlin-common")
    // TODO(Epic 2): Add Spring Boot plugin when implementing Widget aggregate
    // id("eaf.spring-boot")
}

group = "com.axians.eaf.products"
description = "Widget Demo - Reference implementation validating EAF framework capabilities"

// Epic 2 will implement:
// - Complete CQRS/ES vertical slice with Axon Framework
// - PostgreSQL event store with partitioning and BRIN indexes
// - jOOQ projections for read models
// - REST API with OpenAPI documentation
// - End-to-end integration tests

// TODO(Epic 2 Story 2.5): Implement Widget aggregate with CQRS pattern
